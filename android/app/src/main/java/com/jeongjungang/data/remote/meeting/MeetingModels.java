package com.jeongjungang.data.remote.meeting;

import com.jeongjungang.domain.model.LatLng;
import com.jeongjungang.domain.recommend.Participant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** docs/API.md 6절 약속·초대 응답 모델. 서버 JSON은 {@link MeetingJson}이 변환한다. */
public final class MeetingModels {

    private MeetingModels() {}

    /** 약속 목적. 장소 검색 카테고리와 연결된다. */
    public enum Purpose {
        MEAL("FOOD"), CAFE("CAFE"), DRINK("BAR"), ETC(null);

        /** `GET /places`의 category. ETC는 null(사용자가 고른다). */
        public final String placeCategory;

        Purpose(String placeCategory) {
            this.placeCategory = placeCategory;
        }

        static Purpose parse(String s) {
            for (Purpose p : values()) {
                if (p.name().equals(s)) {
                    return p;
                }
            }
            return ETC;
        }
    }

    public enum Status {
        OPEN, CONFIRMED,
        /** 앱이 모르는 값(서버가 새 상태를 추가한 경우). */
        UNKNOWN;

        static Status parse(String s) {
            for (Status st : values()) {
                if (st.name().equals(s)) {
                    return st;
                }
            }
            return UNKNOWN;
        }
    }

    public enum Role {
        HOST, MEMBER;

        static Role parse(String s) {
            return "HOST".equals(s) ? HOST : MEMBER;
        }
    }

    /** 출발지. label은 화면 표시용 (1~60자). */
    public static final class Origin {
        public final String label;
        public final LatLng location;

        public Origin(String label, LatLng location) {
            if (label == null || location == null) {
                throw new IllegalArgumentException("출발지 이름과 좌표가 필요합니다.");
            }
            this.label = label;
            this.location = location;
        }
    }

    /** 확정한 장소(보통 역). */
    public static final class Place {
        public final String name;
        public final LatLng location;
        public final List<Integer> lines;

        public Place(String name, LatLng location, List<Integer> lines) {
            this.name = name;
            this.location = location;
            this.lines = Collections.unmodifiableList(new ArrayList<Integer>(lines));
        }
    }

    public static final class Accountability {
        public final boolean enabled;
        public final int gracePeriodSec;
        public final int marginMinutes;

        Accountability(boolean enabled, int gracePeriodSec, int marginMinutes) {
            this.enabled = enabled;
            this.gracePeriodSec = gracePeriodSec;
            this.marginMinutes = marginMinutes;
        }
    }

    public static final class Meeting {
        public final String id;
        /** 만들 때 응답에만 있다. 상태 조회에서는 null일 수 있다. */
        public final String inviteCode;
        public final String inviteUrl;
        public final String title;
        public final Purpose purpose;
        /** 약속 시각(epoch ms). 안 정했으면 null. */
        public final Long meetAtMillis;
        public final Status status;
        /** 확정 장소. 없으면 null. */
        public final Place place;
        public final Long expiresAtMillis;
        /** 상태 조회에만 있다. 없으면 null. */
        public final Accountability accountability;

        Meeting(String id, String inviteCode, String inviteUrl, String title, Purpose purpose, Long meetAtMillis,
                Status status, Place place, Long expiresAtMillis, Accountability accountability) {
            this.id = id;
            this.inviteCode = inviteCode;
            this.inviteUrl = inviteUrl;
            this.title = title;
            this.purpose = purpose;
            this.meetAtMillis = meetAtMillis;
            this.status = status;
            this.place = place;
            this.expiresAtMillis = expiresAtMillis;
            this.accountability = accountability;
        }
    }

    /** 약속 참가자. 목록은 들어온 순서다(첫 번째가 가장 먼저 들어온 사람). */
    public static final class Member {
        public final String id;
        public final String nickname;
        public final Role role;
        /** 아직 출발지를 안 넣었으면 null. */
        public final Origin origin;
        public final Integer prepMinutes;
        public final boolean optedIn;

