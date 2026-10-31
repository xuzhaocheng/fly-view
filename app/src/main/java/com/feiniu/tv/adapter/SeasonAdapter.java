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

/** 季列表适配器(季标题仅作标签展示,不可选中)。 */
public class SeasonAdapter extends RecyclerView.Adapter<SeasonAdapter.VH> {

    private final List<Season> data = new ArrayList<>();

    public void submit(List<Season> seasons) {
        data.clear();
        data.addAll(seasons);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_category, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        final Season s = data.get(position);
        holder.tv.setText(s.title);
        holder.tv.setFocusable(false);
        holder.tv.setFocusableInTouchMode(false);
        holder.tv.setTextColor(holder.tv.getContext().getResources().getColor(R.color.text_secondary));
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView tv;

        VH(@NonNull View itemView) {
            super(itemView);
            tv = itemView.findViewById(R.id.tv_category);
        }
    }
}
