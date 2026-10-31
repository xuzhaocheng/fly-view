package com.feiniu.tv.api;

import com.feiniu.tv.api.model.Episode;
import com.feiniu.tv.api.model.ItemDetail;
import com.feiniu.tv.api.model.MediaDb;
import com.feiniu.tv.api.model.MediaItem;
import com.feiniu.tv.api.model.PlayInfo;
import com.feiniu.tv.api.model.PlaySession;
import com.feiniu.tv.api.model.Quality;
import com.feiniu.tv.api.model.Season;
import com.feiniu.tv.api.model.StreamList;
import com.feiniu.tv.api.model.SubtitleCue;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * 飞牛影视官方 API 客户端。同步阻塞,请在后台线程调用。
 */
public class FnApiClient {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final TypeToken<List<MediaDb>> TYPE_MEDIA_DB_LIST = new TypeToken<List<MediaDb>>() {};
    private static final TypeToken<List<MediaItem>> TYPE_MEDIA_ITEM_LIST = new TypeToken<List<MediaItem>>() {};
    private static final TypeToken<List<Season>> TYPE_SEASON_LIST = new TypeToken<List<Season>>() {};
    private static final TypeToken<List<Episode>> TYPE_EPISODE_LIST = new TypeToken<List<Episode>>() {};
    private static final TypeToken<List<Quality>> TYPE_QUALITY_LIST = new TypeToken<List<Quality>>() {};

    private final OkHttpClient http;
    private final String baseUrl; // 形如 http://192.168.1.8:5666/v
    private String token;

    public FnApiClient(String host) {
        if (host.endsWith("/")) {
            host = host.substring(0, host.length() - 1);
        }
        this.baseUrl = host + "/v";
        this.http = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getToken() {
        return token;
    }

    /** 登录,返回 token。 */
    public String login(String username, String password, String appName) throws IOException, ApiException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", username);
        body.put("password", password);
        body.put("app_name", appName);
        JsonElement data = request("POST", "/api/v1/login", null, body);
        if (data != null && data.isJsonObject()) {
            JsonObject o = data.getAsJsonObject();
            if (o.has("token")) {
                return o.get("token").getAsString();
            }
            if (o.has("authorization")) {
                return o.get("authorization").getAsString();
            }
        }
        throw new ApiException(-1, "登录响应缺少 token");
    }

    public List<MediaDb> getMediaDbList() throws IOException, ApiException {
        JsonElement data = request("GET", "/api/v1/mediadb/list", null, null);
        return parseList(data, TYPE_MEDIA_DB_LIST);
    }

    /** 网页端"继续观看"的数据源:播放记录列表,服务端已按最近观看排序。 */
    public List<MediaItem> getPlayList() throws IOException, ApiException {
        JsonElement data = request("GET", "/api/v1/play/list", null, null);
        List<MediaItem> list = parseList(data, TYPE_MEDIA_ITEM_LIST);
        for (MediaItem it : list) {
            // 继续观看的续播位置在 ts 字段;统一映射到 watched_ts,
            // 让海报墙/过滤逻辑不用感知两套字段。
            if (it.watched_ts == 0 && it.ts > 0) {
                it.watched_ts = it.ts;
            }
        }
        return list;
    }

    public List<MediaItem> getItemList(String ancestorGuid, int pageSize) throws IOException, ApiException {
        return getItemList(ancestorGuid, pageSize, "DESC", "create_time");
    }

