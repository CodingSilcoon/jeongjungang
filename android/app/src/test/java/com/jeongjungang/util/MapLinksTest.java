package com.jeongjungang.util;

import static org.junit.Assert.assertEquals;

import com.jeongjungang.domain.model.LatLng;
import java.util.Locale;
import org.junit.After;
import org.junit.Test;

public class MapLinksTest {

    private static final LatLng HONGDAE = new LatLng(37.557192, 126.925381);
    private static final LatLng DDP = new LatLng(37.565138, 127.007896);

    private final Locale originalLocale = Locale.getDefault();

    @After
    public void restoreLocale() {
        Locale.setDefault(originalLocale);
    }

    @Test
    public void kakaoAppRouteUsesPublicTransit() {
        assertEquals("kakaomap://route?sp=37.557192,126.925381&ep=37.565138,127.007896&by=PUBLICTRANSIT",
                MapLinks.kakaoAppRoute(HONGDAE, DDP));
    }

    @Test
    public void kakaoWebRouteEncodesNamesAndStripsSeparators() {
        assertEquals("https://map.kakao.com/link/by/traffic/"
                        + "%EB%AF%BC%EC%88%98%20%EC%B6%9C%EB%B0%9C,37.557192,126.925381/"
                        + "a%20b%20c,37.565138,127.007896",
                MapLinks.kakaoWebRoute("민수 출발", HONGDAE, "a,b/c", DDP));
    }

    @Test
    public void naverRouteHasAllRequiredParamsIncludingAppName() {
        assertEquals("nmap://route/public?slat=37.557192&slng=126.925381&sname=%EB%AF%BC%EC%88%98"
                        + "&dlat=37.565138&dlng=127.007896&dname=DDP&appname=com.jeongjungang",
                MapLinks.naverAppRoute("민수", HONGDAE, "DDP", DDP));
    }

    @Test
    public void coordinatesUseDotEvenWhenLocaleUsesComma() {
        Locale.setDefault(Locale.GERMANY);
        assertEquals("kakaomap://route?sp=37.557192,126.925381&ep=37.565138,127.007896&by=PUBLICTRANSIT",
                MapLinks.kakaoAppRoute(HONGDAE, DDP));
    }
}
