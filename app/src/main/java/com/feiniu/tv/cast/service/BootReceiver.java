package com.feiniu.tv.cast.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import com.feiniu.tv.util.Prefs;

/** 开机拉起投屏接收服务;部分固件会拦截,失败只记录不抛出。 */
public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "FeiniuCastBoot";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            return;
        }
        try {
            Prefs prefs = new Prefs(context);
            if (!prefs.isCastAutostart()) {
                Log.i(TAG, "autostart disabled");
                return;
            }
            Intent service = new Intent(context, CastService.class);
            if (Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(service);
            } else {
                context.startService(service);
            }
            Log.i(TAG, "cast service started from boot");
        } catch (RuntimeException e) {
            Log.w(TAG, "autostart failed", e);
        }
    }
}
