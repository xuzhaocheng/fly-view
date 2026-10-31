package com.feiniu.launcher;

import android.content.Context;
import android.content.SharedPreferences;

/** 桌面自身的小配置。 */
final class Prefs {

    private static final String FILE = "launcher";
    private static final String KEY_AUTOSTART = "autostart";

    private Prefs() {
    }

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    static boolean autoStart(Context c) {
        return sp(c).getBoolean(KEY_AUTOSTART, true);
    }

    static void setAutoStart(Context c, boolean on) {
        sp(c).edit().putBoolean(KEY_AUTOSTART, on).apply();
    }
}
