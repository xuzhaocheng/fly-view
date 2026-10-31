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

/** 海报网格适配器。 */
public class PosterAdapter extends RecyclerView.Adapter<PosterAdapter.VH> {

    public interface OnPosterClick {
        void onClick(MediaItem item);
    }

    private final List<MediaItem> data = new ArrayList<>();
    private final OnPosterClick listener;
    private final ImageUrlBuilder imageUrlBuilder;
    private final String token;

    public interface ImageUrlBuilder {
        String build(MediaItem item, int width);
    }

    public PosterAdapter(OnPosterClick listener, ImageUrlBuilder imageUrlBuilder, String token) {
        this.listener = listener;
        this.imageUrlBuilder = imageUrlBuilder;
        this.token = token;
    }

    public void submit(List<MediaItem> items) {
        data.clear();
        data.addAll(items);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_poster, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        final MediaItem item = data.get(position);
        String title = item.tv_title != null && !item.tv_title.isEmpty() ? item.tv_title : item.title;
        holder.tvTitle.setText(title == null ? "" : title);
        if (imageUrlBuilder != null) {
            String url = imageUrlBuilder.build(item, 400);
            int radius = (int) (6 * holder.itemView.getResources().getDisplayMetrics().density);
            ImageLoader.load(holder.itemView.getContext(), holder.img, url, token, radius);
        }
        holder.frame.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) {
                    listener.onClick(item);
                }
            }
        });
        // 网格里不做缩放:卡片放大后描边会被挤到父容器外裁掉,只剩底边一条白线。
        // 焦点只靠 2dp 白描边表示。
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final android.widget.FrameLayout frame;
        final ImageView img;
        final TextView tvTitle;

        VH(@NonNull View itemView) {
            super(itemView);
            frame = (android.widget.FrameLayout) itemView.findViewById(R.id.frame);
            img = itemView.findViewById(R.id.img_poster);
            tvTitle = itemView.findViewById(R.id.tv_title);
        }
    }
}
