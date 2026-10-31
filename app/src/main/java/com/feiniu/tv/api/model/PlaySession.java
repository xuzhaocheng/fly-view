package com.feiniu.tv.api.model;

/** 一次播放会话:播放流地址 + 上报进度所需的流信息。 */
public class PlaySession {
    public String playLink;   // 相对路径,如 /v/media/xxx/preset.m3u8
    public String mediaGuid;
    public String videoGuid;
    public String audioGuid;
    public String resolution;
    public long bitrate;

    // 片头片尾跳过区间(秒),-1 表示无
    public double skipOpeningStart = -1;
    public double skipOpeningEnd = -1;
    public double skipEndingStart = -1;
    public double skipEndingEnd = -1;
}
