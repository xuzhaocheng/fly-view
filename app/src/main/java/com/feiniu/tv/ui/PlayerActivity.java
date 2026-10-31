package com.feiniu.tv.ui;

import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.feiniu.tv.R;
import com.feiniu.tv.api.FnApiClient;
import com.feiniu.tv.api.SubtitleLoader;
import com.feiniu.tv.cast.core.CastSession;
import com.feiniu.tv.cast.service.CastService;
import com.feiniu.tv.api.model.PlaySession;
import com.feiniu.tv.api.model.Quality;
import com.feiniu.tv.api.model.StreamList;
import com.feiniu.tv.api.model.SubtitleCue;
import com.feiniu.tv.util.Err;
import com.feiniu.tv.util.Prefs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 播放器:MediaPlayer + SurfaceView 硬解 H.264 + 客户端字幕渲染。 */
public class PlayerActivity extends AppCompatActivity implements SurfaceHolder.Callback {

    private SurfaceView surfaceView;
    private LinearLayout llControls;
    private LinearLayout llSubtitleMenu;
    private TextView tvTitle;
    private TextView tvTime;
    private TextView tvLoading;
    private TextView tvSubtitle;
    private TextView tvCountdown;
    private SeekBar seekBar;

    private MediaPlayer player;
    private FnApiClient client;
    private Prefs prefs;
    private String guid;
    private String nextGuid;
    private String nextTitle;
    private String host;
    private String token;
    private String playUrl;

    /** 投屏模式:直接播放外部推送的 URL(飞牛媒体地址需要登录态)。 */
    public static final String EXTRA_CAST_URL = "cast_url";
    public static final String EXTRA_TITLE = "title";
    private boolean castMode;
    private String castUrl;
    private CastSession castSession;
    private CastSession.State handledState;
    private long castToken = -1;
    /** 投屏播放页是否存活:服务据此决定要不要拉起新实例。 */
    public static volatile boolean playerAlive;
    private volatile boolean destroyed;
    private boolean surfaceReady;
    private boolean pendingPrepare;

    private SubtitleLoader subtitleLoader;
    private PlaySession currentSession;
    private List<StreamList.SubtitleStream> subtitleStreams = new ArrayList<>();
    private List<Quality> qualityList = new ArrayList<>();
    private List<StreamList.Stream> audioStreams = new ArrayList<>();
    private String currentAudioGuid = null;
    private String currentResolution = null;
    private long currentBitrate = -1;
    private String currentSubtitleGuid = null; // null=默认, ""=关闭, 其他=指定 guid
    private boolean menuOpen = false;

    private final Handler handler = new Handler();
    private boolean controlsVisible = false;
    private boolean prepared = false;
    private boolean userSeeking = false;
    /** seekTo 是异步的,完成前 getCurrentPosition 还是旧值;-1 表示没有待完成的 seek。 */
    private int pendingSeekMs = -1;
    private long pendingSeekAt = 0;
    private int progressTick = 0;
    private int countdownSeconds = 0;
    private boolean skippedOpening = false;
    private boolean skippedEnding = false;

    private final Runnable countdownRunnable = new Runnable() {
        @Override
        public void run() {
            if (countdownSeconds <= 0) {
                tvCountdown.setVisibility(View.GONE);
                playNextEpisode();
                return;
            }
            tvCountdown.setText("即将播放下一集\n" + countdownSeconds + " 秒  (按任意键取消)");
            tvCountdown.setVisibility(View.VISIBLE);
            countdownSeconds--;
            handler.postDelayed(this, 1000);
        }
    };

