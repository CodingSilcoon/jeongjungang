package com.jeongjungang.geocode;

import java.util.List;

/** 주소·장소 응답 (docs/API.md 5절, 6절 GET /places). */
public final class GeoResponses {

    private GeoResponses() {
    }

    public enum ItemType { PLACE, ADDRESS }

    public record GeocodeItem(ItemType type, String name, String address, double lat, double lng) {
    }

    public record GeocodeResult(List<GeocodeItem> items) {
        public GeocodeResult {
            items = List.copyOf(items);
        }
    }

    /** 주소를 못 찾으면 둘 다 null이다. */
    public record ReverseResult(String address, String roadAddress) {
    }

    public record PlaceItem(String name, String category, String address, double lat, double lng,
                            int distanceMeters, String placeUrl) {
    }

    public record PlacePage(List<PlaceItem> items, int page, boolean hasNext) {
        public PlacePage {
            items = List.copyOf(items);
        }
    }
}