    public List<MediaItem> getItemList(String ancestorGuid, int pageSize,
                                       String sortType, String sortColumn) throws IOException, ApiException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ancestor_guid", ancestorGuid);
        Map<String, Object> tags = new LinkedHashMap<>();
        tags.put("type", Arrays.asList("Movie", "TV", "Directory", "Video"));
        body.put("tags", tags);
        body.put("exclude_grouped_video", 1);
        body.put("sort_type", sortType == null ? "DESC" : sortType);
        body.put("sort_column", sortColumn == null ? "create_time" : sortColumn);
        body.put("page_size", pageSize);
        JsonElement data = request("POST", "/api/v1/item/list", null, body);
        return parseList(data, TYPE_MEDIA_ITEM_LIST);
    }

    public ItemDetail getItemDetail(String guid) throws IOException, ApiException {
        JsonElement data = request("GET", "/api/v1/item/" + guid, null, null);
        return GSON.fromJson(data, ItemDetail.class);
    }

    public PlayInfo getPlayInfo(String itemGuid) throws IOException, ApiException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("item_guid", itemGuid);
        JsonElement data = request("POST", "/api/v1/play/info", null, body);
        return GSON.fromJson(data, PlayInfo.class);
    }

    public List<Season> getSeasonList(String guid) throws IOException, ApiException {
        JsonElement data = request("GET", "/api/v1/season/list/" + guid, null, null);
        return parseList(data, TYPE_SEASON_LIST);
    }

    public List<Episode> getEpisodeList(String guid) throws IOException, ApiException {
        JsonElement data = request("GET", "/api/v1/episode/list/" + guid, null, null);
        return parseList(data, TYPE_EPISODE_LIST);
    }

    public StreamList getStreamList(String guid) throws IOException, ApiException {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("before_play", "1");
        JsonElement data = request("GET", "/api/v1/stream/list/" + guid, params, null);
        return GSON.fromJson(data, StreamList.class);
    }

    public List<Quality> getQuality(String mediaGuid) throws IOException, ApiException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("media_guid", mediaGuid);
        JsonElement data = request("POST", "/api/v1/play/quality", null, body);
        return parseList(data, TYPE_QUALITY_LIST);
    }

    /** 核心:拿播放流 URL(play_link)。 */
    public String playPlay(Map<String, Object> body) throws IOException, ApiException {
        JsonElement data = request("POST", "/api/v1/play/play", null, body);
        if (data != null && data.isJsonObject()) {
            JsonObject o = data.getAsJsonObject();
            if (o.has("play_link") && !o.get("play_link").isJsonNull()) {
                return o.get("play_link").getAsString();
            }
        }
        throw new ApiException(-1, "播放响应缺少 play_link");
    }

    /**
     * 完整播放流获取流程:play/info → stream/list → play/quality → play/play。
     * 返回可直接播放的完整 URL(m3u8 或直链)。
     */
    public String getPlayLink(String itemGuid) throws IOException, ApiException {
        return getPlayLink(itemGuid, null, -1);
    }

    /**
     * 获取播放流 URL,可指定字幕轨与起播位置。
     *
     * @param itemGuid     条目 guid
     * @param subtitleGuid 字幕 guid;null=自动选默认字幕,空串=无字幕
     * @param startTs      起播时间戳(秒);<0 表示用上次观看进度
     */
    public String getPlayLink(String itemGuid, String subtitleGuid, long startTs) throws IOException, ApiException {
        return getPlayLink(itemGuid, subtitleGuid, startTs, null, -1).playLink;
    }

    /**
     * 获取播放会话,可指定字幕轨、起播位置、分辨率与码率。
     *
     * @param resolution 分辨率(null=自动选稳妥画质,如 "1080"/"720"/"480")
     * @param bitrate    码率 bps(<=0 表示跟随 resolution 对应的码率)
     */
    public PlaySession getPlayLink(String itemGuid, String subtitleGuid, long startTs,
                                   String resolution, long bitrate) throws IOException, ApiException {
        return getPlayLink(itemGuid, subtitleGuid, null, startTs, resolution, bitrate);
    }

    /**
     * 获取播放会话,可指定音轨、字幕轨、起播位置、分辨率与码率。
     *
     * @param audioGuid 音轨 guid(null/空=第一条)
     */
    public PlaySession getPlayLink(String itemGuid, String subtitleGuid, String audioGuid, long startTs,
                                   String resolution, long bitrate) throws IOException, ApiException {
        PlayInfo info = getPlayInfo(itemGuid);

        String guid = resolvePlayGuid(itemGuid, info);

        StreamList streams = getStreamList(guid);
        if (streams == null || streams.video_streams == null || streams.video_streams.isEmpty()) {
            throw new ApiException(-1, "没有可用的视频流");
        }
        StreamList.Stream video = streams.video_streams.get(0);
        StreamList.Stream audio = pickAudio(streams.audio_streams, audioGuid);

        String mediaGuid = video.media_guid;
        if (mediaGuid == null && streams.files != null && !streams.files.isEmpty()) {
            mediaGuid = streams.files.get(0).guid;
        }

        String res = resolution;
        long bps = bitrate;
        List<Quality> qualities = getQuality(mediaGuid);
        if (res == null && qualities != null && !qualities.isEmpty()) {
            Quality q = pickDefaultQuality(qualities);
            res = q.resolution;
            bps = q.bitrate;
        }
        if (res == null) {
            res = "1080";
        }
        if (bps <= 0) {
            bps = video.bps;
        }

        long ts = startTs;
        if (ts < 0) {
            ts = 0;
            if (info != null && info.item != null) {
                ts = info.item.watched_ts;
            }
        }

        String sub = subtitleGuid;
        if (sub == null) {
            sub = pickDefaultSubtitle(streams);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("media_guid", mediaGuid);
        body.put("video_guid", video.guid);
        body.put("video_encoder", video.codec_name != null ? video.codec_name : "h264");
        body.put("resolution", res);
        body.put("bitrate", bps);
        body.put("startTimestamp", ts);
        body.put("audio_encoder", "aac");
        body.put("audio_guid", audio != null ? audio.guid : "");
        body.put("subtitle_guid", sub);
        body.put("channels", audio != null ? audio.channels : 2);

        String playLink = playPlay(body);

        PlaySession session = new PlaySession();
        session.playLink = playLink;
        session.mediaGuid = mediaGuid;
        session.videoGuid = video.guid;
        session.audioGuid = audio != null ? audio.guid : "";
        session.resolution = res;
        session.bitrate = bps;
        applySkipConfig(session, info);
        return session;
    }

    /** 从 play_config 解析片头片尾跳过区间(有则填,无则保持 -1)。 */
    private static void applySkipConfig(PlaySession session, PlayInfo info) {
        if (info == null || info.play_config == null || !info.play_config.isJsonObject()) {
            return;
        }
        JsonObject cfg = info.play_config.getAsJsonObject();
        double[] op = parseSkipRange(cfg.get("skip_opening"));
        if (op != null) {
            session.skipOpeningStart = op[0];
            session.skipOpeningEnd = op[1];
        }
        double[] ed = parseSkipRange(cfg.get("skip_ending"));
        if (ed != null) {
            session.skipEndingStart = ed[0];
            session.skipEndingEnd = ed[1];
        }
    }

    private static double[] parseSkipRange(JsonElement e) {
        if (e == null || e.isJsonNull()) {
            return null;
        }
        try {
            if (e.isJsonArray() && e.getAsJsonArray().size() >= 2) {
                return new double[]{e.getAsJsonArray().get(0).getAsDouble(),
                        e.getAsJsonArray().get(1).getAsDouble()};
            }
            if (e.isJsonObject()) {
                JsonObject o = e.getAsJsonObject();
                Double s = firstDouble(o, "start", "startTime", "start_time", "begin", "from");
                Double en = firstDouble(o, "end", "endTime", "end_time", "stop", "to");
                if (s != null && en != null && en > s) {
                    return new double[]{s, en};
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static Double firstDouble(JsonObject o, String... keys) {
        for (String k : keys) {
            if (o.has(k) && !o.get(k).isJsonNull()) {
                try {
                    return o.get(k).getAsDouble();
                } catch (Exception ignored) {
                }
            }
        }
        return null;
    }

    /** 上报播放进度(每约 10 秒调用一次),供飞牛记录"看到哪一集哪一秒"。 */
    public void reportPlayProgress(String itemGuid, PlaySession session, long tsSeconds,
                                   long durationSeconds, String subtitleGuid) throws IOException, ApiException {
        if (session == null) {
            return;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("item_guid", itemGuid);
        body.put("media_guid", session.mediaGuid);
        body.put("video_guid", session.videoGuid);
        body.put("audio_guid", session.audioGuid);
        body.put("subtitle_guid", subtitleGuid == null ? "" : subtitleGuid);
        body.put("resolution", session.resolution);
        body.put("bitrate", session.bitrate);
        body.put("ts", tsSeconds);
        body.put("duration", durationSeconds);
        body.put("play_link", session.playLink);
        body.put("create_time", System.currentTimeMillis() / 1000);
        body.put("playback_speed", 1);
        body.put("guid", itemGuid);
        request("POST", "/api/v1/play/record", null, body);
    }

    /** 选一个稳妥的默认画质:优先 720p,否则最低码率。 */
    private static Quality pickDefaultQuality(List<Quality> qualities) {
        Quality lowest = qualities.get(0);
        for (Quality q : qualities) {
            if ("720".equals(q.resolution)) {
                return q;
            }
            if (q.bitrate < lowest.bitrate) {
                lowest = q;
            }
        }
        return lowest;
    }

    /** 获取某条目的完整流信息(视频/音频/字幕/文件)。 */
    public StreamList getStreamListForItem(String itemGuid) throws IOException, ApiException {
        PlayInfo info = getPlayInfo(itemGuid);
        String guid = resolvePlayGuid(itemGuid, info);
        return getStreamList(guid);
    }

    /** 获取某条目的可用画质列表(分辨率+码率)。 */
    public List<Quality> getQualityList(String itemGuid) throws IOException, ApiException {
        PlayInfo info = getPlayInfo(itemGuid);
        String guid = resolvePlayGuid(itemGuid, info);
        StreamList streams = getStreamList(guid);
        if (streams == null || streams.video_streams == null || streams.video_streams.isEmpty()) {
            return new ArrayList<>();
        }
        StreamList.Stream video = streams.video_streams.get(0);
        String mediaGuid = video.media_guid;
        if (mediaGuid == null && streams.files != null && !streams.files.isEmpty()) {
            mediaGuid = streams.files.get(0).guid;
        }
        return getQuality(mediaGuid);
    }

    /** 解析可播放条目的 guid:play/info 顶层 guid 才是真正可播放的(剧集会解析到具体集)。 */
    private static String resolvePlayGuid(String itemGuid, PlayInfo info) {
        if (info != null && info.guid != null && !info.guid.isEmpty()) {
            return info.guid;
        }
        return itemGuid;
    }

    private static String pickDefaultSubtitle(StreamList streams) {
        if (streams.subtitle_streams != null && !streams.subtitle_streams.isEmpty()) {
            for (StreamList.SubtitleStream s : streams.subtitle_streams) {
                if (s.is_default == 1) {
                    return s.guid;
                }
            }
            return streams.subtitle_streams.get(0).guid;
        }
        return "";
    }

    private static StreamList.Stream pickAudio(List<StreamList.Stream> audios, String audioGuid) {
        if (audios == null || audios.isEmpty()) {
            return null;
        }
        if (audioGuid != null && !audioGuid.isEmpty()) {
            for (StreamList.Stream a : audios) {
                if (audioGuid.equals(a.guid)) {
                    return a;
                }
            }
        }
        return audios.get(0);
    }

    /** 获取某条目的音轨列表。 */
    public List<StreamList.Stream> getAudioStreams(String itemGuid) throws IOException, ApiException {
        StreamList streams = getStreamListForItem(itemGuid);
        if (streams != null && streams.audio_streams != null) {
            return streams.audio_streams;
        }
        return new ArrayList<>();
    }

    /** 获取某条目的字幕轨列表。 */
    public List<StreamList.SubtitleStream> getSubtitleStreams(String itemGuid) throws IOException, ApiException {
        PlayInfo info = getPlayInfo(itemGuid);
        String guid = resolvePlayGuid(itemGuid, info);
        StreamList streams = getStreamList(guid);
        if (streams != null && streams.subtitle_streams != null) {
            return streams.subtitle_streams;
        }
        return new ArrayList<>();
    }

    /** 海报图 URL(带宽度)。poster 字段已是完整路径(如 /2c/16/xxx.webp)。 */
    public String imageUrl(String posterGuid, int width) {
        if (posterGuid == null || posterGuid.isEmpty()) {
            return null;
        }
        if (posterGuid.startsWith("/")) {
            return baseUrl + "/api/v1/sys/img" + posterGuid + "?w=" + width;
        }
        return baseUrl + "/api/v1/sys/img/" + posterGuid + "?w=" + width;
    }

    /**
     * 下载并解析播放流的字幕(WebVTT 分片)。playLink 为 play/play 返回的 m3u8 地址。
     * 返回按时间升序的字幕条目列表。
     */
    public List<SubtitleCue> downloadSubtitleCues(String playLink) throws IOException {
        String base = playLink.substring(0, playLink.lastIndexOf('/') + 1);
        String m3u8 = httpGet(base + "subtitle.m3u8");
        if (m3u8 == null || m3u8.isEmpty()) {
            return new ArrayList<>();
        }

        List<String> names = new ArrayList<>();
        List<Double> durations = new ArrayList<>();
        for (String line : m3u8.split("\n")) {
            String t = line.trim();
            if (t.startsWith("#EXTINF:")) {
                String v = t.substring(8).replace(",", "").trim();
                try {
                    durations.add(Double.parseDouble(v));
                } catch (NumberFormatException ignored) {
                    durations.add(4.0);
                }
            } else if (!t.startsWith("#") && t.endsWith(".vtt")) {
                names.add(t);
            }
        }
        if (names.isEmpty()) {
            return new ArrayList<>();
        }

        final String[] contents = new String[names.size()];
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(16);
        java.util.List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) {
            final int idx = i;
            final String url = base + names.get(i);
            futures.add(pool.submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        contents[idx] = httpGet(url);
                    } catch (IOException e) {
                        contents[idx] = null;
                    }
                }
            }));
        }
        for (java.util.concurrent.Future<?> f : futures) {
            try {
                f.get();
            } catch (Exception ignored) {
            }
        }
        pool.shutdown();

        List<SubtitleCue> cues = new ArrayList<>();
        double offset = 0;
        for (int i = 0; i < names.size(); i++) {
            if (contents[i] != null) {
                cues.addAll(parseVtt(contents[i], (long) (offset * 1000)));
            }
            if (i < durations.size()) {
                offset += durations.get(i);
            } else {
                offset += 4;
            }
        }
        return cues;
    }

    private String httpGet(String url) throws IOException {
        Request req = new Request.Builder().url(url).get().build();
        try (Response resp = http.newCall(req).execute()) {
            if (resp.isSuccessful()) {
                return resp.body().string();
            }
            return null;
        }
    }

    /** 解析单个 WebVTT 分片,cue 时间相对该分片,需加 offsetMs。 */
    private static List<SubtitleCue> parseVtt(String vtt, long offsetMs) {
        List<SubtitleCue> cues = new ArrayList<>();
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

    private <T> List<T> parseList(JsonElement data, TypeToken<List<T>> type) {
        if (data == null) {
            return new ArrayList<>();
        }
        if (data.isJsonArray()) {
            return GSON.fromJson(data, type.getType());
        }
        if (data.isJsonObject() && data.getAsJsonObject().has("list")) {
            return GSON.fromJson(data.getAsJsonObject().get("list"), type.getType());
        }
        return new ArrayList<>();
    }

    private JsonElement request(String method, String path, Map<String, String> params,
                                Map<String, Object> jsonBody) throws IOException, ApiException {
        String signPath = "/v" + path;
        String bodyJson = (jsonBody != null) ? GSON.toJson(jsonBody) : null;
        String authx = FnSigner.sign(method, signPath, bodyJson, params);

        HttpUrl.Builder urlBuilder = HttpUrl.parse(baseUrl + path).newBuilder();
        if ("GET".equals(method) && params != null) {
            for (Map.Entry<String, String> e : params.entrySet()) {
                urlBuilder.addQueryParameter(e.getKey(), e.getValue());
            }
        }
        HttpUrl url = urlBuilder.build();
        Request.Builder rb = new Request.Builder().url(url);
        if (token != null) {
            rb.header("Authorization", token);
        }
        rb.header("authx", authx);

        if ("GET".equals(method)) {
            rb.get();
        } else {
            rb.method(method, RequestBody.create(JSON, bodyJson == null ? "" : bodyJson));
        }

        try (Response resp = http.newCall(rb.build()).execute()) {
            String s = resp.body().string();
            JsonObject obj = new JsonParser().parse(s).getAsJsonObject();
            int code = obj.has("code") ? obj.get("code").getAsInt() : -1;
            if (code != 0) {
                String msg = obj.has("msg") && !obj.get("msg").isJsonNull()
                        ? obj.get("msg").getAsString() : ("错误码 " + code);
                throw new ApiException(code, msg);
            }
            return obj.has("data") ? obj.get("data") : null;
        }
    }
}
