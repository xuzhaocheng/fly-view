package com.feiniu.tv.util;

import android.content.Context;
import android.content.SharedPreferences;

/** 本地持久化:NAS 地址 + 登录 token + 播放偏好。 */
public final class Prefs {

    private static final String NAME = "feiniu_tv";
    private static final String KEY_HOST = "host";
    private static final String KEY_TOKEN = "token";
    private static final String KEY_SUB_SIZE = "sub_size";
    private static final String KEY_SUB_COLOR = "sub_color";

    private final SharedPreferences sp;

    public Prefs(Context context) {
        this.sp = context.getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    public String getHost() {
        return sp.getString(KEY_HOST, null);
    }

    public void setHost(String host) {
        sp.edit().putString(KEY_HOST, host).apply();
    }

    public String getToken() {
        return sp.getString(KEY_TOKEN, null);
    }

    public void setToken(String token) {
        sp.edit().putString(KEY_TOKEN, token).apply();
    }

    public void clearToken() {
        sp.edit().remove(KEY_TOKEN).apply();
    }

    /** 字幕字号档位:0=小 1=中 2=大。 */
    public int getSubtitleSize() {
        return sp.getInt(KEY_SUB_SIZE, 1);
    }

    public void setSubtitleSize(int size) {
        sp.edit().putInt(KEY_SUB_SIZE, size).apply();
    }

    /** 字幕颜色:0=白 1=黄。 */
    public int getSubtitleColor() {
        return sp.getInt(KEY_SUB_COLOR, 0);
    }

    public void setSubtitleColor(int color) {
        sp.edit().putInt(KEY_SUB_COLOR, color).apply();
    }


    /** 投屏接收端显示名,默认“飞牛TV”。 */
    public String getCastName() {
        return sp.getString("cast_name", "飞映");
    }

    public void setCastName(String name) {
        sp.edit().putString("cast_name", name).apply();
    }

    /** 投屏设备 UUID,首次生成后固定。 */
    public String getCastUuid() {
        String u = sp.getString("cast_uuid", null);
        if (u == null) {
            u = java.util.UUID.randomUUID().toString();
            sp.edit().putString("cast_uuid", u).apply();
        }
        return u;
    }

    /** 开机自动启动投屏接收。 */
    public boolean isCastAutostart() {
        return sp.getBoolean("cast_autostart", true);
    }

    public void setCastAutostart(boolean on) {
        sp.edit().putBoolean("cast_autostart", on).apply();
    }
}
