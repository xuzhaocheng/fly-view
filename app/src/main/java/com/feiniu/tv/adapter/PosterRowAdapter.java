package com.feiniu.tv.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.feiniu.tv.R;
import com.feiniu.tv.api.model.MediaItem;
import com.feiniu.tv.util.ImageLoader;

import java.util.ArrayList;
import java.util.List;

/**
 * 首页各媒体库分区里的横向海报行。
 * 行尾可附加一个「查看全部」卡片,这样进入完整列表不需要额外的可聚焦标题。
 */
public class PosterRowAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_POSTER = 0;
    private static final int TYPE_SEE_ALL = 1;

    public interface OnItemClick {
        void onClick(MediaItem item);
    }

    public interface OnSeeAllClick {
        void onSeeAll();
    }

    private final List<MediaItem> data = new ArrayList<>();
    private final OnItemClick listener;
    private final ImageUrlBuilder imageUrlBuilder;
    private final String token;
    private boolean seeAll;
    private OnSeeAllClick seeAllListener;

    public interface ImageUrlBuilder {
        String build(MediaItem item, int width);
    }

    public PosterRowAdapter(OnItemClick listener, ImageUrlBuilder builder, String token) {
        this.listener = listener;
        this.imageUrlBuilder = builder;
        this.token = token;
    }

    public void setSeeAll(boolean enabled, OnSeeAllClick click) {
        this.seeAll = enabled;
        this.seeAllListener = click;
        notifyDataSetChanged();
    }

    public void submit(List<MediaItem> items) {
        data.clear();
        if (items != null) {
            data.addAll(items);
        }
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return seeAll && position == data.size() ? TYPE_SEE_ALL : TYPE_POSTER;
    }

    @Override
    public int getItemCount() {
        return data.size() + (seeAll ? 1 : 0);
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_SEE_ALL) {
            View v = inflater.inflate(R.layout.item_see_all, parent, false);
            return new SeeAllVH(v);
        }
        View v = inflater.inflate(R.layout.item_poster_row, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof SeeAllVH) {
            ((SeeAllVH) holder).itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (seeAllListener != null) {
                        seeAllListener.onSeeAll();
                    }
                }
            });
            // 「查看全部」永远是行尾:长按右键必须停在这里,不能再往外跳。
            holder.itemView.setOnKeyListener(new View.OnKeyListener() {
                @Override
                public boolean onKey(View v, int keyCode, android.view.KeyEvent event) {
                    if (event.getAction() != android.view.KeyEvent.ACTION_DOWN) {
                        return false;
                    }
                    if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_RIGHT) {
                        return true;
                    }
                    return keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT
                            && data.isEmpty();
                }
            });
            return;
        }
        bindPoster((VH) holder, position);
    }

    private void bindPoster(@NonNull final VH holder, int position) {
        final MediaItem item = data.get(position);
        String title = item.tv_title != null && !item.tv_title.isEmpty()
                ? item.tv_title : item.title;
        holder.tvTitle.setText(title == null ? "" : title);

        int radius = (int) (6 * holder.itemView.getResources().getDisplayMetrics().density);
        ImageLoader.load(holder.itemView.getContext(), holder.img,
                imageUrlBuilder.build(item, 400), token, radius);

        holder.frame.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) {
                    listener.onClick(item);
                }
            }
        });
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
                float scale = hasFocus ? 1.04f : 1.0f;
                v.animate().scaleX(scale).scaleY(scale).setDuration(140).start();
            }
        });
    }

    static final class VH extends RecyclerView.ViewHolder {
        final android.widget.FrameLayout frame;
        final ImageView img;
        final TextView tvTitle;

        VH(View itemView) {
            super(itemView);
            frame = (android.widget.FrameLayout) itemView.findViewById(R.id.frame);
            img = itemView.findViewById(R.id.img_cover);
            tvTitle = itemView.findViewById(R.id.tv_title);
        }
    }

    static final class SeeAllVH extends RecyclerView.ViewHolder {
        SeeAllVH(View itemView) {
            super(itemView);
        }
    }
}
