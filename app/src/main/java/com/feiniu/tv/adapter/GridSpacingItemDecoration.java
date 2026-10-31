package com.feiniu.tv.adapter;

import android.graphics.Rect;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/** 网格等间距 ItemDecoration,让海报墙均匀分布。 */
public class GridSpacingItemDecoration extends RecyclerView.ItemDecoration {

    private final int spacing;
    private final int spanCount;

    public GridSpacingItemDecoration(int spacing, int spanCount) {
        this.spacing = spacing;
        this.spanCount = spanCount;
    }

    @Override
    public void getItemOffsets(@NonNull Rect outRect, @NonNull View view,
                               @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        int position = parent.getChildAdapterPosition(view);
        if (position < 0) {
            return;
        }
        int column = position % spanCount;
        // 左间距按列比例分配,保证边缘和中间均匀
        outRect.left = spacing - column * spacing / spanCount;
        outRect.right = (column + 1) * spacing / spanCount;
        outRect.top = spacing;
        outRect.bottom = spacing;
    }
}
