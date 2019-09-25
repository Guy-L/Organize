package com.lab.guy.organize;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.support.annotation.Nullable;
import android.support.v7.widget.AppCompatImageView;
import android.util.AttributeSet;
import android.util.Log;

public class PreView extends AppCompatImageView {

    private ValueAnimator colorAnimator = new ValueAnimator();

    public PreView(Context context) {
        super(context);
        setImageResource(R.drawable.preview48);
        setAlpha(0f);
    }

    public PreView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setImageResource(R.drawable.preview48);
        setAlpha(0f);
    }

    public void setup(final float x, final float y, final float screenScale){
        post(new Runnable() {
            @Override
            public void run() {
                setX(x - getWidth()/2f);
                setY(y - getHeight()/2f);

                setScaleX((2-screenScale)/2);
                setScaleY((2-screenScale)/2);
                invalidate();
            }
        });
    }

    @Override
    public float getX() {
        return super.getX() + getWidth()/2f;
    }

    @Override
    public float getY() {
        return super.getY() + getHeight()/2f;
    }

    public void select(){
        colorAnimator.setFloatValues(0,SELECT_SUBTRAHEND);
        colorAnimator.addUpdateListener(colorAnimatorUpdateListener);
        colorAnimator.start();
    }

    public void deselect(){
        colorAnimator.setFloatValues(SELECT_SUBTRAHEND,0);
        colorAnimator.addUpdateListener(colorAnimatorUpdateListener);
        colorAnimator.start();
    }

    static int GREY_INT = 239;
    static int SELECT_SUBTRAHEND = 30;
    private ValueAnimator.AnimatorUpdateListener colorAnimatorUpdateListener = new ValueAnimator.AnimatorUpdateListener() {
        @Override
        public void onAnimationUpdate(ValueAnimator valueAnimator) {
            //Darkens the current color filter by the current value. Will not go under 0.
            setColorFilter(Color.rgb(Math.max((int)(GREY_INT -(float)valueAnimator.getAnimatedValue()),0),
                                     Math.max((int)(GREY_INT -(float)valueAnimator.getAnimatedValue()),0),
                                     Math.max((int)(GREY_INT -(float)valueAnimator.getAnimatedValue()),0)
                                    ), PorterDuff.Mode.SRC_IN);
        }
    };
}