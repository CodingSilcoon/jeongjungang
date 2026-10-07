package com.jeongjungang.data.remote.meeting;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.jeongjungang.data.remote.meeting.MeetingModels.Origin;
import com.jeongjungang.domain.model.LatLng;
import org.junit.Test;

public class MeetingRulesTest {

    @Test
    public void inviteCodeFromCodeOrLink() {
        assertEquals("7K3QH9MX", MeetingRules.parseInviteCode("7K3QH9MX"));
        assertEquals("7K3QH9MX", MeetingRules.parseInviteCode(" 7k3q-h9mx "));
        assertEquals("7K3QH9MX", MeetingRules.parseInviteCode("7K3Q H9MX"));
        assertEquals("7K3QH9MX", MeetingRules.parseInviteCode("https://jjg.duckdns.org/m/7K3QH9MX"));
        assertEquals("7K3QH9MX", MeetingRules.parseInviteCode("https://jjg.duckdns.org/m/7K3QH9MX/?utm=kakao"));
        assertEquals("7K3QH9MX", MeetingRules.parseInviteCode("HTTPS://JJG.DUCKDNS.ORG/m/7k3qh9mx#x"));
    }

    @Test
    public void invalidInviteCodes() {
        assertNull(MeetingRules.parseInviteCode(null));
        assertNull(MeetingRules.parseInviteCode(""));
        assertNull(MeetingRules.parseInviteCode("7K3QH9M"));     // 7자리
        assertNull(MeetingRules.parseInviteCode("7K3QH9MXX"));   // 9자리
        for (String bad : new String[] {"I", "L", "O", "U", "0", "1"}) {
            assertNull(bad, MeetingRules.parseInviteCode("7K3QH9M" + bad));
        }
        assertNull(MeetingRules.parseInviteCode("https://jjg.duckdns.org/x/7K3QH9MX"));
    }

    @Test
    public void nickname() {
        assertNull(MeetingRules.checkNickname("민수"));
        assertNull(MeetingRules.checkNickname("  가나다라마바사아자차카타파하가나다라마바  ".trim()));
        assertNotNull(MeetingRules.checkNickname("   "));
        assertNotNull(MeetingRules.checkNickname(null));
        assertNotNull(MeetingRules.checkNickname("가나다라마바사아자차카타파하가나다라마바사")); // 21자
        assertNotNull(MeetingRules.checkNickname("민\n수"));
        // 보이지 않는 글자: 폭 없는 공백, 방향 바꾸기, 줄 구분자, BOM (서버 @Nickname과 같은 규칙)
        assertNotNull(MeetingRules.checkNickname("민\u200B수"));
        assertNotNull(MeetingRules.checkNickname("민\u202E수"));
        assertNotNull(MeetingRules.checkNickname("민\u2028수"));
        assertNotNull(MeetingRules.checkNickname("\uFEFF민수"));
        // 이모지 하나는 한 글자로 센다
        assertNull(MeetingRules.checkNickname("😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀"));
    }

    @Test
    public void meetAtRange() {
        long now = 1_000_000_000_000L;
        long hour = MeetingRules.MEET_AT_PAST_LIMIT_MS;
        assertNull(MeetingRules.checkMeetAt(now, now));
        assertNull("1시간 전까지는 됨", MeetingRules.checkMeetAt(now - hour, now));
        assertNotNull(MeetingRules.checkMeetAt(now - hour - 1, now));
        assertNull("1년 뒤까지는 됨", MeetingRules.checkMeetAt(now + MeetingRules.MEET_AT_FUTURE_LIMIT_MS, now));
        assertNotNull(MeetingRules.checkMeetAt(now + MeetingRules.MEET_AT_FUTURE_LIMIT_MS + 1, now));
    }

    @Test
    public void titleAndOrigin() {
        assertNull(MeetingRules.checkTitle(null));
        assertNull(MeetingRules.checkTitle(""));
        assertNotNull(MeetingRules.checkTitle(new String(new char[41]).replace('\0', '가')));
        assertNull(MeetingRules.checkOrigin(new Origin("노원역", new LatLng(37.6552, 127.0614))));
        assertNotNull(MeetingRules.checkOrigin(new Origin("  ", new LatLng(37.6, 127.0))));
        assertNotNull(MeetingRules.checkOrigin(new Origin("x", new LatLng(91, 127.0))));
    }
}
