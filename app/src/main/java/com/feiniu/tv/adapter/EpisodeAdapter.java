package com.feiniu.tv.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.feiniu.tv.R;
import com.feiniu.tv.api.model.Episode;

import java.util.ArrayList;
import java.util.List;

/** 剧集卡片:集号 + 集标题 + 底部观看进度,点一下直接播。 */
public class EpisodeAdapter extends RecyclerView.Adapter<EpisodeAdapter.VH> {

    public interface OnEpisodeClick {
        void onClick(Episode episode);
    }

    private final List<Episode> data = new ArrayList<>();
    private final OnEpisodeClick listener;
    private String currentGuid = "";

    public EpisodeAdapter(OnEpisodeClick listener) {
        this.listener = listener;
    }

    public void setCurrentGuid(String guid) {
        this.currentGuid = guid == null ? "" : guid;
    }

    public void submit(List<Episode> episodes) {
        data.clear();
        data.addAll(episodes);
        notifyDataSetChanged();
    }

    public int indexOf(String guid) {
        for (int i = 0; i < data.size(); i++) {
            if (data.get(i).guid != null && data.get(i).guid.equals(guid)) {
                return i;
            }
        }
        return -1;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_episode, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        final Episode ep = data.get(position);
        boolean current = ep.guid != null && ep.guid.equals(currentGuid);

        String label = ep.episode_number > 0
                ? holder.itemView.getContext().getString(R.string.episode_label, ep.episode_number)
                : "";
        if (ep.runtime > 0) {
            label = label.isEmpty() ? ep.runtime + " 分钟" : label + " · " + ep.runtime + " 分钟";
        }
        holder.tvNumber.setText(label);
        holder.tvNumber.setTextColor(holder.itemView.getContext().getResources()
                .getColor(current ? R.color.accent : R.color.text_tertiary));

        String title = ep.title == null ? "" : ep.title.trim();
        holder.tvTitle.setText(title.isEmpty() ? "—" : title);

        holder.itemView.setSelected(current);
        bindProgress(holder, ep.watchedRatio());

        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) {
                    listener.onClick(ep);
                }
            }
        });
    }

    /** 底部进度条:按已看比例分配两段宽度,没看过就整条隐藏。 */
    private static void bindProgress(VH holder, float ratio) {
        // 只看了几秒的进度线画出来也就 1px,没有信息量
        if (ratio < 0.02f) {
            holder.llProgress.setVisibility(View.GONE);
            return;
        }
        holder.llProgress.setVisibility(View.VISIBLE);
        LinearLayout.LayoutParams fill =
                (LinearLayout.LayoutParams) holder.progressFill.getLayoutParams();
        LinearLayout.LayoutParams rest =
                (LinearLayout.LayoutParams) holder.progressRest.getLayoutParams();
        fill.weight = ratio;
        rest.weight = Math.max(0.0001f, 1f - ratio);
        holder.progressFill.setLayoutParams(fill);
        holder.progressRest.setLayoutParams(rest);
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView tvNumber;
        final TextView tvTitle;
        final LinearLayout llProgress;
        final View progressFill;
        final View progressRest;

        VH(@NonNull View itemView) {
            super(itemView);
            tvNumber = itemView.findViewById(R.id.tv_episode);
            tvTitle = itemView.findViewById(R.id.tv_episode_title);
            llProgress = itemView.findViewById(R.id.ll_progress);
            progressFill = itemView.findViewById(R.id.progress_fill);
            progressRest = itemView.findViewById(R.id.progress_rest);
        }
    }
}
