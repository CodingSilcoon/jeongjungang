package com.jeongjungang.data.remote;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * 정중앙 서버 공통 HTTP 호출 (docs/API.md 3절).
 * 응답 봉투 {success, data, error}를 풀고, 실패는 사용자에게 보여도 되는 문장의 {@link ApiException}으로 바꾼다.
 * HttpURLConnection은 PATCH를 못 보내서 OkHttp를 쓴다.
 */
public final class ApiHttp {

    static final int CONNECT_TIMEOUT_MS = 5000;
    static final int READ_TIMEOUT_MS = 8000;

    static final String MSG_NETWORK = "인터넷 연결을 확인해 주세요.";
    static final String MSG_BAD_RESPONSE = "서버 응답을 읽지 못했어요. 잠시 후 다시 시도해 주세요.";
    static final String MSG_SERVER = "서버가 잠시 응답하지 않아요. 잠시 후 다시 시도해 주세요.";

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static volatile OkHttpClient sharedClient;

    /** 성공 응답. 304면 {@link #notModified}가 true이고 data는 null. 204면 data가 null. */
    public static final class Result {
        public final int status;
        public final JSONObject data;
        /** 응답의 ETag 헤더(따옴표 포함 그대로). 없으면 null. */
        public final String etag;
        public final boolean notModified;

        Result(int status, JSONObject data, String etag) {
            this.status = status;
            this.data = data;
            this.etag = etag;
            this.notModified = status == 304;
        }
    }

    private final String baseUrl;
    private final OkHttpClient client;

    /** @param baseUrl 예: "https://jeongjungang.duckdns.org/api/v1" (끝의 / 는 있어도 된다) */
    public ApiHttp(String baseUrl) {
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("서버 주소가 필요합니다.");
        }
        String trimmed = baseUrl.trim();
        this.baseUrl = trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
        this.client = client();
    }

    /** 연결 풀을 앱 전체에서 나눠 쓴다. */
    private static OkHttpClient client() {
        if (sharedClient == null) {
            synchronized (ApiHttp.class) {
                if (sharedClient == null) {
                    sharedClient = new OkHttpClient.Builder()
                            .connectTimeout(CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                            .readTimeout(READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                            .build();
                }
            }
        }
        return sharedClient;
    }

    public Result get(String pathAndQuery, String token, String ifNoneMatch) throws ApiException {
        return call("GET", pathAndQuery, null, token, ifNoneMatch);
    }

    public Result post(String path, JSONObject body, String token) throws ApiException {
        return call("POST", path, body == null ? new JSONObject() : body, token, null);
    }

    public Result patch(String path, JSONObject body, String token) throws ApiException {
        return call("PATCH", path, body == null ? new JSONObject() : body, token, null);
    }

    public Result delete(String path, String token) throws ApiException {
        return call("DELETE", path, null, token, null);
    }

    private Result call(String method, String path, JSONObject body, String token, String ifNoneMatch)
            throws ApiException {
        Request.Builder rb = new Request.Builder()
                .url(baseUrl + path)
                .header("Accept", "application/json")
                .method(method, body == null ? null : RequestBody.create(body.toString(), JSON));
        if (token != null) {
            rb.header("Authorization", "Bearer " + token);
        }
        if (ifNoneMatch != null) {
            rb.header("If-None-Match", ifNoneMatch);
        }
        int status;
        String text;
        String etag;
        String retryAfterHeader;
        try (Response response = client.newCall(rb.build()).execute()) {
            status = response.code();
            etag = response.header("ETag");
            retryAfterHeader = response.header("Retry-After");
            ResponseBody rbody = response.body();
            text = rbody == null ? "" : rbody.string();
        } catch (IOException e) {
            throw new ApiException(ApiException.NETWORK, 0, MSG_NETWORK, 0, e);
        }
        if (status == 304 || status == 204) {
            return new Result(status, null, etag);
        }
        int retryAfter = status == 429 ? parseRetryAfter(retryAfterHeader) : 0;
        JSONObject envelope;
        try {
            envelope = new JSONObject(text);
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
            return new Result(status, data, etag);
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
                    : retryAfter > 0 ? "요청이 너무 많아요. " + retryAfter + "초 뒤에 다시 시도해 주세요."
                    : "요청이 너무 많아요. 잠시 후 다시 시도해 주세요.";
            return new ApiException(ApiException.RATE_LIMITED, status, message, retryAfter, null);
        }
        return new ApiException(code != null ? code : "HTTP_" + status, status,
                serverMessage != null ? serverMessage : MSG_SERVER, retryAfter, null);
    }

    /** 응답 형식이 명세와 다를 때. 파싱하는 쪽에서도 쓴다. */
    public static ApiException badResponse(Exception cause) {
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
    public static String optString(JSONObject o, String key) {
        if (o == null || !o.has(key) || o.isNull(key)) {
            return null;
        }
        String s = o.optString(key, null);
        return s == null || s.isEmpty() ? null : s;
    }
}
