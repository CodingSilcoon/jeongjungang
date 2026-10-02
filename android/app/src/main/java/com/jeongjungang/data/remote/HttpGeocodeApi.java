package com.jeongjungang.data.remote;

import com.jeongjungang.domain.model.LatLng;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** 정중앙 서버의 `/geocode`, `/geocode/reverse` 호출 (docs/API.md 5절). */
public final class HttpGeocodeApi implements GeocodeApi {

    private final ApiHttp http;

    /** @param baseUrl 예: "https://jeongjungang.duckdns.org/api/v1" (끝의 / 는 있어도 된다) */
    public HttpGeocodeApi(String baseUrl) {
        this.http = new ApiHttp(baseUrl);
    }

    @Override
    public List<GeoPlace> search(String query, int size) throws ApiException {
        String q = query == null ? "" : query.trim();
        if (q.length() < MIN_QUERY_LENGTH || q.length() > MAX_QUERY_LENGTH) {
            throw new ApiException("VALIDATION_FAILED", 0,
                    "검색어는 " + MIN_QUERY_LENGTH + "~" + MAX_QUERY_LENGTH + "자로 입력해 주세요.", 0, null);
        }
        int clamped = Math.max(1, Math.min(MAX_SIZE, size));
        JSONObject data = http.get("/geocode?query=" + encode(q) + "&size=" + clamped, null, null).data;
        try {
            JSONArray items = data.getJSONArray("items");
            List<GeoPlace> places = new ArrayList<GeoPlace>(items.length());
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.getJSONObject(i);
                places.add(new GeoPlace(
                        "ADDRESS".equals(item.optString("type")) ? GeoPlace.Type.ADDRESS : GeoPlace.Type.PLACE,
                        item.getString("name"),
                        ApiHttp.optString(item, "address"),
                        new LatLng(item.getDouble("lat"), item.getDouble("lng"))));
            }
            return Collections.unmodifiableList(places);
        } catch (JSONException | IllegalArgumentException e) {
            throw ApiHttp.badResponse(e);
        }
    }

    @Override
    public ReverseAddress reverse(LatLng location) throws ApiException {
        JSONObject data = http.get(String.format(Locale.US, "/geocode/reverse?lat=%.6f&lng=%.6f",
                location.lat, location.lng), null, null).data;
        return new ReverseAddress(ApiHttp.optString(data, "address"), ApiHttp.optString(data, "roadAddress"));
    }

    static String encode(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8").replace("+", "%20");
        } catch (UnsupportedEncodingException e) {
            throw new AssertionError(e);
        }
    }
}
