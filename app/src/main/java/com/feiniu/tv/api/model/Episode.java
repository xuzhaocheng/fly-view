package com.feiniu.tv.api.model;

/** 剧集(episode/list 返回项)。 */
public class Episode {
    public String guid;
    public String title;
    public int episode_number;
    public int season_number;
    public String overview;
    public String still_path;
    public String poster;       // 部分接口用 poster 字段放 16:9 剧照
    public int runtime;         // 分钟
    public long duration;       // 秒
    public int watched;
    public long watched_ts;     // 上次看到第几秒
    public String air_date;
    public String vote_average;

    /** 观看进度 0..1,用于集卡片底部那条进度线。 */
    public float watchedRatio() {
        long total = duration > 0 ? duration : runtime * 60L;
        if (total <= 0 || watched_ts <= 0) {
            return 0f;
        }
        float r = watched_ts / (float) total;
        return r > 1f ? 1f : r;
    }
}
