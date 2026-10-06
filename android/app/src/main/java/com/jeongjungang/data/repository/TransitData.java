package com.jeongjungang.data.repository;

import com.jeongjungang.domain.recommend.Recommender;
import com.jeongjungang.domain.transit.StationIndex;
import com.jeongjungang.domain.transit.TransitCsv;
import com.jeongjungang.domain.transit.TransitCsv.CoordRow;
import com.jeongjungang.domain.transit.TransitCsv.IntervalRow;
import com.jeongjungang.domain.transit.TransitCsv.TransferRow;
import com.jeongjungang.domain.transit.TransitGraph;
import java.io.IOException;
import java.io.Reader;
import java.util.List;

/**
 * 번들 CSV 3종으로 만든 지하철 그래프, 역 좌표 색인, 추천기 묶음.
 * Android 의존성이 없어서 파일에서 읽는 단위 테스트로도 검증할 수 있다.
 */
public final class TransitData {

    public static final String INTERVALS_FILE = "station_intervals.csv";
    public static final String COORDS_FILE = "station_coords.csv";
    public static final String TRANSFERS_FILE = "transfers.csv";

    /** 파일 이름을 받아 UTF-8 Reader를 연다. 앱은 assets, 테스트는 파일 시스템에서 연다. */
    public interface Source {
        Reader open(String fileName) throws IOException;
    }

    public final TransitGraph graph;
    public final StationIndex index;
    public final Recommender recommender;

    private TransitData(TransitGraph graph, StationIndex index) {
        this.graph = graph;
        this.index = index;
        this.recommender = new Recommender(graph, index);
    }

    /** 역 수백 개 규모라 수 ms~1초 걸린다. 메인 스레드에서 부르지 않는다. */
    public static TransitData load(Source source) throws IOException {
        List<IntervalRow> intervals;
        List<CoordRow> coords;
        List<TransferRow> transfers;
        Reader reader = source.open(INTERVALS_FILE);
        try {
            intervals = TransitCsv.parseIntervals(reader);
        } finally {
            reader.close();
        }
        reader = source.open(COORDS_FILE);
        try {
            coords = TransitCsv.parseCoords(reader);
        } finally {
            reader.close();
        }
        reader = source.open(TRANSFERS_FILE);
        try {
            transfers = TransitCsv.parseTransfers(reader);
        } finally {
            reader.close();
        }
        TransitGraph graph = TransitGraph.build(intervals, transfers, TransitGraph.DEFAULT_DWELL_MINUTES_PER_STOP);
        return new TransitData(graph, new StationIndex(coords, graph));
    }
}
