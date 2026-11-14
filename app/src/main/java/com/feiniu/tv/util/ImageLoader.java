package com.feiniu.tv.util;

import android.content.Context;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.FutureTarget;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.GranularRoundedCorners;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.request.RequestOptions;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 海报图加载:带鉴权头(Authorization + Cookie)。 */
public final class ImageLoader {

    /** 图片只按三个档位取,避免同一张海报因为宽度不同在磁盘里存好几份。 */
    public static final int W_THUMB = 400;  // 海报卡片 / 网格 / 拼图
    public static final int W_WIDE = 720;   // 16:9 横幅(继续观看、媒体库)
    public static final int W_HERO = 1280;  // 详情页背景剧照

    /** 预取并发压到 2,老电视的网络线程池经不起几十个请求同时排队。 */
    private static final ExecutorService PREFETCH = Executors.newFixedThreadPool(2);

    private ImageLoader() {
    }

    private static GlideUrl buildGlideUrl(String url, String token) {
        LazyHeaders.Builder headers = new LazyHeaders.Builder();
        if (token != null && !token.isEmpty()) {
            headers.addHeader("Authorization", token);
            headers.addHeader("Cookie", "authorization=" + token);
        }
        return new GlideUrl(url, headers.build());
    }

    /**
     * 只把图落盘、不解码显示:给首页首屏做预取用。同一 URL 已在取的话 Glide 会合并。
     * 注意宽度要和列表里实际用的一致,否则会多存一份。
     */
    public static void prefetch(Context context, String url, String token, int width) {
        if (context == null || url == null || url.isEmpty()) {
            return;
        }
        final Context app = context.getApplicationContext();
        final GlideUrl glideUrl = buildGlideUrl(url, token);
        PREFETCH.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    FutureTarget<File> target = Glide.with(app).load(glideUrl)
                            .downloadOnly(width, width);
                    target.get();
                } catch (Exception ignored) {
                    // 预取失败无所谓,真正显示时会重新请求
                }
            }
        });
    }

    /** 图片磁盘缓存当前占用(字节)。 */
    public static long diskCacheSize(Context context) {
        if (context == null) {
            return 0;
        }
        return dirSize(new File(context.getCacheDir(), "img_cache"));
    }

    private static long dirSize(File dir) {
        if (dir == null || !dir.exists()) {
            return 0;
        }
        File[] children = dir.listFiles();
        if (children == null) {
            return 0;
        }
        long total = 0;
        for (File f : children) {
            total += f.isDirectory() ? dirSize(f) : f.length();
        }
        return total;
    }

    /** 清空图片缓存:内存缓存必须主线程清,磁盘缓存必须后台清。 */
    public static void clearCache(final Context context) {
        if (context == null) {
            return;
        }
        Glide.get(context).clearMemory();
        final Context app = context.getApplicationContext();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Glide.get(app).clearDiskCache();
                } catch (Exception ignored) {
                }
            }
        }, "img-cache-clear").start();
    }

    public static void load(Context context, ImageView view, String url, String token) {
        load(context, view, url, token, 0);
    }

    /**
     * 四角分别指定半径(拼图用):tl=左上 tr=右上 br=右下 bl=左下。
     * 2×2 拼图只圆外侧两个角,中缝保持直角。
     */
    public static void loadCorners(Context context, ImageView view, String url, String token,
                                   int tl, int tr, int br, int bl) {
        if (url == null || url.isEmpty()) {
            view.setImageDrawable(null);
            return;
        }
        LazyHeaders.Builder headers = new LazyHeaders.Builder();
        if (token != null && !token.isEmpty()) {
            headers.addHeader("Authorization", token);
            headers.addHeader("Cookie", "authorization=" + token);
        }
        GlideUrl glideUrl = new GlideUrl(url, headers.build());
        Glide.with(context).load(glideUrl)
                .apply(com.bumptech.glide.request.RequestOptions.bitmapTransform(
                        new com.bumptech.glide.load.MultiTransformation<>(
                                new CenterCrop(),
                                new GranularRoundedCorners(tl, tr, br, bl))))
                .into(view);
    }

    /** radiusPx > 0 时圆角裁剪(4.4 没有 outline clipping,只能在 bitmap 层做)。 */
    public static void load(Context context, ImageView view, String url, String token, int radiusPx) {
        if (url == null || url.isEmpty()) {
            view.setImageDrawable(null);
            return;
        }
        LazyHeaders.Builder headers = new LazyHeaders.Builder();
        if (token != null && !token.isEmpty()) {
            headers.addHeader("Authorization", token);
            headers.addHeader("Cookie", "authorization=" + token);
        }
        GlideUrl glideUrl = new GlideUrl(url, headers.build());
        if (radiusPx > 0) {
            Glide.with(context).load(glideUrl)
                    .apply(RequestOptions.bitmapTransform(
                            new com.bumptech.glide.load.MultiTransformation<>(
                                    new CenterCrop(), new RoundedCorners(radiusPx))))
                    .into(view);
        } else {
            Glide.with(context).load(glideUrl).into(view);
        }
    }
}
