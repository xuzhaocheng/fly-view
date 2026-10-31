package com.feiniu.tv.api;

import com.feiniu.tv.api.model.MediaDb;
import com.feiniu.tv.api.model.MediaItem;

import org.junit.Assume;
import org.junit.Test;

import java.util.List;

/**
 * 针对真实 NAS 的端到端冒烟测试:登录 → 媒体库 → 条目 → 播放地址 → 字幕。
 *
 * <p>地址和账号从环境变量 {@code FN_HOST} / {@code FN_USER} / {@code FN_PASS}
 * 读取,没配就整条跳过——内网地址和口令不进仓库。
 *
 * <pre>
 * FN_HOST=http://192.168.1.8:5666 FN_USER=me FN_PASS=*** gradle test
 * </pre>
 */
public class FnApiClientLiveTest {

    private static final String HOST = env("FN_HOST");
    private static final String USER = env("FN_USER");
    private static final String PASS = env("FN_PASS");

    private static String env(String name) {
        String v = System.getenv(name);
        if (v == null) {
            v = System.getProperty(name);
        }
        return v == null || v.trim().isEmpty() ? null : v.trim();
    }

    @Test
    public void fullFlow() throws Exception {
        Assume.assumeTrue("未配置 FN_HOST/FN_USER/FN_PASS,跳过真实 NAS 测试",
                HOST != null && USER != null && PASS != null);

        FnApiClient c = new FnApiClient(HOST);
        String token = c.login(USER, PASS, "trimemedia-web");
        System.out.println("token=" + token);
        c.setToken(token);

        List<MediaDb> libs = c.getMediaDbList();
        System.out.println("媒体库数=" + libs.size());
        for (MediaDb db : libs) {
            System.out.println("  库: " + db.title + " guid=" + db.guid + " category=" + db.category);
        }

        MediaDb first = libs.get(0);
        List<MediaItem> items = c.getItemList(first.guid, 10);
        System.out.println("第一个库条目数=" + items.size());
        if (!items.isEmpty()) {
            MediaItem it = items.get(0);
            System.out.println("第一个条目 title=" + it.title + " poster=" + it.posterGuid());
            System.out.println("图片URL=" + c.imageUrl(it.posterGuid(), 200));

            String link = c.getPlayLink(it.guid);
            System.out.println("PLAY_LINK=" + link);

            java.util.List<com.feiniu.tv.api.model.SubtitleCue> cues = c.downloadSubtitleCues(HOST + link);
            System.out.println("字幕条数=" + cues.size());
            for (int i = 0; i < Math.min(5, cues.size()); i++) {
                com.feiniu.tv.api.model.SubtitleCue cue = cues.get(i);
                System.out.println("  [" + cue.startMs + "-" + cue.endMs + "] " + cue.text);
            }
        }
    }
}
