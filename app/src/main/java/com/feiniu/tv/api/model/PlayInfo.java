package com.feiniu.tv.api.model;

import com.google.gson.JsonElement;

/** 播放信息(play/info 返回)。 */
public class PlayInfo {
    public String type;
    public String guid;        // 顶层 guid = 可播放条目的 guid(剧集传剧集 guid 时返回其第一集/上次看的集)
    public String parent_guid; // 顶层 parent_guid = 父级(季)guid
    public long watched_ts;
    public JsonElement play_config; // 含 skip_opening / skip_ending(可能为 null)
    public PlayItem item;

    public static class PlayItem {
        public String guid;
        public String parent_guid;
        public int season_number;
        public int episode_number;
        public String title;
        public String type;
        public long watched_ts;
        // 播放流相关字段(具体字段名以实际接口为准,Task 2 核实)
        public String url;
        public String video_url;
        public String stream_url;
        public String channels;
    }
}
