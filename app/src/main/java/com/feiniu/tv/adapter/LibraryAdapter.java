package com.feiniu.tv.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.feiniu.tv.R;
import com.feiniu.tv.api.model.MediaDb;
import com.feiniu.tv.api.model.MediaItem;
import com.feiniu.tv.util.ImageLoader;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 首页「媒体库」卡片:用库内前几部影片的封面拼成 2x2 预览。 */
public class LibraryAdapter extends RecyclerView.Adapter<LibraryAdapter.VH> {

    public interface OnLibraryClick {
        void onClick(MediaDb db);
    }

    public interface ImageUrlBuilder {
        String build(MediaItem item, int width);
    }

    private final List<MediaDb> data = new ArrayList<>();
    private final Map<String, List<MediaItem>> previews = new HashMap<>();
    private final OnLibraryClick listener;
    private final ImageUrlBuilder urlBuilder;
    private final String token;

    public LibraryAdapter(OnLibraryClick listener, ImageUrlBuilder builder, String token) {
        this.listener = listener;
        this.urlBuilder = builder;
        this.token = token;
    }

    public void submit(List<MediaDb> items, Map<String, List<MediaItem>> previewMap) {
        data.clear();
        if (items != null) {
            data.addAll(items);
        }
        previews.clear();
        if (previewMap != null) {
            previews.putAll(previewMap);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_library, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull final VH holder, int position) {
        final MediaDb db = data.get(position);
        holder.title.setText(db.title == null ? "" : db.title);

        ImageView[] slots = {holder.a, holder.b, holder.c, holder.d};
        List<MediaItem> items = previews.get(db.guid);
        for (int i = 0; i < slots.length; i++) {
            MediaItem item = items != null && i < items.size() ? items.get(i) : null;
            if (item == null) {
                slots[i].setImageDrawable(null);
            } else if (urlBuilder != null) {
                int r = (int) (8 * holder.itemView.getResources().getDisplayMetrics().density);
                // 只圆 2×2 拼图的外侧角:a=左上 b=右上 c=左下 d=右下
                int tl = i == 0 ? r : 0;
                int tr = i == 1 ? r : 0;
                int bl = i == 2 ? r : 0;
                int br = i == 3 ? r : 0;
                ImageLoader.loadCorners(holder.itemView.getContext(), slots[i],
                        urlBuilder.build(item, ImageLoader.W_THUMB), token, tl, tr, br, bl);
            }
        }

        holder.frame.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) {
                    listener.onClick(db);
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
                v.animate().scaleX(scale).scaleY(scale).setDuration(150).start();
            }
        });
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    static final class VH extends RecyclerView.ViewHolder {
        final TextView title;
        final android.widget.FrameLayout frame;
        final ImageView a, b, c, d;

        VH(View itemView) {
            super(itemView);
            frame = (android.widget.FrameLayout) itemView.findViewById(R.id.frame);
            title = itemView.findViewById(R.id.tv_lib_title);
            a = itemView.findViewById(R.id.img_a);
            b = itemView.findViewById(R.id.img_b);
            c = itemView.findViewById(R.id.img_c);
            d = itemView.findViewById(R.id.img_d);
        }
    }
}
