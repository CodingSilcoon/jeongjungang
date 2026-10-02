package com.jeongjungang.util;

import com.jeongjungang.domain.model.LatLng;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Locale;

/**
 * 출발지 → 후보 역 대중교통 길찾기 링크. 실제 경로(버스 포함)는 지도 앱에 맡긴다.
 *
 * <ul>
 *   <li>카카오맵 앱: {@code kakaomap://route?sp=위도,경도&ep=위도,경도&by=PUBLICTRANSIT}
 *       (https://apis.map.kakao.com/android_v2/docs/api-guide/urlscheme/)</li>
 *   <li>카카오맵 웹: {@code https://map.kakao.com/link/by/traffic/이름,위도,경도/이름,위도,경도}
 *       (https://apis.map.kakao.com/web/guide/ "URL로 바로가기")</li>
 *   <li>네이버지도 앱: {@code nmap://route/public?slat&slng&sname&dlat&dlng&dname&appname}, appname 필수
 *       (https://guide.ncloud-docs.com/docs/maps-url-scheme)</li>
 * </ul>
 */
public final class MapLinks {

    public static final String KAKAO_MAP_PACKAGE = "net.daum.android.map";
    public static final String NAVER_MAP_PACKAGE = "com.nhn.android.nmap";
    /** 네이버 URL Scheme의 appname. 앱 패키지 이름을 쓴다. */
    static final String APP_NAME = "com.jeongjungang";

    private MapLinks() {}

    public static String kakaoAppRoute(LatLng from, LatLng to) {
        return "kakaomap://route?sp=" + coord(from) + "&ep=" + coord(to) + "&by=PUBLICTRANSIT";
    }

    /** 카카오맵 앱이 없을 때 브라우저로 여는 링크. 이름의 쉼표·슬래시는 경로 구분자와 겹쳐서 뺀다. */
    public static String kakaoWebRoute(String fromName, LatLng from, String toName, LatLng to) {
        return "https://map.kakao.com/link/by/traffic/"
                + webPoint(fromName, from) + "/" + webPoint(toName, to);
    }

    public static String naverAppRoute(String fromName, LatLng from, String toName, LatLng to) {
        return "nmap://route/public"
                + "?slat=" + num(from.lat) + "&slng=" + num(from.lng) + "&sname=" + encode(fromName)
                + "&dlat=" + num(to.lat) + "&dlng=" + num(to.lng) + "&dname=" + encode(toName)
                + "&appname=" + APP_NAME;
    }

    public static String playStore(String packageName) {
        return "market://details?id=" + packageName;
    }

    private static String webPoint(String name, LatLng p) {
        return encode(name.replace(",", " ").replace("/", " ")) + "," + coord(p);
    }

    private static String coord(LatLng p) {
        return num(p.lat) + "," + num(p.lng);
    }

    /** 기기 로캘이 소수점에 쉼표를 써도 점으로 나오게 한다. */
    private static String num(double v) {
        return String.format(Locale.US, "%.6f", v);
    }

    static String encode(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8").replace("+", "%20");
        } catch (UnsupportedEncodingException e) {
            throw new AssertionError(e);
        }
    }
}
