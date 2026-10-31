package com.feiniu.tv.api.model;

/** 一条字幕(解析后的 WebVTT cue)。 */
public class SubtitleCue {
    public long startMs;
    public long endMs;
    public String text;

    public SubtitleCue(long startMs, long endMs, String text) {
        this.startMs = startMs;
        this.endMs = endMs;
        this.text = text;
    }
}
