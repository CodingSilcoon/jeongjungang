package com.jeongjungang.domain.transit;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

/** 번들 CSV(UTF-8) 파서. 입력은 Reader라서 Android assets/InputStream, 테스트 문자열 모두 쓸 수 있다. */
public final class TransitCsv {

    private static final int NOT_SEOUL_METRO_LINE = -1;
    private static final int SECONDS_PER_MINUTE = 60;

    private TransitCsv() {}

    /** 역간 소요시간 한 행. 한 노선 안에서 파일 순서를 유지한다. */
    public static final class IntervalRow {
        public final int line;
        public final String station;
        public final double minutes;
        public final double km;

        public IntervalRow(int line, String station, double minutes, double km) {
            this.line = line;
            this.station = station;
            this.minutes = minutes;
            this.km = km;
        }
    }

    public static final class CoordRow {
        public final int line;
        public final String station;
        public final double lat;
        public final double lng;

        public CoordRow(int line, String station, double lat, double lng) {
            this.line = line;
            this.station = station;
            this.lat = lat;
            this.lng = lng;
        }
    }

    /** toLine이 -1이면 1~8호선이 아닌 노선(경의중앙선, 공항철도 등). */
    public static final class TransferRow {
        public final int line;
        public final String station;
        public final int toLine;
        public final double meters;
        public final double minutes;

        public TransferRow(int line, String station, int toLine, double meters, double minutes) {
            this.line = line;
            this.station = station;
            this.toLine = toLine;
            this.meters = meters;
            this.minutes = minutes;
        }
    }

    /** "mm:ss"를 분(소수)으로 바꾼다. */
    public static double parseMinutes(String mmss) {
        String[] parts = mmss.trim().split(":");
        if (parts.length != 2) {
            throw new IllegalArgumentException("mm:ss 형식이 아닙니다: " + mmss);
        }
        try {
            int minutes = Integer.parseInt(parts[0]);
            int seconds = Integer.parseInt(parts[1]);
            return minutes + seconds / (double) SECONDS_PER_MINUTE;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("mm:ss 형식이 아닙니다: " + mmss, e);
        }
    }

    /** 연번,호선,역명,소요시간,역간거리(km),호선별누계(km) */
    public static List<IntervalRow> parseIntervals(Reader reader) throws IOException {
        List<IntervalRow> rows = new ArrayList<IntervalRow>();
        for (String[] f : readRows(reader, 6)) {
            rows.add(new IntervalRow(
                    Integer.parseInt(f[1].trim()),
                    StationNames.normalize(f[2]),
                    parseMinutes(f[3]),
                    Double.parseDouble(f[4].trim())));
        }
        return rows;
    }

    /** 연번,호선,고유역번호,역명,위도,경도,작성일자 */
    public static List<CoordRow> parseCoords(Reader reader) throws IOException {
        List<CoordRow> rows = new ArrayList<CoordRow>();
        for (String[] f : readRows(reader, 7)) {
            rows.add(new CoordRow(
                    Integer.parseInt(f[1].trim()),
                    StationNames.normalize(f[3]),
                    Double.parseDouble(f[4].trim()),
                    Double.parseDouble(f[5].trim())));
        }
        return rows;
    }

    /** 연번,호선,환승역명,환승노선,환승거리(m),환승소요시간 */
    public static List<TransferRow> parseTransfers(Reader reader) throws IOException {
        List<TransferRow> rows = new ArrayList<TransferRow>();
        for (String[] f : readRows(reader, 6)) {
            rows.add(new TransferRow(
                    Integer.parseInt(f[1].trim()),
                    StationNames.normalize(f[2]),
                    parseSeoulMetroLine(f[3]),
                    Double.parseDouble(f[4].trim()),
                    parseMinutes(f[5])));
        }
        return rows;
    }

    /** "4호선" -> 4. 그 외 노선명은 -1. */
    private static int parseSeoulMetroLine(String label) {
        String s = label.trim();
        if (s.endsWith("호선")) {
            try {
                int line = Integer.parseInt(s.substring(0, s.length() - 2));
                if (line >= 1 && line <= 8) {
                    return line;
                }
            } catch (NumberFormatException ignored) {
                // 숫자가 아니면 서울교통공사 호선이 아닌 것으로 본다.
            }
        }
        return NOT_SEOUL_METRO_LINE;
    }

    /** 헤더(첫 줄)와 빈 줄을 건너뛰고, 열 수가 맞는 행만 돌려준다. BOM은 제거한다. */
    private static List<String[]> readRows(Reader reader, int expectedColumns) throws IOException {
        BufferedReader in = reader instanceof BufferedReader ? (BufferedReader) reader : new BufferedReader(reader);
        List<String[]> rows = new ArrayList<String[]>();
        String line;
        boolean isHeader = true;
        while ((line = in.readLine()) != null) {
            if (isHeader) {
                isHeader = false;
                continue;
            }
            if (line.trim().isEmpty()) {
                continue;
            }
            String[] fields = line.split(",", -1);
            if (fields.length != expectedColumns) {
                throw new IllegalArgumentException("열 수가 " + expectedColumns + "개가 아닙니다: " + line);
            }
            rows.add(fields);
        }
        return rows;
    }
}
