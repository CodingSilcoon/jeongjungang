package com.jeongjungang.data.remote;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import com.jeongjungang.data.remote.meeting.HttpMeetingApi;
import com.jeongjungang.data.remote.meeting.MeetingModels.InvitePreview;
import com.jeongjungang.data.remote.meeting.MeetingModels.Member;
import com.jeongjungang.data.remote.meeting.MeetingModels.Membership;
import com.jeongjungang.data.remote.meeting.MeetingModels.Origin;
import com.jeongjungang.data.remote.meeting.MeetingModels.Place;
import com.jeongjungang.data.remote.meeting.MeetingModels.PlacePage;
import com.jeongjungang.data.remote.meeting.MeetingModels.Purpose;
import com.jeongjungang.data.remote.meeting.MeetingModels.Role;
import com.jeongjungang.data.remote.meeting.MeetingModels.Snapshot;
import com.jeongjungang.data.remote.meeting.MeetingModels.Status;
import com.jeongjungang.data.remote.meeting.MeetingUpdate;
import com.jeongjungang.data.remote.meeting.ParticipantUpdate;
import com.jeongjungang.domain.model.LatLng;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.junit.Before;
import org.junit.Test;

/**
 * 앱의 실제 통신 코드(HttpMeetingApi, HttpGeocodeApi)를 실제 서버에 붙여 본다.
 * 평소에는 건너뛰고, 서버를 띄운 뒤 이렇게 돌린다:
 *   cd android && gradlew.bat testDebugUnitTest -PliveServer=http://localhost:8080/api/v1 --tests "*LiveServerContractTest"
 * 주소 검색·주변 장소는 서버에 카카오 REST 키가 있어야 결과가 나온다.
 */
public class LiveServerContractTest {

    private static final String BASE_URL = System.getProperty("liveServer", "");
    private static final LatLng DAPSIMNI = new LatLng(37.5669, 127.0527);
    private static final long ONE_DAY_MILLIS = 24L * 60 * 60 * 1000;

    private HttpMeetingApi meetings;
    private HttpGeocodeApi geocode;

    @Before
    public void setUp() {
        assumeTrue("-PliveServer=<서버 주소>를 줄 때만 실행한다", !BASE_URL.isEmpty());
        meetings = new HttpMeetingApi(BASE_URL);
        geocode = new HttpGeocodeApi(BASE_URL);
    }

    @Test
    public void addressSearchAndPinLookup() throws Exception {
        List<GeoPlace> found = geocode.search("coex", 5);
        assertFalse("코엑스 검색 결과가 있어야 한다", found.isEmpty());
        assertNotNull(found.get(0).location);

        ReverseAddress pin = geocode.reverse(new LatLng(37.5489, 126.9227));
        assertNotNull("핀 주소가 있어야 한다", pin.address);
    }

    @Test
    public void meetingLifecycle_createInviteJoinPollConfirmLeaveCancel() throws Exception {
        // 만들기
        Membership host = meetings.create("민수", "계약 테스트", Purpose.MEAL, null,
                new Origin("홍대입구역", new LatLng(37.5572, 126.9245)));
        assertEquals(Role.HOST, host.role);
        assertNotNull(host.token);
        assertNotNull(host.meeting.inviteUrl);
        String code = host.meeting.inviteCode;

        // 초대 미리보기: 사람이 소문자·하이픈으로 붙여 넣은 코드
        String messy = code.substring(0, 4).toLowerCase(Locale.ROOT) + "-" + code.substring(4);
        InvitePreview preview = meetings.preview(messy);
        assertEquals("민수", preview.hostNickname);
        assertEquals(1, preview.participantCount);

        // 초대 링크를 통째로 붙여 넣어 참가
        Membership member = meetings.join(host.meeting.inviteUrl, "지현",
                new Origin("노원역", new LatLng(37.6552, 127.0614)));
        assertEquals(Role.MEMBER, member.role);
        assertEquals(host.meetingId, member.meetingId);

        // 폴링: 들어온 순서, 내 정보, ETag·304
        Snapshot snap = meetings.get(host.meetingId, member.token, null);
        assertEquals(2, snap.members.size());
        assertEquals("민수", snap.members.get(0).nickname);
        assertEquals(Role.HOST, snap.members.get(0).role);
        assertEquals("지현", snap.members.get(1).nickname);
        assertEquals(member.participantId, snap.myParticipantId);
        assertNotNull(snap.etag);
        assertTrue("서버 시각이 와야 시계 보정을 한다", snap.serverTimeMillis > 0);
        assertNull("바뀐 게 없으면 304라 null", meetings.get(host.meetingId, member.token, snap.etag));

        // 내 정보 수정 → 버전이 바뀌어 새 스냅샷
        meetings.updateParticipant(member.participantId, member.token,
                new ParticipantUpdate().prepMinutes(30).optedIn(true));
        Snapshot afterUpdate = meetings.get(host.meetingId, member.token, snap.etag);
        assertNotNull(afterUpdate);
        Member me = afterUpdate.me();
        assertEquals(Integer.valueOf(30), me.prepMinutes);
        assertTrue(me.optedIn);

        // 방장이 시간·장소 정하고 확정
        long meetAt = System.currentTimeMillis() + ONE_DAY_MILLIS;
        meetings.update(host.meetingId, host.token, new MeetingUpdate()
                .meetAt(meetAt)
                .place(new Place("답십리역", DAPSIMNI, Arrays.asList(5)))
                .confirm());
        Snapshot confirmed = meetings.get(host.meetingId, host.token, null);
        assertEquals(Status.CONFIRMED, confirmed.meeting.status);
        assertEquals("답십리역", confirmed.meeting.place.name);
        assertEquals(Arrays.asList(5), confirmed.meeting.place.lines);
        assertEquals(meetAt / 1000, confirmed.meeting.meetAtMillis / 1000);

        // 확정된 약속에는 못 들어온다. 서버 문구가 그대로 사용자에게 간다
        ApiException closed = expectApiError(() -> meetings.join(code, "늦은사람", null));
        assertEquals("MEETING_CLOSED", closed.code);
        assertNotNull(closed.getMessage());

        // 주변 술집 (약속 목적 DRINK의 카테고리)
        PlacePage bars = meetings.places(DAPSIMNI, Purpose.DRINK.placeCategory, 500, 1);
        assertEquals(1, bars.page);

        // 방장이 나가면 먼저 들어온 지현이 방장, 나간 사람 토큰은 끝
        meetings.removeParticipant(host.participantId, host.token);
        Snapshot handedOver = meetings.get(host.meetingId, member.token, null);
        assertEquals(1, handedOver.members.size());
        assertEquals(Role.HOST, handedOver.myRole);
        assertEquals("UNAUTHORIZED", expectApiError(() -> meetings.get(host.meetingId, host.token, null)).code);

        // 새 방장이 취소하면 약속이 사라진다
        meetings.cancel(member.meetingId, member.token);
        assertEquals("UNAUTHORIZED", expectApiError(() -> meetings.get(member.meetingId, member.token, null)).code);
        assertEquals("MEETING_NOT_FOUND", expectApiError(() -> meetings.preview(code)).code);
    }

    private interface Call {
        void run() throws Exception;
    }

    private static ApiException expectApiError(Call call) throws Exception {
        try {
            call.run();
        } catch (ApiException e) {
            return e;
        }
        fail("ApiException이 나야 한다");
        return null;
    }
}
