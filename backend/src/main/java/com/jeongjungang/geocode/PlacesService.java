package com.jeongjungang.geocode;

import com.jeongjungang.geocode.GeoResponses.PlaceItem;
import com.jeongjungang.geocode.GeoResponses.PlacePage;
import com.jeongjungang.geocode.kakao.KakaoLocalClient;
import com.jeongjungang.geocode.kakao.KakaoResponses.Place;
import com.jeongjungang.geocode.kakao.KakaoResponses.PlaceResult;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

/** 확정한 역 주변 식당·카페·술집 (docs/API.md 6절 GET /places). 가까운 순, 한 쪽에 15곳. */
@Service
public class PlacesService {

    public static final int PAGE_SIZE = 15;
    /** 카카오 검색은 45쪽까지만 준다. */
    public static final int MAX_PAGE = 45;
    static final Duration CACHE_TTL = Duration.ofHours(1);
    private static final String CATEGORY_SEPARATOR = " > ";

    private final KakaoLocalClient kakao;
    private final GeoCache cache;

    PlacesService(KakaoLocalClient kakao, GeoCache cache) {
        this.kakao = kakao;
        this.cache = cache;
    }

    public PlacePage find(double lat, double lng, PlaceCategory category, int radius, int page) {
        String key = String.format(Locale.US, "p:%s:%.5f,%.5f:%d:%d", category, lat, lng, radius, page);
        return cache.getOrLoad(key, PlacePage.class, CACHE_TTL, () -> findKakao(lat, lng, category, radius, page));
    }

    private PlacePage findKakao(double lat, double lng, PlaceCategory category, int radius, int page) {
        PlaceResult result = category.kakaoGroupCode() != null
                ? kakao.category(category.kakaoGroupCode(), lat, lng, radius, page, PAGE_SIZE)
                : kakao.keywordNear(category.keyword(), lat, lng, radius, page, PAGE_SIZE);
        List<PlaceItem> items = new ArrayList<>();
        for (Place p : result.documents()) {
            Coordinates.parse(p.y(), p.x()).ifPresent(c -> items.add(new PlaceItem(p.placeName(),
                    lastCategory(p.categoryName()), Coordinates.firstNonBlank(p.roadAddressName(), p.addressName()),
                    c.lat(), c.lng(), distance(p.distance()), p.placeUrl())));
        }
        boolean hasNext = result.meta() != null && !result.meta().isEnd() && page < MAX_PAGE;
        return new PlacePage(items, page, hasNext);
    }

    /** "음식점 > 한식 > 국밥"에서 가장 구체적인 "국밥". */
    private static String lastCategory(String categoryName) {
        if (categoryName == null || categoryName.isBlank()) {
            return null;
        }
        int at = categoryName.lastIndexOf(CATEGORY_SEPARATOR);
        return at < 0 ? categoryName.strip() : categoryName.substring(at + CATEGORY_SEPARATOR.length()).strip();
    }

    private static int distance(String meters) {
        try {
            return meters == null || meters.isBlank() ? 0 : Integer.parseInt(meters.strip());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
