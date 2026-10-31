package com.feiniu.tv.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.feiniu.tv.R;
import com.feiniu.tv.api.model.MediaItem;
import com.feiniu.tv.util.ImageLoader;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 「继续观看」横向卡片。
 *
 * <p>飞牛的海报实际是 16:9 横图(1920x1080 / 3840x2160),所以这里按横版卡片展示,
 * 比竖版网格里塞横图更合理;卡片底部同时给出季集、剩余时长和进度条。
 */
public class ContinueAdapter extends RecyclerView.Adapter<ContinueAdapter.VH> {

    public interface OnContinueClick {
        void onClick(MediaItem item);
    }

    private final List<MediaItem> data = new ArrayList<>();
    private final OnContinueClick listener;
    private final ImageUrlBuilder imageUrlBuilder;
    private final String token;

    public interface ImageUrlBuilder {
        String build(MediaItem item, int width);
    }

    public ContinueAdapter(OnContinueClick listener, ImageUrlBuilder builder, String token) {
        this.listener = listener;
        this.imageUrlBuilder = builder;
        this.token = token;
    }

    public void submit(List<MediaItem> items) {
        data.clear();
        if (items != null) {
            data.addAll(items);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_continue, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        final MediaItem item = data.get(position);

        String title = item.tv_title != null && !item.tv_title.isEmpty()
                ? item.tv_title : item.title;
        holder.tvTitle.setText(title == null ? "" : title);
        holder.tvMeta.setText(meta(item));

        long duration = item.duration > 0 ? item.duration : 0;
        long ts = item.ts > 0 ? item.ts : item.watched_ts;
        int progress = 0;
        if (duration > 0 && ts > 0) {
            progress = (int) Math.min(1000, ts * 1000 / duration);
            // 刚看了几秒的条目按真实比例几乎为 0,给个 2% 的下限让它可见。
            progress = Math.max(progress, 20);
        }
        holder.progress.setProgress(progress);

        if (imageUrlBuilder != null && holder.img != null) {
            int radius = (int) (8 * holder.itemView.getResources().getDisplayMetrics().density);
            ImageLoader.load(holder.itemView.getContext(), holder.img,
                    imageUrlBuilder.build(item, 960), token, radius);
        }

        holder.frame.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) {
                    listener.onClick(item);
                }
            }
        });
        holder.itemView.setOnFocusChangeListener(null);
        // 行首/行尾吞掉左右键:否则长按会"漏"到相邻行的卡片上。
        holder.frame.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, android.view.KeyEvent event) {
                if (event.getAction() != android.view.KeyEvent.ACTION_DOWN) {
                    return false;
                }
                int pos = holder.getAdapterPosition();
                if (pos == RecyclerView.NO_POSITION) {
                    return false;
                }
                if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT && pos == 0) {
                    return true;
                }
                return keyCode == android.view.KeyEvent.KEYCODE_DPAD_RIGHT
                        && pos == getItemCount() - 1;
            }
        });
        holder.frame.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                float scale = hasFocus ? 1.03f : 1.0f;
                v.animate().scaleX(scale).scaleY(scale).setDuration(150).start();
            }
        });
    }

    /** "S1E2 · 剩余 12 分钟" / "剩余 12 分钟" / 空。 */
    private static String meta(MediaItem item) {
        StringBuilder sb = new StringBuilder();
        if (item.season_number > 0 || item.episode_number > 0) {
            sb.append(String.format(Locale.US, "S%dE%d", item.season_number, item.episode_number));
        }
        long duration = item.duration;
        long ts = item.ts > 0 ? item.ts : item.watched_ts;
        if (duration > ts && duration > 0) {
            long leftMin = Math.max(1, (duration - ts) / 60);
            if (sb.length() > 0) {
                sb.append("  ·  ");
            }
            if (leftMin >= 60) {
                sb.append("剩余 ").append(leftMin / 60).append(" 小时");
            } else {
                sb.append("剩余 ").append(leftMin).append(" 分钟");
            }
        }
        return sb.toString();
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    static final class VH extends RecyclerView.ViewHolder {
        final android.widget.FrameLayout frame;
        final ImageView img;
        final TextView tvTitle;
        final TextView tvMeta;
        final ProgressBar progress;

        VH(View itemView) {
            super(itemView);
            frame = (android.widget.FrameLayout) itemView.findViewById(R.id.frame);
            img = itemView.findViewById(R.id.img_cover);
            tvTitle = itemView.findViewById(R.id.tv_title);
            tvMeta = itemView.findViewById(R.id.tv_meta);
            progress = itemView.findViewById(R.id.progress);
        }
    }

}
