package com.jeongjungang.data.repository;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.util.Log;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * 약속마다 받은 참가자 토큰 보관 (docs/API.md 2절).
 * 토큰은 Android Keystore의 AES-GCM 키로 암호화해 SharedPreferences에 둔다. 키는 기기 밖으로 나가지 않는다.
 * (EncryptedSharedPreferences는 2025년에 지원 중단돼서 Keystore를 직접 쓴다.)
 * 앱을 지우면 토큰도 사라지고, 서버에서는 약속에서 빠진 것으로 본다.
 */
public final class ParticipantTokenStore {

    /** 저장된 내 자격 한 건. */
    public static final class Entry {
        public final String meetingId;
        public final String participantId;
        public final String token;

        Entry(String meetingId, String participantId, String token) {
            this.meetingId = meetingId;
            this.participantId = participantId;
            this.token = token;
        }
    }

    private static final String TAG = "ParticipantTokenStore";
    private static final String PREFS = "meeting_tokens";
    private static final String KEY_ALIAS = "jeongjungang.participantToken";
    private static final String KEYSTORE = "AndroidKeyStore";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_BITS = 128;

    private final SharedPreferences prefs;

    public ParticipantTokenStore(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public synchronized void save(String meetingId, String participantId, String token) {
        prefs.edit()
                .putString(meetingId + ".participantId", participantId)
                .putString(meetingId + ".token", encrypt(token))
                .commit();
    }

    /** 없거나 복호화할 수 없으면(키가 사라짐 등) null. */
    public synchronized Entry get(String meetingId) {
        String participantId = prefs.getString(meetingId + ".participantId", null);
        String sealed = prefs.getString(meetingId + ".token", null);
        if (participantId == null || sealed == null) {
            return null;
        }
        String token = decrypt(sealed);
        if (token == null) {
            remove(meetingId);
            return null;
        }
        return new Entry(meetingId, participantId, token);
    }

    /** 내가 들어가 있는 약속 id 목록. 앱을 다시 열었을 때 "내 약속" 표시용. */
    public synchronized List<String> meetingIds() {
        List<String> ids = new ArrayList<String>();
        for (String key : prefs.getAll().keySet()) {
            if (key.endsWith(".participantId")) {
                ids.add(key.substring(0, key.length() - ".participantId".length()));
            }
        }
        return ids;
    }

    public synchronized void remove(String meetingId) {
        prefs.edit()
                .remove(meetingId + ".participantId")
                .remove(meetingId + ".token")
                .commit();
    }

    private static SecretKey key() throws Exception {
        KeyStore ks = KeyStore.getInstance(KEYSTORE);
        ks.load(null);
        KeyStore.Entry entry = ks.getEntry(KEY_ALIAS, null);
        if (entry instanceof KeyStore.SecretKeyEntry) {
            return ((KeyStore.SecretKeyEntry) entry).getSecretKey();
        }
        KeyGenerator gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE);
        gen.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build());
        return gen.generateKey();
    }

    /** 결과: base64(iv) + ":" + base64(암호문). */
    private static String encrypt(String plain) {
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key());
            byte[] sealed = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP) + ":"
                    + Base64.encodeToString(sealed, Base64.NO_WRAP);
        } catch (Exception e) {
            // Keystore가 고장 난 기기에서 토큰을 평문으로 남기지 않는다. 저장 실패로 처리한다.
            throw new IllegalStateException("토큰을 안전하게 저장하지 못했습니다.", e);
        }
    }

    private static String decrypt(String stored) {
        int sep = stored.indexOf(':');
        if (sep < 0) {
            return null;
        }
        try {
            byte[] iv = Base64.decode(stored.substring(0, sep), Base64.NO_WRAP);
            byte[] sealed = Base64.decode(stored.substring(sep + 1), Base64.NO_WRAP);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(sealed), StandardCharsets.UTF_8);
        } catch (Exception e) {
            Log.w(TAG, "토큰 복호화 실패. 다시 참가해야 함", e);
            return null;
        }
    }
}
