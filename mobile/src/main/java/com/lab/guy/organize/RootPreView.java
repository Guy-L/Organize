package com.lab.guy.organize;

import android.content.Context;
import android.support.annotation.Nullable;
import android.util.AttributeSet;

public class RootPreView extends PreView implements ZoomReactive {
    public RootPreView(Context context) {
        super(context);
        setImageResource(R.drawable.rootpre96);
        setAlpha(1f);
    }

    public RootPreView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setImageResource(R.drawable.rootpre96);
        setAlpha(1f);
    }

    @Override
    public void onParentScale(ZoomableScrollView zoomableLayout) { //todo: BAD SCALING..?
        setScaleX(0.5f / zoomableLayout.getScaleX());
        setScaleY(0.5f / zoomableLayout.getScaleY());

        invalidate();
    }
}
