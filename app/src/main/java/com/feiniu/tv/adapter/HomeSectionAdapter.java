package com.feiniu.tv.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.feiniu.tv.R;
import com.feiniu.tv.api.model.MediaDb;
import com.feiniu.tv.api.model.MediaItem;
import com.feiniu.tv.util.ImageLoader;

import java.util.ArrayList;
import java.util.List;

/**
 * 首页纵向分区列表。每个 section 自带一条横向 RecyclerView:
 * 纵向滚动/焦点由外层 RecyclerView 负责,避免 ScrollView 嵌套导致的
 * "焦点跑到屏幕外、页面不跟滚"问题。
 */
public class HomeSectionAdapter extends RecyclerView.Adapter<HomeSectionAdapter.VH> {

    public static final int TYPE_LIBRARIES = 0;
    public static final int TYPE_CONTINUE = 1;
    public static final int TYPE_POSTERS = 2;
    public static final int TYPE_HEADER = 3;

    public static final class Section {
        public final int type;
        public final String title;
        public final MediaDb db;              // TYPE_POSTERS / TYPE_LIBRARIES 可能为空
        public final List<MediaItem> items = new ArrayList<>();
        public final List<MediaDb> dbList = new ArrayList<>();
        public final java.util.Map<String, List<MediaItem>> previews = new java.util.HashMap<>();
        public boolean showMore;
        public boolean loading = true;

        public Section(int type, String title, MediaDb db, boolean showMore) {
            this.type = type;
            this.title = title;
            this.db = db;
            this.showMore = showMore;
        }
    }

    public interface Callbacks {
        void onLibrary(MediaDb db);

        void onContinue(MediaItem item);

        void onPoster(MediaItem item);

        void onMore(MediaDb db);

        void onSettings();
    }

    private final List<Section> sections = new ArrayList<>();
    private final Callbacks callbacks;
    private final String token;

    public HomeSectionAdapter(Callbacks callbacks, String token) {
        this.callbacks = callbacks;
        this.token = token;
    }

    public void submit(List<Section> list) {
        sections.clear();
        if (list != null) {
            sections.addAll(list);
        }
        notifyDataSetChanged();
    }

    public void updateSection(int index) {
        if (index >= 0 && index < sections.size()) {
            notifyItemChanged(index);
        }
    }

    public int indexOf(Section section) {
        return sections.indexOf(section);
    }

    @Override
    public int getItemViewType(int position) {
        return sections.get(position).type;
    }

    @Override
    public int getItemCount() {
        return sections.size();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_HEADER) {
            View header = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_home_header, parent, false);
            return new VH(header);
        }
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_home_section, parent, false);
        VH vh = new VH(v);
        vh.rvRow.setLayoutManager(
                new LinearLayoutManager(parent.getContext(), LinearLayoutManager.HORIZONTAL, false));
        vh.rvRow.setClipChildren(false);
        vh.rvRow.setClipToPadding(false);
        return vh;
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        final Section section = sections.get(position);
        if (section.type == TYPE_HEADER) {
            View settings = holder.itemView.findViewById(R.id.btn_settings);
            if (settings != null) {
                settings.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (callbacks != null) {
                            callbacks.onSettings();
                        }
                    }
                });
            }
            return;
        }
        holder.title.setText(section.title == null ? "" : section.title);
        // 标题只做视觉分隔;进入完整列表靠行尾的「查看全部」卡片。
        holder.title.setFocusable(false);

        if (section.type == TYPE_LIBRARIES) {
            LibraryAdapter adapter = new LibraryAdapter(new LibraryAdapter.OnLibraryClick() {
                @Override
                public void onClick(MediaDb db) {
                    if (callbacks != null) {
                        callbacks.onLibrary(db);
                    }
                }
            }, new LibraryAdapter.ImageUrlBuilder() {
                @Override
                public String build(MediaItem item, int width) {
                    return urlBuilder == null ? "" : urlBuilder.build(item, width);
                }
            }, token);
            holder.rvRow.setAdapter(adapter);
            adapter.submit(section.dbList, section.previews);
        } else if (section.type == TYPE_CONTINUE) {
            ContinueAdapter adapter = new ContinueAdapter(new ContinueAdapter.OnContinueClick() {
                @Override
                public void onClick(MediaItem item) {
                    if (callbacks != null) {
                        callbacks.onContinue(item);
                    }
                }
            }, new ContinueAdapter.ImageUrlBuilder() {
                @Override
                public String build(MediaItem item, int width) {
                    return imageUrl(item);
                }
            }, token);
            holder.rvRow.setAdapter(adapter);
            adapter.submit(section.items);
        } else {
            PosterRowAdapter adapter = new PosterRowAdapter(new PosterRowAdapter.OnItemClick() {
                @Override
                public void onClick(MediaItem item) {
                    if (callbacks != null) {
                        callbacks.onPoster(item);
                    }
                }
            }, new PosterRowAdapter.ImageUrlBuilder() {
                @Override
                public String build(MediaItem item, int width) {
                    return imageUrl(item);
                }
            }, token);
            holder.rvRow.setAdapter(adapter);
            if (section.db != null) {
                adapter.setSeeAll(true, new PosterRowAdapter.OnSeeAllClick() {
                    @Override
                    public void onSeeAll() {
                        if (callbacks != null) {
                            callbacks.onMore(section.db);
                        }
                    }
                });
            }
            adapter.submit(section.items);
        }
    }

    /** 图片地址由外部注入,避免适配器和 API 客户端耦合。 */
    public interface UrlBuilder {
        String build(MediaItem item, int width);
    }

    private UrlBuilder urlBuilder;

    public void setUrlBuilder(UrlBuilder builder) {
        this.urlBuilder = builder;
    }

    private String imageUrl(MediaItem item) {
        return urlBuilder == null ? "" : urlBuilder.build(item, ImageLoader.W_WIDE);
    }


    static final class VH extends RecyclerView.ViewHolder {
        final TextView title;
        final RecyclerView rvRow;

        VH(View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.tv_section_title);
            rvRow = itemView.findViewById(R.id.rv_row);
        }
    }
}
