package com.feiniu.tv.api;

import com.feiniu.tv.api.model.SubtitleCue;

import android.util.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 惰性字幕加载器:飞牛的字幕是 HLS WebVTT 分片(边转码边生成),
 * 必须按播放进度分片拉取,不能一次性全下(否则拿到空分片)。
 */
public class SubtitleLoader {

    private static final int PREFETCH_AHEAD = 40; // 提前 40 个分片(约 160 秒)

    private final OkHttpClient http;
    private final String base;
    private final List<String> names = new ArrayList<>();
    private final long[] offsets;
    private final Map<Integer, List<SubtitleCue>> cache = new ConcurrentHashMap<>();
    private final ExecutorService pool = Executors.newFixedThreadPool(6);
    private volatile int prefetchedUpTo = -1;

    public SubtitleLoader(String fullPlayUrl) throws IOException {
        this.http = new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build();
        this.base = fullPlayUrl.substring(0, fullPlayUrl.lastIndexOf('/') + 1);

        String m3u8 = httpGet(base + "subtitle.m3u8");
        List<Double> durations = new ArrayList<>();
        if (m3u8 != null) {
            for (String line : m3u8.split("\n")) {
                String t = line.trim();
                if (t.startsWith("#EXTINF:")) {
                    try {
                        durations.add(Double.parseDouble(t.substring(8).replace(",", "").trim()));
                    } catch (NumberFormatException ignored) {
                        durations.add(4.0);
                    }
                } else if (!t.startsWith("#") && t.endsWith(".vtt")) {
                    names.add(t);
                }
            }
        }
        offsets = new long[names.size()];
        double acc = 0;
        for (int i = 0; i < names.size(); i++) {
            offsets[i] = (long) (acc * 1000);
            if (i < durations.size()) {
                acc += durations.get(i);
            } else {
                acc += 4;
            }
        }
    }

    public boolean isEmpty() {
        return names.isEmpty();
    }

    /** 播放位置变化时调用:按需预取当前及后续分片。 */
    public void onPositionChanged(long posMs) {
        int seg = findSegment(posMs);
        if (seg < 0) {
            return;
        }
        int target = Math.min(names.size() - 1, seg + PREFETCH_AHEAD);
        while (prefetchedUpTo < target) {
            final int idx = prefetchedUpTo + 1;
            prefetchedUpTo = idx;
            pool.submit(new Runnable() {
                @Override
                public void run() {
                    if (cache.containsKey(idx)) {
                        return;
                    }
                    List<SubtitleCue> parsed = null;
                    // 分片可能边转码边生成,空内容就重试几次(对齐官方网页版 fetchWithRetry)
                    for (int attempt = 0; attempt < 3 && parsed == null; attempt++) {
                        try {
                            String content = httpGet(base + names.get(idx));
                            if (content != null && content.contains("-->")) {
                                parsed = parseVtt(content, offsets[idx]);
                            } else if (attempt < 2) {
                                try {
                                    Thread.sleep(800);
                                } catch (InterruptedException ignored) {
                                }
                            } else {
                                parsed = new ArrayList<SubtitleCue>();
                            }
                        } catch (IOException e) {
                            if (attempt < 2) {
                                try {
                                    Thread.sleep(800);
                                } catch (InterruptedException ignored) {
                                }
                            } else {
                                parsed = new ArrayList<SubtitleCue>();
                            }
                        }
                    }
                    if (parsed == null) {
                        parsed = new ArrayList<SubtitleCue>();
                    }
                    if (!parsed.isEmpty()) {
                        Log.d("FeiniuTV", "字幕分片 " + idx + " 解析到 " + parsed.size() + " 条");
                    }
                    cache.put(idx, parsed);
                }
            });
        }
    }

    /** 返回当前时间点命中的字幕条目,无则 null。 */
    public SubtitleCue getCue(long posMs) {
        int seg = findSegment(posMs);
        if (seg < 0) {
            return null;
        }
        List<SubtitleCue> cues = cache.get(seg);
        if (cues == null || cues.isEmpty()) {
            return null;
        }
        for (SubtitleCue c : cues) {
            if (posMs >= c.startMs && posMs <= c.endMs) {
                return c;
            }
        }
        return null;
    }

    public void close() {
        pool.shutdownNow();
    }

    private int findSegment(long posMs) {
        if (names.isEmpty() || posMs < 0) {
            return -1;
        }
        int lo = 0;
        int hi = names.size() - 1;
        int ans = 0;
        while (lo <= hi) {
            int mid = (lo + hi) / 2;
            if (offsets[mid] <= posMs) {
                ans = mid;
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        return ans;
    }

    private String httpGet(String url) throws IOException {
        Request req = new Request.Builder().url(url).get().build();
        try (Response resp = http.newCall(req).execute()) {
            if (resp.isSuccessful()) {
                return resp.body().string();
            }
            return "";
        }
    }

    private static List<SubtitleCue> parseVtt(String vtt, long offsetMs) {
        List<SubtitleCue> cues = new ArrayList<>();
        if (vtt == null) {
            return cues;
        }
        String[] lines = vtt.split("\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            int arrow = line.indexOf("-->");
            if (arrow > 0) {
                long start = parseVttTime(line.substring(0, arrow).trim());
                long end = parseVttTime(line.substring(arrow + 3).trim());
                StringBuilder text = new StringBuilder();
                int j = i + 1;
                while (j < lines.length && !lines[j].trim().isEmpty() && lines[j].indexOf("-->") < 0) {
                    if (text.length() > 0) {
                        text.append('\n');
                    }
                    text.append(lines[j].trim());
                    j++;
                }
                cues.add(new SubtitleCue(offsetMs + start, offsetMs + end, text.toString()));
                i = j - 1;
            }
        }
        return cues;
    }

    private static long parseVttTime(String s) {
        s = s.split(" ")[0].trim();
        String[] parts = s.split(":");
        if (parts.length == 3) {
            return Long.parseLong(parts[0]) * 3600000L
                    + Long.parseLong(parts[1]) * 60000L
                    + (long) (Double.parseDouble(parts[2]) * 1000);
        } else if (parts.length == 2) {
            return Long.parseLong(parts[0]) * 60000L
                    + (long) (Double.parseDouble(parts[1]) * 1000);
        }
        return 0;
    }
}
