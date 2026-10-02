package com.jeongjungang.domain.transit;

import java.util.HashMap;
import java.util.Map;

/** CSV 파일마다 다른 역명 표기를 하나로 맞춘다 (docs/DATA.md 참고). */
public final class StationNames {

    private static final String STATION_SUFFIX = "역";
    private static final Map<String, String> ALIASES = new HashMap<String, String>();

    static {
        ALIASES.put("총신대입구", "이수");
        ALIASES.put("뚝섬유원지", "자양");
    }

    private StationNames() {}

    /** 앞뒤 공백과 끝의 '역'을 제거하고 별칭을 대표 이름으로 바꾼다. */
    public static String normalize(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("역명이 null입니다.");
        }
        String name = raw.trim();
        if (name.length() > STATION_SUFFIX.length() && name.endsWith(STATION_SUFFIX)) {
            name = name.substring(0, name.length() - STATION_SUFFIX.length());
        }
        String alias = ALIASES.get(name);
        return alias != null ? alias : name;
    }
}
