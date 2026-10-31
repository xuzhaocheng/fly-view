package com.feiniu.tv.cast.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import com.feiniu.tv.R;
import com.feiniu.tv.cast.core.CastSession;
import com.feiniu.tv.cast.net.ControlServer;
import com.feiniu.tv.ui.HomeActivity;
import com.feiniu.tv.ui.PlayerActivity;
import com.feiniu.tv.util.Prefs;

import java.io.IOException;

import fi.iki.elonen.NanoHTTPD;

/**
 * DLNA 接收端常驻服务。
 *
 * <p>与 HiCast 的差别只有一处:收到 SetAVTransportURI 后拉起的播放页会把
 * 本机登录态(token)附在请求头上,因为飞牛影视推过来的媒体地址需要鉴权。
 */
public class CastService extends Service {

    private static final String TAG = "FeiniuCast";

    public static final int NOTIF_ID = 2001;
    private static final String CHANNEL_ID = "feiniu_cast";

    private static final int FIRST_PORT = 50000;
    private static final int LAST_PORT = 50009;

    private static volatile CastSession session;
    private static volatile boolean running;
    private static volatile int boundPort;

    private ControlServer server;
    private SsdpAgent ssdp;
    private Thread ssdpThread;
    private WifiManager.MulticastLock multicastLock;
    private Handler mainHandler;
    private boolean initialized;
    private int port;

    public static CastSession session() {
        return session;
    }

    public static boolean isRunning() {
        return running;
    }

    public static int port() {
        return boundPort;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mainHandler = new Handler(Looper.getMainLooper());
        if (initialized) {
            return;
        }

        Prefs prefs = new Prefs(this);
        String name = prefs.getCastName();
        String uuid = prefs.getCastUuid();

        session = new CastSession();
        session.addListener(sessionListener);
        AudioVolumeController volumeController = new AudioVolumeController(this);
        session.setVolumeController(volumeController);
        // 手机滑条默认 50,若不同步,第一次加减都落在错误区间。
        session.syncVolume(volumeController.getVolume());

        acquireMulticastLock();

        port = bindControlServer(name, uuid);
        if (port < 0) {
            Log.e(TAG, "no free control port in " + FIRST_PORT + ".." + LAST_PORT);
            startForegroundCompat();
            stopSelf();
            return;
        }
        Log.i(TAG, "control server listening on port " + port);

        startSsdp(uuid, port);
        startForegroundCompat();

        boundPort = port;
        running = true;
        initialized = true;
        Log.i(TAG, "cast receiver started as \"" + name + "\"");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!initialized) {
            return START_NOT_STICKY;
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onDestroy() {
        Log.i(TAG, "cast receiver stopping");
        running = false;
        boundPort = 0;
        initialized = false;

        if (mainHandler != null) {
            mainHandler.removeCallbacksAndMessages(null);
        }
        if (session != null) {
            session.removeListener(sessionListener);
            session.stop();
        }

        if (ssdp != null) {
            ssdp.stop();
        }
        if (ssdpThread != null) {
            try {
                ssdpThread.join(1500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            ssdpThread = null;
        }
        ssdp = null;

        if (server != null) {
            try {
                server.stop();
            } catch (RuntimeException e) {
                Log.w(TAG, "control server stop failed", e);
            }
            server = null;
        }

        releaseMulticastLock();
        stopForeground(true);
        session = null;
        super.onDestroy();
    }

    private void acquireMulticastLock() {
        WifiManager wm = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        if (wm == null) {
            Log.w(TAG, "no WifiManager (wired device?); continuing without multicast lock");
            return;
        }
        try {
            multicastLock = wm.createMulticastLock(TAG);
            multicastLock.setReferenceCounted(false);
            multicastLock.acquire();
        } catch (RuntimeException e) {
            multicastLock = null;
            Log.w(TAG, "multicast lock unavailable", e);
        }
    }

    private void releaseMulticastLock() {
        if (multicastLock == null) {
            return;
        }
        try {
            if (multicastLock.isHeld()) {
                multicastLock.release();
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "multicast lock release failed", e);
        }
        multicastLock = null;
    }

    private int bindControlServer(String name, String uuid) {
        for (int candidate = FIRST_PORT; candidate <= LAST_PORT; candidate++) {
            ControlServer attempt = new ControlServer(name, uuid, session, candidate);
            try {
                attempt.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
                server = attempt;
                return attempt.port();
            } catch (IOException e) {
                Log.w(TAG, "port " + candidate + " busy: " + e.getMessage());
                try {
                    attempt.stop();
                } catch (RuntimeException ignored) {
                }
            }
        }
        return -1;
    }

    private void startSsdp(String uuid, int httpPort) {
        ssdp = new SsdpAgent(uuid, httpPort);
        ssdpThread = new Thread(ssdp, "feiniu-ssdp");
        ssdpThread.setDaemon(true);
        ssdpThread.start();
    }

    @SuppressWarnings("deprecation")
    private void startForegroundCompat() {
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) {
                NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                        getString(R.string.app_name), NotificationManager.IMPORTANCE_LOW);
                channel.setShowBadge(false);
                nm.createNotificationChannel(channel);
            }
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
            builder.setPriority(Notification.PRIORITY_LOW);
        }

        Intent open = new Intent(this, HomeActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pending = PendingIntent.getActivity(this, 0, open, 0);

        builder.setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle(new Prefs(this).getCastName())
                .setContentText(getString(R.string.cast_notification))
                .setOngoing(true)
                .setContentIntent(pending);
        startForeground(NOTIF_ID, builder.build());
    }

    private final CastSession.Listener sessionListener = new SessionWatcher();

    private final class SessionWatcher implements CastSession.Listener {
        @Override
        public void onSessionChanged(CastSession changed) {
            Log.i(TAG, "session " + changed.state() + " title=\"" + changed.title()
                    + "\" uri=" + changed.uri());
            if (changed.state() != CastSession.State.PREPARING) {
                return;
            }
            final String uri = changed.uri();
            final String title = changed.title();
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    launchPlayer(uri, title);
                }
            });
        }
    }

    private void launchPlayer(String uri, String title) {
        // 播放页开着时它自己监听会话并处理状态变化;再 startActivity 会让
        // onNewIntent 重新 prepare,表现为"按一下音量就重新加载"。
        if (com.feiniu.tv.ui.PlayerActivity.playerAlive) {
            Log.i(TAG, "player already open; letting it handle the session");
            return;
        }
        try {
            Intent player = new Intent(this, PlayerActivity.class);
            player.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            player.putExtra(PlayerActivity.EXTRA_CAST_URL, uri);
            player.putExtra(PlayerActivity.EXTRA_TITLE, title);
            startActivity(player);
            Log.i(TAG, "player launched for " + uri);
        } catch (RuntimeException e) {
            Log.e(TAG, "cannot launch player", e);
        }
    }
}
