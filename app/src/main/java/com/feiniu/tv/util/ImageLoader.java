package com.feiniu.tv.util;

import android.content.Context;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.GranularRoundedCorners;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.request.RequestOptions;

/** 海报图加载:带鉴权头(Authorization + Cookie)。 */
public final class ImageLoader {

    /** 图片只按三个档位取,避免同一张海报因为宽度不同在磁盘里存好几份。 */
    public static final int W_THUMB = 400;  // 海报卡片 / 网格 / 拼图
    public static final int W_WIDE = 720;   // 16:9 横幅(继续观看、媒体库)
    public static final int W_HERO = 1280;  // 详情页背景剧照

    private ImageLoader() {
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
