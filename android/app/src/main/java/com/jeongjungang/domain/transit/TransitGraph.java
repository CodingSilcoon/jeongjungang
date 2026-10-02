package com.jeongjungang.domain.transit;

import com.jeongjungang.domain.transit.TransitCsv.IntervalRow;
import com.jeongjungang.domain.transit.TransitCsv.TransferRow;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 노드 = (호선, 역), 간선 = 역간 소요시간(양방향 동일 가정), 같은 역명의 다른 호선 노드는 환승 간선.
 * 만드는 규칙은 docs/DATA.md 참고.
 */
public final class TransitGraph {

    /** 환승표에 없는 환승에 쓰는 기본 시간(분). */
    public static final double DEFAULT_TRANSFER_MINUTES = 4.0;

    /**
     * 역마다 정차시간 가정값(분). CSV의 소요시간은 정차시간을 뺀 운행시간이라 구간마다 더해 준다.
     * 구간(간선)마다 한 번 더하므로 도착역 정차도 포함한다. 환승 간선에는 더하지 않는다.
     */
    public static final double DEFAULT_DWELL_MINUTES_PER_STOP = 0.5;

    /** 분기점 역과, 그 지선이 본선과 직통 운행하는지 여부. */
    private static final class Branch {
        final String junction;
        /** true면 셔틀 지선: 분기점에서 환승이 필요하다 (환승표의 "같은 호선" 행이 그 시간). */
        final boolean isShuttle;

        Branch(String junction, boolean isShuttle) {
            this.junction = junction;
            this.isShuttle = isShuttle;
        }
    }

    /**
     * 지선 첫 행은 바로 앞 행이 아니라 분기점 역에서 이어진다. 키는 "호선|지선 첫 역".
     * 2호선 성수지선(용답)·신정지선(도림천)은 셔틀이라 환승이 필요하고,
     * 5호선 마천지선(둔촌동)은 강동에서 직통 분기한다.
     */
    private static final Map<String, Branch> BRANCHES = new HashMap<String, Branch>();

    /** 셔틀 지선이 분기점 역에 붙는 별도 노드의 키 접미사. */
    private static final String SHUTTLE_NODE_SUFFIX = "#지선";

    static {
        BRANCHES.put("2|용답", new Branch("성수", true));
        BRANCHES.put("2|도림천", new Branch("신도림", true));
        BRANCHES.put("5|둔촌동", new Branch("강동", false));
    }

    public static final class Edge {
        public final int to;
        public final double minutes;
        public final boolean isTransfer;

        Edge(int to, double minutes, boolean isTransfer) {
            this.to = to;
            this.minutes = minutes;
            this.isTransfer = isTransfer;
        }
    }

    private final List<String> stationOfNode = new ArrayList<String>();
    private final List<Integer> lineOfNode = new ArrayList<Integer>();
    private final List<List<Edge>> edges = new ArrayList<List<Edge>>();
    private final Map<String, Integer> nodeIndex = new HashMap<String, Integer>();
    private final Map<String, List<Integer>> nodesByStation = new LinkedHashMap<String, List<Integer>>();
    private final Map<String, Double> transferMinutes = new HashMap<String, Double>();

    private TransitGraph() {}

    /** CSV 운행시간 그대로(정차시간 0) 만든다. 앱에서는 정차시간을 넘기는 오버로드를 쓴다. */
    public static TransitGraph build(List<IntervalRow> intervals, List<TransferRow> transfers) {
        return build(intervals, transfers, 0.0);
    }

    /**
     * @param dwellMinutesPerStop 구간마다 더할 정차시간(분). 보통 {@link #DEFAULT_DWELL_MINUTES_PER_STOP}
     * @throws IllegalArgumentException 정차시간이 음수이거나 NaN인 경우
     */
    public static TransitGraph build(List<IntervalRow> intervals, List<TransferRow> transfers,
                                     double dwellMinutesPerStop) {
        if (dwellMinutesPerStop < 0 || Double.isNaN(dwellMinutesPerStop)) {
            throw new IllegalArgumentException("정차시간은 0 이상이어야 합니다: " + dwellMinutesPerStop);
        }
        TransitGraph g = new TransitGraph();
        g.addIntervalEdges(intervals, dwellMinutesPerStop);
        g.addTransferEdges(transfers);
        return g;
    }

    private void addIntervalEdges(List<IntervalRow> intervals, double dwellMinutesPerStop) {
        Map<Integer, Integer> previousNodeByLine = new HashMap<Integer, Integer>();
        for (IntervalRow row : intervals) {
            int node = nodeFor(key(row.line, row.station), row.line, row.station);
            Integer previous = previousNodeByLine.get(row.line);
            boolean isLineStart = row.km == 0 || previous == null;
            if (!isLineStart) {
                addEdge(originOf(row, previous), node, row.minutes + dwellMinutesPerStop, false);
            }
            previousNodeByLine.put(row.line, node);
        }
    }

