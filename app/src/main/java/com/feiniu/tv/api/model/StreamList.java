package com.feiniu.tv.api.model;

import java.util.List;

/** 流列表(stream/list 返回)。 */
public class StreamList {
    public List<FileItem> files;
    public List<Stream> video_streams;
    public List<Stream> audio_streams;
    public List<SubtitleStream> subtitle_streams;

    public static class FileItem {
        public String path;
        public String guid;
        public long size;
    }

    public static class Stream {
        public String guid;
        public String codec_name;
        public long bps;
        public String media_guid;
        public int channels;
        public String resolution_type;
        public int width;
        public int height;
        public String title;
        public String language;
    }

    public static class SubtitleStream {
        public String guid;
        public String title;
        public String language;
        public String codec_name;
        public String format;
        public int index;
        public int is_default; // 接口返回 0/1,不是 boolean
        public int is_external;
    }
}
