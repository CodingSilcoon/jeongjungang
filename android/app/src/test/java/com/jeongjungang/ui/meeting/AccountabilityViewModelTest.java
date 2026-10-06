package com.jeongjungang.ui.meeting;

import static org.junit.Assert.assertEquals;

import com.jeongjungang.data.remote.ApiException;
import org.junit.Test;

public class AccountabilityViewModelTest {

    @Test
    public void conflictMessagesAreFriendly() {
        assertEquals("모두가 책임 알람에 동의해야 켤 수 있어요.",
                AccountabilityViewModel.messageFor(new ApiException("NOT_ALL_OPTED_IN", 409, "server", 0, null)));
        assertEquals("장소와 시간을 먼저 확정해 주세요.",
                AccountabilityViewModel.messageFor(new ApiException("ALARM_NOT_READY", 409, "server", 0, null)));
        assertEquals("server",
                AccountabilityViewModel.messageFor(new ApiException("OTHER", 400, "server", 0, null)));
    }
}
