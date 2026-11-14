package com.feiniu.tv.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.feiniu.tv.R;
import com.feiniu.tv.api.model.Season;

import java.util.ArrayList;
import java.util.List;

/** 季标签:可选,选中项用 accent 填充,焦点另加描边。 */
public class SeasonAdapter extends RecyclerView.Adapter<SeasonAdapter.VH> {

    public interface OnSeasonClick {
        void onClick(Season season);
    }

    private final List<Season> data = new ArrayList<>();
    private final OnSeasonClick listener;
    private String selectedGuid = "";

    public SeasonAdapter(OnSeasonClick listener) {
        this.listener = listener;
    }

    public void submit(List<Season> seasons) {
        data.clear();
        data.addAll(seasons);
        notifyDataSetChanged();
    }

    /** 只改高亮,不动列表内容。 */
    public void setSelectedGuid(String guid) {
        selectedGuid = guid == null ? "" : guid;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_season, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        final Season s = data.get(position);
        holder.tv.setText(s.title == null ? "" : s.title);
        holder.tv.setSelected(s.guid != null && s.guid.equals(selectedGuid));
        holder.tv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) {
                    listener.onClick(s);
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
            tv = itemView.findViewById(R.id.tv_season);
        }
    }
}
