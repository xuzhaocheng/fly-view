package com.feiniu.tv.api.model;

/** 海报墙条目(item/list 返回项)。 */
public class MediaItem {
    public String guid;
    public String title;
    public String tv_title;
    public String name;
    public String poster;    // 海报图 guid(item/list 返回的是 poster,单数)
    public String posters;   // 部分接口返回 posters(复数),兼容保留
    public String backdrops; // 背景图 guid
    public String type;      // Movie / TV / Directory / Video
    public String gallery_type;
    public double vote_average;
    public String release_date;
    public String overview;
    public int watched;
    public long watched_ts;  // 上次观看位置(秒),>0 表示看过
    // /api/v1/play/list(继续观看)专属字段
    public long ts;          // 播放位置(秒)
    public long duration;    // 总时长(秒)
    public String parent_title; // 剧集名(Episode 类型时)
    public int season_number;
    public int episode_number;

    /** 取海报 guid,优先 poster。 */
    public String posterGuid() {
        if (poster != null && !poster.isEmpty()) {
            return poster;
        }
        return posters;
    }
}
