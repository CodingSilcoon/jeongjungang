package com.jeongjungang.push;

import com.jeongjungang.auth.Caller;
import com.jeongjungang.common.ApiException;
import com.jeongjungang.common.ErrorCode;
import com.jeongjungang.push.api.DeviceRequests.RegisterDevice;
import java.time.Clock;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 기기 등록 (docs/API.md 7절 POST /devices). 없으면 만들고 있으면 갱신한다. */
@Service
public class DeviceService {

    private final DeviceRepository devices;
    private final Clock clock;

    DeviceService(DeviceRepository devices, Clock clock) {
        this.devices = devices;
        this.clock = clock;
    }

    @Transactional
    public void register(Caller caller, RegisterDevice request) {
        String appVersion = request.appVersion() == null ? null : request.appVersion().strip();
        try {
            devices.upsert(caller.participantId(), request.fcmToken().strip(), request.platform().name(),
                    appVersion, clock.instant());
        } catch (DataIntegrityViolationException e) {
            // 토큰 확인 직후 참가자가 나갔다(외래키). 이 토큰은 더 이상 유효하지 않다
            throw new ApiException(ErrorCode.UNAUTHORIZED);
        }
    }

    /**
     * FCM이 더 이상 받지 않는 토큰의 기기를 지운다. 한 폰이 여러 약속에 등록돼 있으면 모두 지운다.
     * 푸시는 커밋 뒤에 보내므로 새 트랜잭션에서 한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void removeByFcmToken(String fcmToken) {
        devices.deleteByFcmToken(fcmToken);
    }
}
