package com.jeongjungang;

import android.app.Application;
import com.jeongjungang.util.KakaoMaps;

/** 앱 시작 시 한 번 하는 초기화. */
public class JeongjungangApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        // 공식 문서가 Application에서 초기화하라고 권한다. 키가 없거나 실패해도 앱은 뜬다.
        KakaoMaps.init(this);
    }
}
