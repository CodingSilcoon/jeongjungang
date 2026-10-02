package com.jeongjungang.domain.transit;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * 다중 출발 Dijkstra. 비용은 (소요시간, 환승 횟수)를 사전식으로 비교한다.
 * 출발 역마다 접근시간을 초기 비용으로 주고, 그 역에서 어느 호선을 타든 환승 0회로 센다.
 */
public final class RouteFinder {

    private static final double TIME_EPSILON = 1e-9;

    private RouteFinder() {}

    /** 한 역까지의 최소 소요시간과 그 경로의 환승 횟수. */
    public static final class Route {
        public final double minutes;
        public final int transfers;

        Route(double minutes, int transfers) {
            this.minutes = minutes;
            this.transfers = transfers;
        }
    }

    private static final class State implements Comparable<State> {
        final int node;
        final double minutes;
        final int transfers;

        State(int node, double minutes, int transfers) {
            this.node = node;
            this.minutes = minutes;
            this.transfers = transfers;
        }

        @Override
        public int compareTo(State other) {
            return isBetter(minutes, transfers, other.minutes, other.transfers) ? -1
                    : isBetter(other.minutes, other.transfers, minutes, transfers) ? 1 : 0;
        }
    }

    private static boolean isBetter(double minutesA, int transfersA, double minutesB, int transfersB) {
        if (Math.abs(minutesA - minutesB) > TIME_EPSILON) {
            return minutesA < minutesB;
        }
        return transfersA < transfersB;
    }

    /**
     * @param accessMinutesByStation 출발 후보 역 -> 그 역까지 가는 접근시간(분)
     * @return 도달 가능한 역 -> 최소 비용. 도달 못 하는 역은 포함하지 않는다.
     * @throws IllegalArgumentException 출발 역이 비었거나, 그래프에 없거나, 접근시간이 음수/NaN인 경우
     */
    public static Map<String, Route> find(TransitGraph graph, Map<String, Double> accessMinutesByStation) {
        if (accessMinutesByStation == null || accessMinutesByStation.isEmpty()) {
            throw new IllegalArgumentException("출발 역이 필요합니다.");
        }
        int n = graph.nodeCount();
        double[] bestMinutes = new double[n];
        int[] bestTransfers = new int[n];
        Arrays.fill(bestMinutes, Double.POSITIVE_INFINITY);
        PriorityQueue<State> queue = new PriorityQueue<State>();

        for (Map.Entry<String, Double> source : accessMinutesByStation.entrySet()) {
            double access = source.getValue();
            if (!graph.hasStation(source.getKey())) {
                throw new IllegalArgumentException("그래프에 없는 역입니다: " + source.getKey());
            }
            if (access < 0 || Double.isNaN(access)) {
                throw new IllegalArgumentException("접근시간이 올바르지 않습니다: " + source.getKey());
            }
            for (int node : graph.nodesOfStation(source.getKey())) {
                if (isBetter(access, 0, bestMinutes[node], bestTransfers[node])) {
                    bestMinutes[node] = access;
                    bestTransfers[node] = 0;
                    queue.add(new State(node, access, 0));
                }
            }
        }

        while (!queue.isEmpty()) {
            State current = queue.poll();
            if (isBetter(bestMinutes[current.node], bestTransfers[current.node], current.minutes, current.transfers)) {
                continue; // 더 좋은 값이 이미 확정됨
            }
            for (TransitGraph.Edge edge : graph.edges(current.node)) {
                double nextMinutes = current.minutes + edge.minutes;
                int nextTransfers = current.transfers + (edge.isTransfer ? 1 : 0);
                if (isBetter(nextMinutes, nextTransfers, bestMinutes[edge.to], bestTransfers[edge.to])) {
                    bestMinutes[edge.to] = nextMinutes;
                    bestTransfers[edge.to] = nextTransfers;
                    queue.add(new State(edge.to, nextMinutes, nextTransfers));
                }
            }
        }
        return collectBestPerStation(graph, bestMinutes, bestTransfers);
    }

    private static Map<String, Route> collectBestPerStation(TransitGraph graph, double[] minutes, int[] transfers) {
        Map<String, Route> result = new LinkedHashMap<String, Route>();
        for (int node = 0; node < graph.nodeCount(); node++) {
            if (Double.isInfinite(minutes[node])) {
                continue;
            }
            String station = graph.stationOf(node);
            Route existing = result.get(station);
            if (existing == null || isBetter(minutes[node], transfers[node], existing.minutes, existing.transfers)) {
                result.put(station, new Route(minutes[node], transfers[node]));
            }
        }
        return result;
    }
}
