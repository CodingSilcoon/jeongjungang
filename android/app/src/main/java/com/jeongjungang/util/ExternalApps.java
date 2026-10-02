package com.jeongjungang.util;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import com.jeongjungang.domain.model.LatLng;

/**
 * 공유 시트와 지도 앱 길찾기를 연다. 설치 여부는 실행해 보고 실패하면 대체 경로로 간다
 * (Android 11 패키지 가시성 때문에 미리 조회하려면 manifest에 queries가 필요해서 이 방식을 쓴다).
 */
public final class ExternalApps {

    private ExternalApps() {}

    public static void share(Context context, String text) {
        Intent send = new Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, text);
        context.startActivity(Intent.createChooser(send, "약속 장소 공유"));
    }

    /** 카카오맵 앱으로 길찾기. 앱이 없으면 카카오맵 웹으로 연다. */
    public static void openKakaoRoute(Context context, String fromName, LatLng from, String toName, LatLng to) {
        if (!tryOpen(context, MapLinks.kakaoAppRoute(from, to))) {
            tryOpen(context, MapLinks.kakaoWebRoute(fromName, from, toName, to));
        }
    }

    /**
     * 네이버지도 앱으로 길찾기.
     * @return 앱이 없어서 열지 못했으면 false. 화면에서 스토어 이동이나 카카오맵을 권하면 된다
     */
    public static boolean openNaverRoute(Context context, String fromName, LatLng from, String toName, LatLng to) {
        return tryOpen(context, MapLinks.naverAppRoute(fromName, from, toName, to));
    }

    public static void openPlayStore(Context context, String packageName) {
        if (!tryOpen(context, MapLinks.playStore(packageName))) {
            tryOpen(context, "https://play.google.com/store/apps/details?id=" + packageName);
        }
    }

    private static boolean tryOpen(Context context, String uri) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
        if (!(context instanceof android.app.Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        try {
            context.startActivity(intent);
            return true;
        } catch (ActivityNotFoundException e) {
            return false;
        }
    }
}