        Member(String id, String nickname, Role role, Origin origin, Integer prepMinutes, boolean optedIn) {
            this.id = id;
            this.nickname = nickname;
            this.role = role;
            this.origin = origin;
            this.prepMinutes = prepMinutes;
            this.optedIn = optedIn;
        }
    }

    /** `GET /meetings/{id}` 한 번의 결과. */
    public static final class Snapshot {
        public final long version;
        public final Meeting meeting;
        public final List<Member> members;
        public final String myParticipantId;
        public final Role myRole;
        /** 서버 시각(epoch ms). 기기 시계 보정용. 없으면 0. */
        public final long serverTimeMillis;
        /** 다음 요청의 If-None-Match에 넣을 값. */
        public final String etag;

        Snapshot(long version, Meeting meeting, List<Member> members, String myParticipantId, Role myRole,
                 long serverTimeMillis, String etag) {
            this.version = version;
            this.meeting = meeting;
            this.members = Collections.unmodifiableList(new ArrayList<Member>(members));
            this.myParticipantId = myParticipantId;
            this.myRole = myRole;
            this.serverTimeMillis = serverTimeMillis;
            this.etag = etag;
        }

        public boolean amHost() {
            return myRole == Role.HOST;
        }

        public Member me() {
            for (Member m : members) {
                if (m.id.equals(myParticipantId)) {
                    return m;
                }
            }
            return null;
        }

        /** 출발지를 넣은 참가자만 추천기({@code Recommender})에 넘길 형태로. 순서는 들어온 순서. */
        public List<Participant> recommendParticipants() {
            List<Participant> out = new ArrayList<Participant>();
            for (Member m : members) {
                if (m.origin != null) {
                    out.add(new Participant(m.nickname, m.origin.location));
                }
            }
            return out;
        }

        /** 출발지를 아직 안 넣은 사람 수. "2명이 아직 위치를 안 넣었어요" 표시용. */
        public int membersWithoutOrigin() {
            int n = 0;
            for (Member m : members) {
                if (m.origin == null) {
                    n++;
                }
            }
            return n;
        }
    }

    /** 초대 화면 미리보기. 위치 같은 개인 정보는 없다. */
    public static final class InvitePreview {
        public final String title;
        public final String hostNickname;
        public final int participantCount;
        public final Status status;
        public final Long meetAtMillis;

        InvitePreview(String title, String hostNickname, int participantCount, Status status, Long meetAtMillis) {
            this.title = title;
            this.hostNickname = hostNickname;
            this.participantCount = participantCount;
            this.status = status;
            this.meetAtMillis = meetAtMillis;
        }
    }

    /** 약속을 만들거나 참가했을 때 받는 내 자격. 토큰은 {@code ParticipantTokenStore}에 저장한다. */
    public static final class Membership {
        public final String meetingId;
        public final String participantId;
        public final Role role;
        public final String token;
        /** 약속을 만들 때만 있다(초대 코드·링크 포함). 참가할 때는 null. */
        public final Meeting meeting;

        Membership(String meetingId, String participantId, Role role, String token, Meeting meeting) {
            this.meetingId = meetingId;
            this.participantId = participantId;
            this.role = role;
            this.token = token;
            this.meeting = meeting;
        }
    }

    /** 약속 장소 주변 가게 한 곳 (`GET /places`). */
    public static final class NearbyPlace {
        public final String name;
        /** 카카오 분류. 예: "한식" */
        public final String category;
        public final String address;
        public final LatLng location;
        public final int distanceMeters;
        /** 카카오맵 장소 페이지. 없으면 null. */
        public final String placeUrl;

        NearbyPlace(String name, String category, String address, LatLng location, int distanceMeters,
                    String placeUrl) {
            this.name = name;
            this.category = category;
            this.address = address;
            this.location = location;
            this.distanceMeters = distanceMeters;
            this.placeUrl = placeUrl;
        }
    }

    public static final class PlacePage {
        public final List<NearbyPlace> items;
        public final int page;
        public final boolean hasNext;

        PlacePage(List<NearbyPlace> items, int page, boolean hasNext) {
            this.items = Collections.unmodifiableList(new ArrayList<NearbyPlace>(items));
            this.page = page;
            this.hasNext = hasNext;
        }
    }
}
