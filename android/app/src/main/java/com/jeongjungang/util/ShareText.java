package com.jeongjungang.util;

import com.jeongjungang.domain.recommend.PersonTrip;
import com.jeongjungang.domain.recommend.Recommendation;
import java.util.List;

/** 친구에게 보낼 공유 문구. 카카오톡 등에 그대로 붙여도 읽히는 평문으로 만든다. */
public final class ShareText {

    static final String HEADER = "[정중앙] 우리 만날 곳 후보";
    static final String APPROX_NOTICE = "※ 소요시간은 지하철 공개 데이터로 계산한 근사값이에요. 실제 경로는 지도 앱에서 확인해 주세요.";

    private ShareText() {}

    /** 후보 전체(순위 순서). */
    public static String forAll(List<Recommendation> recommendations) {
        if (recommendations == null || recommendations.isEmpty()) {
            throw new IllegalArgumentException("공유할 후보가 없습니다.");
        }
        StringBuilder sb = new StringBuilder(HEADER).append('\n');
        for (int i = 0; i < recommendations.size(); i++) {
            sb.append('\n');
            appendCandidate(sb, (i + 1) + ". ", recommendations.get(i));
        }
        return sb.append('\n').append(APPROX_NOTICE).toString();
    }

    /** 상세 화면에서 한 곳만 공유할 때. */
    public static String forOne(Recommendation recommendation) {
        StringBuilder sb = new StringBuilder("[정중앙] 여기서 만나요").append("\n\n");
        appendCandidate(sb, "", recommendation);
        return sb.append('\n').append(APPROX_NOTICE).toString();
    }

    /** 화면과 공유 문구에서 같이 쓰는 역 표시 이름. 예: "동대문역사문화공원역 (2·4·5호선)" */
    public static String stationLabel(Recommendation r) {
        StringBuilder sb = new StringBuilder(r.station).append("역");
        if (!r.lines.isEmpty()) {
            sb.append(" (");
            for (int i = 0; i < r.lines.size(); i++) {
                if (i > 0) {
                    sb.append('·');
                }
                sb.append(r.lines.get(i));
            }
            sb.append("호선)");
        }
        return sb.toString();
    }

    /** 예: "민수 25분 · 환승 없음", "지영 35분 · 환승 1회" */
    public static String tripLabel(PersonTrip t) {
        return t.name + " " + Math.round(t.minutes) + "분 · "
                + (t.transfers == 0 ? "환승 없음" : "환승 " + t.transfers + "회");
    }

    private static void appendCandidate(StringBuilder sb, String prefix, Recommendation r) {
        sb.append(prefix).append(stationLabel(r)).append('\n');
        sb.append("   ").append(r.reason).append('\n');
        for (PersonTrip t : r.trips) {
            sb.append("   - ").append(tripLabel(t)).append('\n');
        }
    }
}
