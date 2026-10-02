package com.jeongjungang.data.remote;

import com.jeongjungang.domain.model.LatLng;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * 정중앙 서버의 `/geocode`, `/geocode/reverse` 호출 (docs/API.md 5절).
 * 응답 봉투 {success, data, error}와 오류 코드를 해석해 {@link ApiException}으로 바꾼다.
 */
public final class HttpGeocodeApi implements GeocodeApi {

    static final int CONNECT_TIMEOUT_MS = 5000;
    static final int READ_TIMEOUT_MS = 8000;

    static final String MSG_NETWORK = "인터넷 연결을 확인해 주세요.";
    static final String MSG_BAD_RESPONSE = "검색 결과를 읽지 못했어요. 잠시 후 다시 시도해 주세요.";
    static final String MSG_SERVER = "주소 검색이 잠시 안 돼요. 잠시 후 다시 시도해 주세요.";

    private final String baseUrl;

    /** @param baseUrl 예: "https://jeongjungang.duckdns.org/api/v1" (끝의 / 는 있어도 된다) */
    public HttpGeocodeApi(String baseUrl) {
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("서버 주소가 필요합니다.");
        }
        String trimmed = baseUrl.trim();
        this.baseUrl = trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

    @Override
    public List<GeoPlace> search(String query, int size) throws ApiException {
        String q = query == null ? "" : query.trim();
        if (q.length() < MIN_QUERY_LENGTH || q.length() > MAX_QUERY_LENGTH) {
            throw new ApiException("VALIDATION_FAILED", 0,
                    "검색어는 " + MIN_QUERY_LENGTH + "~" + MAX_QUERY_LENGTH + "자로 입력해 주세요.", 0, null);
        }
        int clamped = Math.max(1, Math.min(MAX_SIZE, size));
        JSONObject data = get("/geocode?query=" + encode(q) + "&size=" + clamped);
        try {
            JSONArray items = data.getJSONArray("items");
            List<GeoPlace> places = new ArrayList<GeoPlace>(items.length());
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.getJSONObject(i);
                places.add(new GeoPlace(
                        "ADDRESS".equals(item.optString("type")) ? GeoPlace.Type.ADDRESS : GeoPlace.Type.PLACE,
                        item.getString("name"),
                        optString(item, "address"),
                        new LatLng(item.getDouble("lat"), item.getDouble("lng"))));
            }
            return Collections.unmodifiableList(places);
        } catch (JSONException | IllegalArgumentException e) {
            throw badResponse(e);
        }
    }

    @Override
    public ReverseAddress reverse(LatLng location) throws ApiException {
        JSONObject data = get(String.format(Locale.US, "/geocode/reverse?lat=%.6f&lng=%.6f",
                location.lat, location.lng));
        return new ReverseAddress(optString(data, "address"), optString(data, "roadAddress"));
    }

    /** 성공이면 data 객체, 아니면 ApiException. */
    private JSONObject get(String pathAndQuery) throws ApiException {
        HttpURLConnection conn = null;
        int status;
        String body;
        String retryAfterHeader;
        try {
            conn = (HttpURLConnection) new URL(baseUrl + pathAndQuery).openConnection();
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setRequestProperty("Accept", "application/json");
            status = conn.getResponseCode();
            retryAfterHeader = conn.getHeaderField("Retry-After");
            InputStream in = status >= 400 ? conn.getErrorStream() : conn.getInputStream();
            body = in == null ? "" : readAll(in);
        } catch (IOException e) {
            throw new ApiException(ApiException.NETWORK, 0, MSG_NETWORK, 0, e);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
        int retryAfter = status == 429 ? parseRetryAfter(retryAfterHeader) : 0;
        JSONObject envelope;
        try {
            envelope = new JSONObject(body);
        } catch (JSONException e) {
            if (status >= 200 && status < 300) {
                throw badResponse(e);
            }
            throw fromStatus(status, null, null, retryAfter);
        }
        if (status >= 200 && status < 300 && envelope.optBoolean("success", false)) {
            JSONObject data = envelope.optJSONObject("data");
            if (data == null) {
                throw badResponse(null);
            }
            return data;
        }
        JSONObject error = envelope.optJSONObject("error");
        throw fromStatus(status,
                error == null ? null : optString(error, "code"),
                error == null ? null : optString(error, "message"),
                retryAfter);
    }

    private static ApiException fromStatus(int status, String code, String serverMessage, int retryAfter) {
        if (status == 429 || ApiException.RATE_LIMITED.equals(code)) {
            String message = serverMessage != null ? serverMessage
                    : retryAfter > 0 ? "검색을 너무 자주 했어요. " + retryAfter + "초 뒤에 다시 시도해 주세요."
                    : "검색을 너무 자주 했어요. 잠시 후 다시 시도해 주세요.";
            return new ApiException(ApiException.RATE_LIMITED, status, message, retryAfter, null);
        }
        return new ApiException(code != null ? code : "HTTP_" + status, status,
                serverMessage != null ? serverMessage : MSG_SERVER, retryAfter, null);
    }

    private static ApiException badResponse(Exception cause) {
        return new ApiException(ApiException.BAD_RESPONSE, 200, MSG_BAD_RESPONSE, 0, cause);
    }

    static int parseRetryAfter(String header) {
        if (header == null) {
            return 0;
        }
        try {
            return Math.max(0, Integer.parseInt(header.trim()));
        } catch (NumberFormatException e) {
            return 0; // HTTP 날짜 형식은 쓰지 않기로 했다(docs/API.md: 초 단위)
        }
    }

    /** JSON null과 빈 값을 Java null로. org.json의 optString은 null을 "null"로 돌려줘서 따로 처리한다. */
    private static String optString(JSONObject o, String key) {
        if (!o.has(key) || o.isNull(key)) {
            return null;
        }
        String s = o.optString(key, null);
        return s == null || s.isEmpty() ? null : s;
    }

    private static String readAll(InputStream in) throws IOException {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            in.close();
        }
    }

    private static String encode(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8").replace("+", "%20");
        } catch (UnsupportedEncodingException e) {
            throw new AssertionError(e);
        }
    }
}
