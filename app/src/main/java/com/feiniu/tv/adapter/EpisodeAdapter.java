package com.feiniu.tv.adapter;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.feiniu.tv.R;
import com.feiniu.tv.api.model.Episode;

import java.util.ArrayList;
import java.util.List;

/** 剧集列表适配器(支持高亮"正在观看"的集)。 */
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
        holder.tv.setText(String.valueOf(ep.episode_number));

        boolean current = ep.guid != null && ep.guid.equals(currentGuid);
        if (current) {
            holder.tv.setTextColor(Color.WHITE);
            holder.tv.setBackgroundColor(holder.tv.getContext().getResources().getColor(R.color.accent));
        } else {
            holder.tv.setTextColor(holder.tv.getContext().getResources().getColor(R.color.text_secondary));
            holder.tv.setBackgroundResource(R.drawable.bg_poster_card);
        }

        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) {
                    listener.onClick(ep);
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView tv;

        VH(@NonNull View itemView) {
            super(itemView);
            tv = itemView.findViewById(R.id.tv_episode);
        }
    }
}
