package com.feiniu.tv.util;

import android.app.ActivityManager;
import android.content.Context;

import androidx.annotation.NonNull;

import com.bumptech.glide.Glide;
import com.bumptech.glide.GlideBuilder;
import com.bumptech.glide.load.DecodeFormat;
import com.bumptech.glide.load.engine.cache.InternalCacheDiskCacheFactory;
import com.bumptech.glide.load.engine.cache.LruResourceCache;
import com.bumptech.glide.load.engine.bitmap_recycle.LruBitmapPool;
import com.bumptech.glide.module.AppGlideModule;
import com.bumptech.glide.request.RequestOptions;

/**
 * Glide 全局配置。电视内存吃紧(常见 128~192MB 堆),默认的 ARGB_8888 +
 * 大盘缓存会把内存和存储都用满,这里按机器能力收一下。
 */
@com.bumptech.glide.annotation.GlideModule
public final class FlyGlideModule extends AppGlideModule {

    private static final int DISK_CACHE_MB = 256;

    @Override
    public void applyOptions(@NonNull Context context, @NonNull GlideBuilder builder) {
        int memoryClassMb = 128;
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (am != null) {
            memoryClassMb = am.getMemoryClass();
        }
        // 内存缓存与复用池各取堆的 1/8,低内存机型再收一档
        int cacheMb = Math.max(12, memoryClassMb / 8);
        builder.setMemoryCache(new LruResourceCache(cacheMb * 1024L * 1024L));
        builder.setBitmapPool(new LruBitmapPool(cacheMb * 1024L * 1024L));
        builder.setDiskCache(new InternalCacheDiskCacheFactory(context, "img_cache",
                DISK_CACHE_MB * 1024L * 1024L));
        // 海报没有透明通道,565 能省一半解码内存
        builder.setDefaultRequestOptions(new RequestOptions()
                .format(DecodeFormat.PREFER_RGB_565));
    }

    @Override
    public boolean isManifestParsingEnabled() {
        return false;
    }
}
