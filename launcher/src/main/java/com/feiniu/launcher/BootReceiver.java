package com.feiniu.launcher;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * 开机后把桌面拉到前台。
 *
 * 背景:这台 Sharp/YunOS 的 root 守护进程 tvos 会在每次开机时把系统桌面
 * com.yunos.tv.homeshell 恢复并设为 HOME,非 root 无法真正替换。
 * 因此在 BOOT_COMPLETED 后主动启动自己,并在一段时间后再补两次,
 * 覆盖系统桌面启动得比我们晚的情况。
 */
public class BootReceiver extends BroadcastReceiver {

    static final String ACTION_RETRY = "com.feiniu.launcher.BOOT_RETRY";

    /** 首次启动之后的补刀时间点(毫秒)。 */
    private static final long[] RETRY_DELAYS = {8000L, 25000L};

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Prefs.autoStart(context)) {
            return;
        }
        // 已经抢到前台了就不再补刀,避免把正在看片的用户拽回桌面
        if (LauncherActivity.shownSinceBoot) {
            return;
        }
        startSelf(context);
        if (ACTION_RETRY.equals(intent.getAction())) {
            return;
        }
        for (int i = 0; i < RETRY_DELAYS.length; i++) {
            scheduleRetry(context, 1000 + i, RETRY_DELAYS[i]);
        }
    }

    private void startSelf(Context context) {
        try {
            Intent i = new Intent(context, LauncherActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            context.startActivity(i);
        } catch (Exception ignored) {
        }
    }

    private void scheduleRetry(Context context, int requestCode, long delayMs) {
        try {
            AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (am == null) {
                return;
            }
            Intent i = new Intent(context, BootReceiver.class).setAction(ACTION_RETRY);
            PendingIntent pi = PendingIntent.getBroadcast(
                    context, requestCode, i, PendingIntent.FLAG_UPDATE_CURRENT);
            // 用开机计时而不是墙上时钟:开机后 NTP 校时会跳变,墙钟闹钟会立刻误触发
            am.set(AlarmManager.ELAPSED_REALTIME,
                    android.os.SystemClock.elapsedRealtime() + delayMs, pi);
        } catch (Exception ignored) {
        }
    }
}
