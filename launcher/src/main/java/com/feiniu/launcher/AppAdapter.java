package com.feiniu.launcher;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/** 应用网格适配器。 */
public class AppAdapter extends RecyclerView.Adapter<AppAdapter.VH> {

    public static class AppEntry {
        public String label;
        public Drawable icon;
        public Intent intent;

        public AppEntry(String label, Drawable icon, Intent intent) {
            this.label = label;
            this.icon = icon;
            this.intent = intent;
        }
    }

    private final Context context;
    private final List<AppEntry> data = new ArrayList<>();

    public AppAdapter(Context context) {
        this.context = context;
    }

    public void submit(List<AppEntry> apps) {
        data.clear();
        data.addAll(apps);
        notifyDataSetChanged();
    }

    public int indexOfLabel(String label) {
        for (int i = 0; i < data.size(); i++) {
            if (label.equals(data.get(i).label)) {
                return i;
            }
        }
        return -1;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        final AppEntry e = data.get(position);
        holder.tvName.setText(e.label);
        if (e.icon != null) {
            holder.img.setImageDrawable(e.icon);
        } else {
            holder.img.setImageDrawable(null);
        }
        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (e.intent != null) {
                    try {
                        e.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        context.startActivity(e.intent);
                    } catch (Exception ignored) {
                    }
                }
            }
        });
        holder.itemView.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                float s = hasFocus ? 1.08f : 1.0f;
                v.animate().scaleX(s).scaleY(s).setDuration(120).start();
            }
        });
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final ImageView img;
        final TextView tvName;

        VH(@NonNull View itemView) {
            super(itemView);
            img = itemView.findViewById(R.id.img_icon);
            tvName = itemView.findViewById(R.id.tv_name);
        }
    }
}