    private final Runnable progressUpdater = new Runnable() {
        @Override
        public void run() {
            updateProgress();
            handler.postDelayed(this, 500);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);

        prefs = new Prefs(this);
        host = prefs.getHost();
        token = prefs.getToken();
        castUrl = getIntent().getStringExtra(EXTRA_CAST_URL);
        castMode = !TextUtils.isEmpty(castUrl);
        if (castMode) {
            castSession = CastService.session();
            playerAlive = true;
            if (castSession != null) {
                castSession.addListener(castListener);
                castSession.addSeekListener(castSeekListener);
            }
        }
        guid = getIntent().getStringExtra("guid");
        nextGuid = getIntent().getStringExtra("next_guid");
        nextTitle = getIntent().getStringExtra("next_title");
        if (castMode) {
            if (TextUtils.isEmpty(castUrl)) {
                finish();
                return;
            }
        } else if (TextUtils.isEmpty(host) || TextUtils.isEmpty(token) || TextUtils.isEmpty(guid)) {
            finish();
            return;
        }
        if (!TextUtils.isEmpty(host)) {
            client = new FnApiClient(host);
            client.setToken(token);
        }

        surfaceView = findViewById(R.id.surface_view);
        llControls = findViewById(R.id.ll_controls);
        llSubtitleMenu = findViewById(R.id.ll_subtitle_menu);
        tvTitle = findViewById(R.id.tv_player_title);
        tvTime = findViewById(R.id.tv_time);
        tvLoading = findViewById(R.id.tv_loading);
        tvSubtitle = findViewById(R.id.tv_subtitle);
        tvCountdown = findViewById(R.id.tv_countdown);
        seekBar = findViewById(R.id.seek_bar);
        surfaceView.getHolder().addCallback(this);
        applySubtitleStyle();

        String title = getIntent().getStringExtra("title");
        if (castMode) {
            showCastTitle(title);
        } else {
            tvTitle.setText(TextUtils.isEmpty(title) ? "" : title);
        }

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                userSeeking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                userSeeking = false;
                if (player != null && prepared) {
                    seekTo(seekBar.getProgress() * 1000);
                }
            }
        });

        if (castMode) {
            playUrl = buildFullUrl(host == null ? "" : host, castUrl);
            prepare();
        } else {
            loadSubtitleStreams();
            loadPlay(currentSubtitleGuid, currentAudioGuid, -1, currentResolution, currentBitrate);
        }
    }

    /** 同一实例被新的投屏复用时,直接切到新地址。 */
    @Override
    protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        String url = intent == null ? null : intent.getStringExtra(EXTRA_CAST_URL);
        if (TextUtils.isEmpty(url)) {
            return;
        }
        castUrl = url;
        castMode = true;
        showCastTitle(intent.getStringExtra(EXTRA_TITLE));
        playUrl = buildFullUrl(host == null ? "" : host, castUrl);
        prepare();
    }

    /**
     * 投屏时控制条上的标题。发送端多数只在 DIDL 里带 dc:title,拿不到就退回
     * 地址里的文件名(纯文件投屏很常见),再没有就显示「投屏播放」,不要留空。
     */
    private void showCastTitle(String castTitle) {
        String text = castTitle == null ? "" : castTitle.trim();
        if (text.isEmpty()) {
            text = titleFromUrl(castUrl);
        }
        tvTitle.setText(text.isEmpty() ? "投屏播放" : text);
    }

    private static String titleFromUrl(String url) {
        if (url == null) {
            return "";
        }
        int q = url.indexOf('?');
        String path = q >= 0 ? url.substring(0, q) : url;
        int slash = path.lastIndexOf('/');
        String name = slash >= 0 ? path.substring(slash + 1) : path;
        try {
            name = java.net.URLDecoder.decode(name, "UTF-8");
        } catch (Exception ignored) {
        }
        int dot = name.lastIndexOf('.');
        if (dot <= 0) {
            // 没有扩展名多半是 guid 之类的内部地址,别拿它当片名。
            return "";
        }
        return name.substring(0, dot).trim();
    }

    /** 后台加载字幕轨 + 画质 + 音轨列表。 */
    private void loadSubtitleStreams() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final List<StreamList.SubtitleStream> list = client.getSubtitleStreams(guid);
                    final List<Quality> qualities = client.getQualityList(guid);
                    final List<StreamList.Stream> audios = client.getAudioStreams(guid);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            subtitleStreams = list != null ? list : new ArrayList<StreamList.SubtitleStream>();
                            qualityList = qualities != null ? qualities : new ArrayList<Quality>();
                            audioStreams = audios != null ? audios : new ArrayList<StreamList.Stream>();
                        }
                    });
                } catch (Exception e) {
                    Log.e("FeiniuTV", "getSubtitleStreams failed", e);
                }
            }
        }).start();
    }

    /** 后台获取播放地址 + 下载字幕。 */
    private void loadPlay(final String subtitleGuid, final String audioGuid, final long startTs,
                          final String resolution, final long bitrate) {
        tvLoading.setVisibility(View.VISIBLE);
        tvLoading.setText("加载中...");
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final PlaySession session = client.getPlayLink(guid, subtitleGuid, audioGuid, startTs, resolution, bitrate);
                    final String fullUrl = buildFullUrl(host, session.playLink);
                    final SubtitleLoader loader = new SubtitleLoader(fullUrl);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (subtitleLoader != null) {
                                subtitleLoader.close();
                            }
                            subtitleLoader = loader;
                            currentSession = session;
                            playUrl = fullUrl;
                            prepare();
                        }
                    });
                } catch (final Exception e) {
                    Log.e("FeiniuTV", "getPlayLink failed", e);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            tvLoading.setText("获取播放地址失败:\n" + Err.describe(e));
                        }
                    });
                }
            }
        }).start();
    }

    private final CastSession.Listener castListener = new CastSession.Listener() {
        @Override
        public void onSessionChanged(CastSession changed) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    reconcileCast();
                }
            });
        }
    };

    private final CastSession.SeekListener castSeekListener = new CastSession.SeekListener() {
        @Override
        public void onSeek(final long positionMs) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (player != null && prepared) {
                        seekTo((int) positionMs);
                    }
                }
            });
        }
    };

    /** 把 CastSession 的状态翻译成播放器命令;电平触发,自带去重。 */
    private void reconcileCast() {
        if (destroyed || !castMode || castSession == null) {
            return;
        }
        CastSession.State st = castSession.state();
        if (st == handledState) {
            return;
        }
        handledState = st;
        switch (st) {
            case PLAYING:
                if (player != null && prepared && !player.isPlaying()) {
                    player.start();
                }
                break;
            case PAUSED:
                if (player != null && prepared && player.isPlaying()) {
                    player.pause();
                }
                break;
            case STOPPED:
                finish();
                break;
            case PREPARING:
                if (castToken != castSession.prepareToken()) {
                    castToken = castSession.prepareToken();
                    castUrl = castSession.uri();
                    showCastTitle(castSession.title());
                    playUrl = buildFullUrl(host == null ? "" : host, castSession.uri());
                    prepare();
                }
                break;
            default:
                break;
        }
    }

    private static String buildFullUrl(String host, String link) {
        if (link.startsWith("http://") || link.startsWith("https://")) {
            return link;
        }
        if (link.startsWith("/")) {
            return host + link;
        }
        return host + "/" + link;
    }

    private void prepare() {
        if (playUrl == null) {
            return;
        }
        if (!surfaceReady) {
            // Cast mode can arrive before the SurfaceView exists; setDisplay
            // would throw "surface has been released". Wait for the callback.
            pendingPrepare = true;
            return;
        }
        if (castMode && castSession != null) {
            handledState = CastSession.State.PREPARING;
            castToken = castSession.prepareToken();
        }
        tvLoading.setText("缓冲中...");
        releasePlayer();
        skippedOpening = false;
        skippedEnding = false;
        pendingSeekMs = -1;
        player = new MediaPlayer();
        player.setAudioStreamType(AudioManager.STREAM_MUSIC);
        try {
            Uri uri = Uri.parse(playUrl);
            Map<String, String> headers = new HashMap<>();
            if (token != null && !token.isEmpty()) {
                headers.put("Authorization", token);
            }
            player.setDataSource(this, uri, headers);
            player.setDisplay(surfaceView.getHolder());
            player.setScreenOnWhilePlaying(true);
            player.setOnSeekCompleteListener(new MediaPlayer.OnSeekCompleteListener() {
                @Override
                public void onSeekComplete(MediaPlayer mp) {
                    pendingSeekMs = -1;
                }
            });
            player.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    prepared = true;
                    tvLoading.setVisibility(View.GONE);
                    seekBar.setMax(mp.getDuration() / 1000);
                    boolean wantPlay = true;
                    if (castMode && castSession != null) {
                        castSession.onPrepared(mp.getDuration());
                        wantPlay = castSession.state() == CastSession.State.PLAYING;
                        handledState = castSession.state();
                    }
                    if (wantPlay) {
                        mp.start();
                    }
                    handler.post(progressUpdater);
                }
            });
            player.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer mp, int what, int extra) {
                    tvLoading.setVisibility(View.VISIBLE);
                    tvLoading.setText("播放出错(" + what + "," + extra + ")");
                    return true;
                }
            });
            player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    if (castMode) {
                        finish();
                    } else {
                        startNextCountdown();
                    }
                }
            });
            player.prepareAsync();
        } catch (Exception e) {
            Log.e("FeiniuTV", "prepare failed", e);
            tvLoading.setText("播放失败:\n" + Err.describe(e));
        }
    }

    private void updateProgress() {
        if (player != null && prepared && !userSeeking) {
            int dur = player.getDuration() / 1000;
            if (pendingSeekMs >= 0 && System.currentTimeMillis() - pendingSeekAt > 5000) {
                // seek 迟迟没回调(流不可 seek),别把进度条卡在目标位置。
                pendingSeekMs = -1;
            }
            if (pendingSeekMs >= 0) {
                // seek 还没落地,别用旧位置把进度条拨回去。
                seekBar.setProgress(pendingSeekMs / 1000);
                tvTime.setText(formatTime(pendingSeekMs / 1000) + " / " + formatTime(dur));
                return;
            }
            int cur = player.getCurrentPosition() / 1000;
            seekBar.setProgress(cur);
            tvTime.setText(formatTime(cur) + " / " + formatTime(dur));
            checkSkip(player.getCurrentPosition());
            updateSubtitle(player.getCurrentPosition());
            progressTick++;
            if (castMode && castSession != null) {
                castSession.updatePosition(player.getCurrentPosition(), player.getDuration());
            }
            if (progressTick % 20 == 0) { // 每 10 秒上报一次进度
                reportProgress();
            }
        }
    }

    /** 片头片尾自动跳过(有配置才生效)。 */
    private void checkSkip(long posMs) {
        if (currentSession == null || player == null) {
            return;
        }
        double posSec = posMs / 1000.0;
        if (!skippedOpening && currentSession.skipOpeningStart >= 0
                && posSec >= currentSession.skipOpeningStart && posSec < currentSession.skipOpeningEnd) {
            skippedOpening = true;
            seekTo((int) (currentSession.skipOpeningEnd * 1000));
            return;
        }
        if (!skippedEnding && currentSession.skipEndingStart >= 0
                && posSec >= currentSession.skipEndingStart && posSec < currentSession.skipEndingEnd) {
            skippedEnding = true;
            seekTo((int) (currentSession.skipEndingEnd * 1000));
        }
    }

    /** 上报播放进度到飞牛服务器(异步,失败静默)。 */
    private void reportProgress() {
        if (player == null || !prepared || currentSession == null) {
            return;
        }
        final String itemGuid = guid;
        final PlaySession session = currentSession;
        final long ts = player.getCurrentPosition() / 1000;
        final long dur = player.getDuration() / 1000;
        final String sub = currentSubtitleGuid;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    client.reportPlayProgress(itemGuid, session, ts, dur, sub);
                } catch (Exception ignored) {
                }
            }
        }).start();
    }

    private void updateSubtitle(long posMs) {
        if (subtitleLoader == null || subtitleLoader.isEmpty()) {
            tvSubtitle.setVisibility(View.GONE);
            return;
        }
        subtitleLoader.onPositionChanged(posMs);
        SubtitleCue cue = subtitleLoader.getCue(posMs);
        if (cue != null && cue.text != null && !cue.text.isEmpty()) {
            tvSubtitle.setText(cue.text);
            tvSubtitle.setVisibility(View.VISIBLE);
        } else {
            tvSubtitle.setVisibility(View.GONE);
        }
    }

    private static String formatTime(int seconds) {
        int h = seconds / 3600;
        int m = (seconds % 3600) / 60;
        int s = seconds % 60;
        if (h > 0) {
            return String.format("%d:%02d:%02d", h, m, s);
        }
        return String.format("%02d:%02d", m, s);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (tvCountdown != null && tvCountdown.getVisibility() == View.VISIBLE) {
            // 倒计时中,任意键取消
            cancelCountdown();
            return true;
        }
        if (menuOpen) {
            // 菜单打开时:只拦截 BACK/MENU 关闭菜单,其余按键(方向键焦点导航、OK 触发点击)交给系统
            if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_MENU) {
                hideSubtitleMenu();
                return true;
            }
            return super.onKeyDown(keyCode, event);
        }

        switch (keyCode) {
            case KeyEvent.KEYCODE_MENU:
                showSubtitleMenu();
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                if (!controlsVisible) {
                    showControls(true);
                } else {
                    togglePause();
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                seekBy(-10);
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                seekBy(10);
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                changeVolume(1);
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                changeVolume(-1);
                return true;
            case KeyEvent.KEYCODE_BACK:
                if (castMode && castSession != null) {
                    castSession.stop();
                }
                finish();
                return true;
            default:
                break;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void showControls(boolean show) {
        controlsVisible = show;
        llControls.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) {
            handler.removeCallbacks(hideControlsRunnable);
            handler.postDelayed(hideControlsRunnable, 4000);
        }
    }

    private final Runnable hideControlsRunnable = new Runnable() {
        @Override
        public void run() {
            showControls(false);
        }
    };

    private void togglePause() {
        if (player == null || !prepared) {
            return;
        }
        if (player.isPlaying()) {
            player.pause();
        } else {
            player.start();
        }
        showControls(true);
    }

    private void seekBy(int seconds) {
        if (player == null || !prepared) {
            return;
        }
        // 连续快进/快退时以上一次的目标为基准,否则每次都从旧位置起跳,连按只前进一次。
        int base = pendingSeekMs >= 0 ? pendingSeekMs : player.getCurrentPosition();
        seekTo(base + seconds * 1000);
        showControls(true);
    }

    private void seekTo(int ms) {
        if (player == null || !prepared) {
            return;
        }
        int dur = player.getDuration();
        if (ms < 0) {
            ms = 0;
        }
        if (dur > 0 && ms > dur) {
            ms = dur;
        }
        pendingSeekMs = ms;
        pendingSeekAt = System.currentTimeMillis();
        seekBar.setProgress(ms / 1000);
        tvTime.setText(formatTime(ms / 1000) + " / " + formatTime(dur / 1000));
        player.seekTo(ms);
    }

    private void changeVolume(int delta) {
        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (am == null) {
            return;
        }
        int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int cur = am.getStreamVolume(AudioManager.STREAM_MUSIC);
        int next = Math.max(0, Math.min(max, cur + delta));
        am.setStreamVolume(AudioManager.STREAM_MUSIC, next, 0);
        showControls(true);
    }

    // ---------- 字幕菜单 ----------

    private void showSubtitleMenu() {
        menuOpen = true;
        llSubtitleMenu.removeAllViews();

        addHeader("画质");
        if (qualityList.isEmpty()) {
            addHeader("  (加载中...)");
        } else {
            java.util.Set<String> seen = new java.util.HashSet<>();
            for (final Quality q : qualityList) {
                if (q.resolution == null || seen.contains(q.resolution)) {
                    continue;
                }
                seen.add(q.resolution);
                addQualityItem(q);
            }
        }

        addHeader("字幕");
        addSubtitleItem("关闭字幕", "");
        for (final StreamList.SubtitleStream s : subtitleStreams) {
            addSubtitleItem(s.title != null && !s.title.isEmpty() ? s.title : (s.language + " " + s.format),
                    s.guid);
        }

        if (audioStreams != null && audioStreams.size() > 1) {
            addHeader("音轨");
            for (final StreamList.Stream a : audioStreams) {
                addAudioItem(a);
            }
        }

        addHeader("字幕样式");
        addSizeCycleItem();
        addColorCycleItem();

        llSubtitleMenu.setVisibility(View.VISIBLE);
        if (llSubtitleMenu.getChildCount() > 0) {
            llSubtitleMenu.getChildAt(0).requestFocus();
        }
        showControls(false);
    }

    private static String sizeLabel(int s) {
        return s == 0 ? "小" : s == 2 ? "大" : "中";
    }

    private void addSizeCycleItem() {
        final TextView tv = makeMenuTextView("字号: " + sizeLabel(prefs.getSubtitleSize()));
        tv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int s = (prefs.getSubtitleSize() + 1) % 3;
                prefs.setSubtitleSize(s);
                applySubtitleStyle();
                tv.setText("字号: " + sizeLabel(s));
            }
        });
        llSubtitleMenu.addView(tv);
    }

    private void addColorCycleItem() {
        final TextView tv = makeMenuTextView("颜色: " + (prefs.getSubtitleColor() == 1 ? "黄" : "白"));
        tv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int c = 1 - prefs.getSubtitleColor();
                prefs.setSubtitleColor(c);
                applySubtitleStyle();
                tv.setText("颜色: " + (c == 1 ? "黄" : "白"));
            }
        });
        llSubtitleMenu.addView(tv);
    }

    private TextView makeMenuTextView(String label) {
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextColor(getResources().getColor(R.color.text_primary));
        tv.setTextSize(22);
        tv.setPadding(16, 16, 16, 16);
        tv.setFocusable(true);
        tv.setFocusableInTouchMode(true);
        tv.setBackgroundResource(R.drawable.bg_poster_card);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = 8;
        tv.setLayoutParams(lp);
        return tv;
    }

    private void applySubtitleStyle() {
        int size = prefs.getSubtitleSize();
        float sp = size == 0 ? 20f : size == 2 ? 34f : 26f;
        tvSubtitle.setTextSize(sp);
        tvSubtitle.setTextColor(prefs.getSubtitleColor() == 1 ? 0xFFFFEB3B : 0xFFFFFFFF);
    }

    private void addAudioItem(final StreamList.Stream a) {
        String lang = a.language != null && !a.language.isEmpty() ? a.language : "";
        String codec = a.codec_name != null ? a.codec_name.toUpperCase() : "";
        String ch = a.channels > 0 ? (" " + a.channels + "声道") : "";
        String label = (a.title != null && !a.title.isEmpty()) ? a.title : (lang + " " + codec + ch).trim();
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextColor(getResources().getColor(R.color.text_primary));
        tv.setTextSize(22);
        tv.setPadding(16, 16, 16, 16);
        tv.setFocusable(true);
        tv.setFocusableInTouchMode(true);
        tv.setBackgroundResource(R.drawable.bg_poster_card);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = 8;
        tv.setLayoutParams(lp);
        tv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideSubtitleMenu();
                switchAudio(a);
            }
        });
        llSubtitleMenu.addView(tv);
    }

    private void addHeader(String label) {
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextColor(getResources().getColor(R.color.text_secondary));
        tv.setTextSize(16);
        tv.setPadding(16, 12, 16, 4);
        tv.setFocusable(false);
        llSubtitleMenu.addView(tv);
    }

    private void addQualityItem(final Quality q) {
        String label = q.resolution + "p · " + (q.bitrate / 1000000.0) + "M";
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextColor(getResources().getColor(R.color.text_primary));
        tv.setTextSize(22);
        tv.setPadding(16, 16, 16, 16);
        tv.setFocusable(true);
        tv.setFocusableInTouchMode(true);
        tv.setBackgroundResource(R.drawable.bg_poster_card);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = 8;
        tv.setLayoutParams(lp);
        tv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideSubtitleMenu();
                switchQuality(q);
            }
        });
        llSubtitleMenu.addView(tv);
    }

    private void addSubtitleItem(final String label, final String subtitleGuid) {
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextColor(getResources().getColor(R.color.text_primary));
        tv.setTextSize(22);
        tv.setPadding(16, 16, 16, 16);
        tv.setFocusable(true);
        tv.setFocusableInTouchMode(true);
        tv.setBackgroundResource(R.drawable.bg_poster_card);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = 8;
        tv.setLayoutParams(lp);
        tv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideSubtitleMenu();
                switchSubtitle(subtitleGuid);
            }
        });
        llSubtitleMenu.addView(tv);
    }

    private void hideSubtitleMenu() {
        menuOpen = false;
        llSubtitleMenu.setVisibility(View.GONE);
    }

    private void switchSubtitle(final String subtitleGuid) {
        int pos = (player != null && prepared) ? player.getCurrentPosition() / 1000 : 0;
        currentSubtitleGuid = subtitleGuid;
        releasePlayer();
        tvSubtitle.setVisibility(View.GONE);
        loadPlay(subtitleGuid, currentAudioGuid, pos, currentResolution, currentBitrate);
    }

    private void switchQuality(final Quality q) {
        int pos = (player != null && prepared) ? player.getCurrentPosition() / 1000 : 0;
        currentResolution = q.resolution;
        currentBitrate = q.bitrate;
        releasePlayer();
        loadPlay(currentSubtitleGuid, currentAudioGuid, pos, currentResolution, currentBitrate);
    }

    private void switchAudio(final StreamList.Stream a) {
        int pos = (player != null && prepared) ? player.getCurrentPosition() / 1000 : 0;
        currentAudioGuid = a.guid;
        releasePlayer();
        loadPlay(currentSubtitleGuid, currentAudioGuid, pos, currentResolution, currentBitrate);
    }

    /** 播完启动下一集倒计时。 */
    private void startNextCountdown() {
        if (TextUtils.isEmpty(nextGuid)) {
            finish();
            return;
        }
        countdownSeconds = 5;
        handler.post(countdownRunnable);
    }

    private void cancelCountdown() {
        handler.removeCallbacks(countdownRunnable);
        tvCountdown.setVisibility(View.GONE);
        finish();
    }

    /** 播完自动连播下一集。 */
    private void playNextEpisode() {
        if (TextUtils.isEmpty(nextGuid)) {
            finish();
            return;
        }
        guid = nextGuid;
        nextGuid = "";
        if (!TextUtils.isEmpty(nextTitle)) {
            tvTitle.setText(nextTitle);
            nextTitle = "";
        }
        currentSubtitleGuid = null;
        currentAudioGuid = null;
        currentResolution = null;
        currentBitrate = -1;
        subtitleStreams = new ArrayList<>();
        qualityList = new ArrayList<>();
        audioStreams = new ArrayList<>();
        tvSubtitle.setVisibility(View.GONE);
        releasePlayer();
        loadSubtitleStreams();
        loadPlay(null, null, -1, null, -1);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        surfaceReady = true;
        if (pendingPrepare && player == null && playUrl != null) {
            pendingPrepare = false;
            prepare();
        } else if (player != null && prepared) {
            player.setDisplay(holder);
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        surfaceReady = false;
    }

    private void releasePlayer() {
        handler.removeCallbacks(progressUpdater);
        pendingPrepare = false;
        pendingSeekMs = -1;
        if (player != null) {
            try {
                player.release();
            } catch (Exception ignored) {
            }
            player = null;
        }
        prepared = false;
    }
    @Override
    protected void onPause() {
        super.onPause();
        reportProgress();
        releasePlayer();
        if (subtitleLoader != null) {
            subtitleLoader.close();
            subtitleLoader = null;
        }
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        super.onDestroy();
        reportProgress();
        releasePlayer();
        playerAlive = false;
        if (castMode && castSession != null) {
            castSession.removeListener(castListener);
            castSession.removeSeekListener(castSeekListener);
            castSession.stop();
            castSession = null;
        }
        if (subtitleLoader != null) {
            subtitleLoader.close();
            subtitleLoader = null;
        }
    }
}
