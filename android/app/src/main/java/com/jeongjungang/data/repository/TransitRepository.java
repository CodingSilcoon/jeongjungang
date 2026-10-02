package com.jeongjungang.data.repository;

import android.content.Context;
import android.content.res.AssetManager;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

/** assets/data의 CSV로 {@link TransitData}를 한 번만 만들어 앱 전체에서 같이 쓴다. */
public final class TransitRepository {

    private static final String ASSET_DIR = "data/";
    private static volatile TransitRepository instance;

    private final AssetManager assets;
    private TransitData data;

    private TransitRepository(Context context) {
        this.assets = context.getApplicationContext().getAssets();
    }

    public static TransitRepository getInstance(Context context) {
        if (instance == null) {
            synchronized (TransitRepository.class) {
                if (instance == null) {
                    instance = new TransitRepository(context);
                }
            }
        }
        return instance;
    }

    /** 처음 호출할 때 CSV를 읽어 그래프를 만든다. 백그라운드 스레드에서 부른다. */
    public synchronized TransitData get() throws IOException {
        if (data == null) {
            data = TransitData.load(new TransitData.Source() {
                @Override
                public Reader open(String fileName) throws IOException {
                    return new BufferedReader(
                            new InputStreamReader(assets.open(ASSET_DIR + fileName), StandardCharsets.UTF_8));
                }
            });
        }
        return data;
    }
}
