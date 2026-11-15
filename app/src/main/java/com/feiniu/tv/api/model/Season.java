package com.feiniu.tv.api.model;

/** 季(season/list 返回项)。部分剧集的 title 为空,要用 season_number 兜底。 */
public class Season {
    public String guid;
    public String title;
    public String poster;
    public int season_number;

    /** 季标签文案:接口没给标题时用季号补,0 视为特别篇。 */
    public String displayTitle() {
        String t = title == null ? "" : title.trim();
        if (!t.isEmpty()) {
            return t;
        }
        return season_number == 0 ? "特别篇" : "第 " + season_number + " 季";
    }
}
