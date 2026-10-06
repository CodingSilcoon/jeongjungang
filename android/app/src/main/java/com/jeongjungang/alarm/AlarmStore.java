package com.jeongjungang.alarm;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 예약한 알람 목록을 기기에 저장한다. 재부팅하면 AlarmManager 예약이 사라져서
 * 이 목록으로 다시 예약한다.
 */
final class AlarmStore {

    private static final String PREFS = "alarms";
    private static final String KEY_IDS = "ids";

    private final SharedPreferences prefs;

    AlarmStore(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    synchronized void put(AlarmSpec spec) {
        Set<String> ids = new HashSet<String>(prefs.getStringSet(KEY_IDS, new HashSet<String>()));
        ids.add(spec.id);
        prefs.edit()
                .putStringSet(KEY_IDS, ids)
                .putLong(key(spec.id, "fireAt"), spec.fireAtMillis)
                .putString(key(spec.id, "title"), spec.title)
                .putString(key(spec.id, "place"), spec.place)
                .putLong(key(spec.id, "meetAt"), spec.meetAtMillis)
                .commit();
    }

    synchronized AlarmSpec get(String id) {
        if (id == null || !prefs.getStringSet(KEY_IDS, new HashSet<String>()).contains(id)) {
            return null;
        }
        return new AlarmSpec(id,
                prefs.getLong(key(id, "fireAt"), 0),
                prefs.getString(key(id, "title"), ""),
                prefs.getString(key(id, "place"), null),
                prefs.getLong(key(id, "meetAt"), 0));
    }

    synchronized List<AlarmSpec> all() {
        List<AlarmSpec> list = new ArrayList<AlarmSpec>();
        for (String id : prefs.getStringSet(KEY_IDS, new HashSet<String>())) {
            AlarmSpec spec = get(id);
            if (spec != null) {
                list.add(spec);
            }
        }
        return list;
    }

    synchronized void remove(String id) {
        Set<String> ids = new HashSet<String>(prefs.getStringSet(KEY_IDS, new HashSet<String>()));
        ids.remove(id);
        prefs.edit()
                .putStringSet(KEY_IDS, ids)
                .remove(key(id, "fireAt"))
                .remove(key(id, "title"))
                .remove(key(id, "place"))
                .remove(key(id, "meetAt"))
                .commit();
    }

    synchronized boolean isEmpty() {
        return prefs.getStringSet(KEY_IDS, new HashSet<String>()).isEmpty();
    }

    private static String key(String id, String field) {
        return "alarm." + id + "." + field;
    }
}
