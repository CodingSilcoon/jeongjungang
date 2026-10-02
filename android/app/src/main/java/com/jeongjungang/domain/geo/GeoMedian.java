package com.jeongjungang.domain.geo;

import com.jeongjungang.domain.model.LatLng;
import java.util.List;

/** 참가자 좌표들의 기하 중앙값. 순수 Java (Android 의존성 없음). */
public final class GeoMedian {

    private static final int MAX_ITERATIONS = 100;
    private static final double EPSILON = 1e-9;
    /** 이 값보다 가까우면 추정점이 입력점 위에 있다고 보고 0 나눗셈을 피한다. */
    private static final double ZERO_DISTANCE = 1e-12;

    private GeoMedian() {}

    /**
     * n=1은 그 점, n=2는 중점(Weiszfeld는 두 점 사이 모든 점이 해라 유일해가 없음),
     * n>=3은 Weiszfeld(Vardi-Zhang 보정)로 계산한다.
     *
     * @throws IllegalArgumentException points가 null/비어 있거나 유한하지 않은 좌표를 포함한 경우
     */
    public static LatLng compute(List<LatLng> points) {
        validate(points);
        if (points.size() == 1) {
            return points.get(0);
        }
        if (points.size() == 2) {
            return midpoint(points.get(0), points.get(1));
        }
        return weiszfeld(points);
    }

    private static void validate(List<LatLng> points) {
        if (points == null || points.isEmpty()) {
            throw new IllegalArgumentException("참가자 좌표가 필요합니다.");
        }
        for (LatLng p : points) {
            if (p == null || !Double.isFinite(p.lat) || !Double.isFinite(p.lng)) {
                throw new IllegalArgumentException("유효하지 않은 좌표입니다.");
            }
        }
    }

    private static LatLng midpoint(LatLng a, LatLng b) {
        return new LatLng((a.lat + b.lat) / 2, (a.lng + b.lng) / 2);
    }

    /**
     * 경도 1도는 위도에 따라 길이가 달라지므로, 평균 위도의 cos로 경도를 보정한 평면에서 계산한다.
     * 추정점이 입력점과 겹치면 Vardi-Zhang 보정으로 특이점을 처리한다.
     */
    private static LatLng weiszfeld(List<LatLng> points) {
        int n = points.size();
        double meanLat = 0;
        for (LatLng p : points) {
            meanLat += p.lat;
        }
        meanLat /= n;
        double lngScale = Math.cos(Math.toRadians(meanLat));

        double[] xs = new double[n];
        double[] ys = new double[n];
        double x = 0;
        double y = 0;
        for (int i = 0; i < n; i++) {
            xs[i] = points.get(i).lng * lngScale;
            ys[i] = points.get(i).lat;
            x += xs[i];
            y += ys[i];
        }
        x /= n;
        y /= n;

        for (int iter = 0; iter < MAX_ITERATIONS; iter++) {
            double numX = 0;
            double numY = 0;
            double sumW = 0;
            int coincident = 0;
            for (int i = 0; i < n; i++) {
                double d = Math.hypot(xs[i] - x, ys[i] - y);
                if (d < ZERO_DISTANCE) {
                    coincident++;
                    continue;
                }
                numX += xs[i] / d;
                numY += ys[i] / d;
                sumW += 1 / d;
            }
            if (sumW == 0) {
                break; // 모든 점이 추정점과 같은 위치
            }
            double tx = numX / sumW;
            double ty = numY / sumW;
            double nextX = tx;
            double nextY = ty;
            if (coincident > 0) {
                double r = Math.hypot(numX - x * sumW, numY - y * sumW);
                double gamma = r == 0 ? 1 : Math.min(1, coincident / r);
                nextX = (1 - gamma) * tx + gamma * x;
                nextY = (1 - gamma) * ty + gamma * y;
            }
            boolean converged = Math.hypot(nextX - x, nextY - y) < EPSILON;
            x = nextX;
            y = nextY;
            if (converged) {
                break;
            }
        }
        return new LatLng(y, x / lngScale);
    }
}
