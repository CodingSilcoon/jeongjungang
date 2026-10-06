package com.jeongjungang.geocode;

import com.jeongjungang.geocode.GeoResponses.GeocodeItem;
import com.jeongjungang.geocode.GeoResponses.GeocodeResult;
import com.jeongjungang.geocode.GeoResponses.ItemType;
import com.jeongjungang.geocode.GeoResponses.ReverseResult;
import com.jeongjungang.geocode.kakao.KakaoLocalClient;
import com.jeongjungang.geocode.kakao.KakaoResponses.Address;
import com.jeongjungang.geocode.kakao.KakaoResponses.AddressName;
import com.jeongjungang.geocode.kakao.KakaoResponses.CoordAddress;
import com.jeongjungang.geocode.kakao.KakaoResponses.Place;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * 주소·장소 이름 → 좌표, 좌표 → 주소 (docs/API.md 5절). 같은 요청은 24시간 캐시한다.
 * 검색은 주소 검색 결과(ADDRESS)를 앞에, 장소 이름 검색 결과(PLACE)를 뒤에 둔다.
 */
@Service
public class GeocodeService {

    static final Duration CACHE_TTL = Duration.ofHours(24);
    private static final Pattern SPACES = Pattern.compile("\s+");

    private final KakaoLocalClient kakao;
    private final GeoCache cache;

    GeocodeService(KakaoLocalClient kakao, GeoCache cache) {
        this.kakao = kakao;
        this.cache = cache;
    }

    public GeocodeResult search(String query, int size) {
        String normalized = SPACES.matcher(query.strip()).replaceAll(" ");
        return cache.getOrLoad("q:" + size + ":" + normalized, GeocodeResult.class, CACHE_TTL,
                () -> searchKakao(normalized, size));
    }

    public ReverseResult reverse(double lat, double lng) {
        String key = String.format(Locale.US, "r:%.5f,%.5f", lat, lng);
        return cache.getOrLoad(key, ReverseResult.class, CACHE_TTL, () -> reverseKakao(lat, lng));
    }

    private GeocodeResult searchKakao(String query, int size) {
        List<GeocodeItem> items = new ArrayList<>();
        for (Address a : kakao.address(query, size).documents()) {
            // 이름은 도로명 주소, 보조 주소는 지번 주소를 우선한다 (docs/API.md 예시)
            String name = name(a.roadAddress()).orElse(a.addressName());
            String jibun = name(a.address()).orElse(a.addressName());
            Coordinates.parse(a.y(), a.x()).ifPresent(c ->
                    items.add(new GeocodeItem(ItemType.ADDRESS, name, jibun, c.lat(), c.lng())));
        }
        for (Place p : kakao.keyword(query, size).documents()) {
            Coordinates.parse(p.y(), p.x()).ifPresent(c -> items.add(new GeocodeItem(ItemType.PLACE,
                    p.placeName(), Coordinates.firstNonBlank(p.roadAddressName(), p.addressName()), c.lat(), c.lng())));
        }
        return new GeocodeResult(items.size() > size ? items.subList(0, size) : items);
    }

    private ReverseResult reverseKakao(double lat, double lng) {
        List<CoordAddress> docs = kakao.coordToAddress(lat, lng).documents();
        if (docs == null || docs.isEmpty()) {
            return new ReverseResult(null, null);
        }
        CoordAddress first = docs.get(0);
        return new ReverseResult(name(first.address()).orElse(null), name(first.roadAddress()).orElse(null));
    }

    private static Optional<String> name(AddressName address) {
        return Optional.ofNullable(address).map(AddressName::addressName).filter(s -> !s.isBlank());
    }
}