    private void addTransferEdges(List<TransferRow> transfers) {
        Map<String, Double> tableMinutes = new HashMap<String, Double>();
        for (TransferRow t : transfers) {
            if (t.toLine < 1 || !nodeIndex.containsKey(key(t.line, t.station))
                    || !nodeIndex.containsKey(key(t.toLine, t.station))) {
                continue;
            }
            String pairKey = pairKey(t.line, t.toLine, t.station);
            Double existing = tableMinutes.get(pairKey);
            if (existing == null || t.minutes < existing) {
                tableMinutes.put(pairKey, t.minutes);
            }
        }
        for (Map.Entry<String, List<Integer>> entry : nodesByStation.entrySet()) {
            List<Integer> nodes = entry.getValue();
            for (int i = 0; i < nodes.size(); i++) {
                for (int j = i + 1; j < nodes.size(); j++) {
                    int a = nodes.get(i);
                    int b = nodes.get(j);
                    Double fromTable = tableMinutes.get(pairKey(lineOfNode.get(a), lineOfNode.get(b), entry.getKey()));
                    double minutes = fromTable != null ? fromTable : DEFAULT_TRANSFER_MINUTES;
                    transferMinutes.put(pairKey(lineOfNode.get(a), lineOfNode.get(b), entry.getKey()), minutes);
                    addEdge(a, b, minutes, true);
                }
            }
        }
    }

    /** 이 행의 구간이 어느 노드에서 시작하는지. 지선 첫 행이면 분기점, 아니면 바로 앞 행. */
    private int originOf(IntervalRow row, int previous) {
        Branch branch = BRANCHES.get(row.line + "|" + row.station);
        if (branch == null) {
            return previous;
        }
        if (branch.isShuttle) {
            return nodeFor(key(row.line, branch.junction) + SHUTTLE_NODE_SUFFIX, row.line, branch.junction);
        }
        return nodeFor(key(row.line, branch.junction), row.line, branch.junction);
    }

    private int nodeFor(String k, int line, String station) {
        Integer existing = nodeIndex.get(k);
        if (existing != null) {
            return existing;
        }
        int id = stationOfNode.size();
        nodeIndex.put(k, id);
        stationOfNode.add(station);
        lineOfNode.add(line);
        edges.add(new ArrayList<Edge>());
        List<Integer> sameStation = nodesByStation.get(station);
        if (sameStation == null) {
            sameStation = new ArrayList<Integer>();
            nodesByStation.put(station, sameStation);
        }
        sameStation.add(id);
        return id;
    }

    private void addEdge(int a, int b, double minutes, boolean isTransfer) {
        edges.get(a).add(new Edge(b, minutes, isTransfer));
        edges.get(b).add(new Edge(a, minutes, isTransfer));
    }

    private static String key(int line, String station) {
        return line + "|" + station;
    }

    private static String pairKey(int lineA, int lineB, String station) {
        return Math.min(lineA, lineB) + "|" + Math.max(lineA, lineB) + "|" + station;
    }

    public int nodeCount() {
        return stationOfNode.size();
    }

    public List<Edge> edges(int node) {
        return Collections.unmodifiableList(edges.get(node));
    }

    public String stationOf(int node) {
        return stationOfNode.get(node);
    }

    public boolean hasStation(String station) {
        return nodesByStation.containsKey(station);
    }

    public Set<String> stationNames() {
        return Collections.unmodifiableSet(nodesByStation.keySet());
    }

    /** 같은 역명의 모든 호선 노드. 없으면 빈 목록. */
    public List<Integer> nodesOfStation(String station) {
        List<Integer> nodes = nodesByStation.get(station);
        return nodes == null ? Collections.<Integer>emptyList() : Collections.unmodifiableList(nodes);
    }

    /** 같은 호선에서 바로 이웃한 역 (환승 제외). 없는 역이면 빈 집합. */
    public Set<String> neighborStations(int line, String station) {
        Integer node = nodeIndex.get(key(line, station));
        if (node == null) {
            return Collections.emptySet();
        }
        Set<String> result = new LinkedHashSet<String>();
        for (Edge e : edges.get(node)) {
            if (!e.isTransfer) {
                result.add(stationOfNode.get(e.to));
            }
        }
        return result;
    }

    /** 두 호선 사이 환승 시간(분). 해당 역에서 두 호선이 만나지 않으면 null. */
    public Double transferMinutes(int lineA, int lineB, String station) {
        return transferMinutes.get(pairKey(lineA, lineB, station));
    }
}
