package com.feiniu.tv.adapter;

import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.feiniu.tv.R;
import com.feiniu.tv.api.model.MediaDb;

import java.util.ArrayList;
import java.util.List;

/** 顶部分类栏适配器(选中项高亮)。 */
public class CategoryAdapter extends RecyclerView.Adapter<CategoryAdapter.VH> {

    public interface OnCategoryClick {
        void onClick(int position, MediaDb category);
    }

    private final List<MediaDb> data = new ArrayList<>();
    private final OnCategoryClick listener;
    private int selectedPosition = 0;

    public CategoryAdapter(OnCategoryClick listener) {
        this.listener = listener;
    }

    public void setSelected(int position) {
        if (selectedPosition == position) {
            return;
        }
        int old = selectedPosition;
        selectedPosition = position;
        notifyItemChanged(old);
        notifyItemChanged(position);
    }

    public void submit(List<MediaDb> categories) {
        data.clear();
        data.addAll(categories);
        selectedPosition = 0;
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
        final MediaDb c = data.get(position);
        holder.tv.setText(c.title);
        if (position == selectedPosition) {
            holder.tv.setTextColor(holder.tv.getContext().getResources().getColor(R.color.accent));
            holder.tv.setTypeface(null, Typeface.BOLD);
        } else {
            holder.tv.setTextColor(holder.tv.getContext().getResources().getColor(R.color.text_secondary));
            holder.tv.setTypeface(null, Typeface.NORMAL);
        }
        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) {
                    listener.onClick(position, c);
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
            tv = itemView.findViewById(R.id.tv_category);
        }
    }
}
