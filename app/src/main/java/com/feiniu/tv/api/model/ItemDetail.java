package com.feiniu.tv.api.model;

import java.util.List;

/** 条目详情(item/{guid} 返回)。 */
public class ItemDetail {
    public String guid;
    public String title;
    public String tv_title;
    public String name;
    public String original_title;
    public String overview;
    public String posters;
    public String backdrops;
    public String type;
    public double vote_average;
    public String release_date;
    public int runtime;              // 分钟,剧集为 0
    public String content_ratings;   // 分级,如 R / PG-13,可能为 null
    public List<String> production_countries;
    public int is_favorite;
    public int is_watched;
    public long watched_ts;          // 电影:上次看到第几秒
    public int number_of_seasons;
    public int number_of_episodes;
    public int can_play;
}
