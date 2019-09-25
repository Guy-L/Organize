package com.lab.guy.organize;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewParent;
import android.widget.FrameLayout;

//Improvement of a solution by S/O Oded:
//https://stackoverflow.com/a/19204645/8387364
//Many thanks to Oded.
@SuppressWarnings("deprecation")
public class ZoomableScrollView extends FrameLayout {

    private static final long  FLING_DURATION_MIN = 100;
    private static final int   FLING_DILUTION_FACTOR = 7;
    private static final float FLING_THRESHOLD_MIN = 200;
    private static final float FLING_THRESHOLD_MAX = 400;

    private static final int INVALID_POINTER_ID = 1;
    private int mActivePointerId = INVALID_POINTER_ID;

    private float mScaleFactor = 1;
    private ScaleGestureDetector mScaleDetector;
    private Matrix mScaleMatrix = new Matrix();
    private Matrix mScaleMatrixInverse = new Matrix();

    public float mPosX;
    public float mPosY;
    private Float mInitX;
    private Float mInitY;
    private Matrix mTranslateMatrix = new Matrix();
    private Matrix mTranslateMatrixInverse = new Matrix();

    private float mLastTouchX;
    private float mLastTouchY;

    private float mFocusY;
    private float mFocusX;

    private int mCanvasWidth;
    private int mCanvasHeight;

    private GestureDetector gestureDetector;

    private float[] mInvalidateWorkingArray = new float[6];
    private float[] mDispatchTouchEventWorkingArray = new float[2];
    private float[] mOnTouchEventWorkingArray = new float[2];

    private boolean mIsScaling;

    private Folder.ActionRelayer mActionRelayer;

    public ZoomableScrollView(Context context) {
        super(context);
        GestureListener listener = new GestureListener();

        gestureDetector = new GestureDetector(getContext(), listener);
        setOnTouchListener(touchListener);

        mScaleDetector = new ScaleGestureDetector(context, new ScaleListener());
        mTranslateMatrix.setTranslate(0, 0);
        mScaleMatrix.setScale(1, 1);
    }

    public ZoomableScrollView(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
        GestureListener listener = new GestureListener();

        gestureDetector = new GestureDetector(getContext(), listener);
        setOnTouchListener(touchListener);

        mScaleDetector = new ScaleGestureDetector(context, new ScaleListener());
        mTranslateMatrix.setTranslate(0, 0);
        mScaleMatrix.setScale(1, 1);
    }

    private OnTouchListener touchListener = new OnTouchListener() {
        @SuppressLint("ClickableViewAccessibility")
        @Override
        public boolean onTouch(View view, MotionEvent motionEvent) {
            return gestureDetector.onTouchEvent(motionEvent);
        }
    };

    private class GestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onDown(MotionEvent e) {
            return false;
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {

            if(e1.getRawX()>e2.getRawX()) velocityX = -Math.abs(velocityX); //Fling right
            if(e2.getRawX()>e1.getRawX()) velocityX = Math.abs(velocityX); //Fling  left

            if(e1.getRawY()>e2.getRawY()) velocityY = -Math.abs(velocityY); //Fling bottom
            if(e2.getRawY()>e1.getRawY()) velocityY = Math.abs(velocityY); //Fling  top

            //+ = To Left/Top, - = To Right/Bottom
//            Todo Improve flinging (direction, threshold) && fix quick-succession && adapt method names accurately
//            startFling(velocityX>0?Math.min(velocityX>FLING_THRESHOLD_MIN?velocityX:0,FLING_THRESHOLD_MAX)
//                                  :Math.max(velocityX<-FLING_THRESHOLD_MIN?velocityX:0,-FLING_THRESHOLD_MAX),
//                       velocityY>0?Math.min(velocityY>FLING_THRESHOLD_MIN?velocityY:0,FLING_THRESHOLD_MAX)
//                                  :Math.max(velocityY<-FLING_THRESHOLD_MIN?velocityY:0,-FLING_THRESHOLD_MAX),
//                                   FLING_DURATION_MIN);

            return true;
        }

        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {
            if(mActionRelayer!=null) mActionRelayer.singleTap(e.getX(),e.getY());
            return super.onSingleTapConfirmed(e);
        }

        @Override
        public void onLongPress(MotionEvent e) {
            if(mActionRelayer!=null) mActionRelayer.longTap(e.getX(),e.getY());
            super.onLongPress(e);
        }

        @Override
        public boolean onDoubleTap(MotionEvent e) {
            if(mScaleFactor == 1.0f) setScaleSmooth(getMaxScale());
            else{setScaleSmooth(1.0f);}
            return false;
        }
    }

    public void passInitialPosition(float x, float y) {
        mInitX = x;
        mInitY = y;
    }
    public void passTapRelayer(Folder.ActionRelayer relayer){
        mActionRelayer = relayer;
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = getChildAt(i);
            if (child.getVisibility() != GONE) {
                child.layout(l, t, l+child.getMeasuredWidth(), t += child.getMeasuredHeight());
            }
        }
    }

    //todo temp
    private boolean bar = false;

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);


        if(!bar) {
            int height = 0;
            int width = 0;
            int childCount = getChildCount();
            for (int i = 0; i < childCount; i++) {
                View child = getChildAt(i);
                if (child.getVisibility() != GONE) {
                    measureChild(child, widthMeasureSpec, heightMeasureSpec);
                    height += child.getMeasuredHeight();
                    width = Math.max(width, child.getMeasuredWidth());
                }
            }
            mCanvasWidth = width;
            mCanvasHeight = height;

            scrollBy(mInitX == null ? (mCanvasWidth / 2 - getWidth() / 2) : mInitX,
                    mInitY == null ? (mCanvasHeight / 2 - getHeight() / 2) : mInitY);

            bar=!bar;
        }
        //todo Retain Position/Scale on Orientation Change
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        canvas.save();
        canvas.translate(mPosX, mPosY);
        canvas.scale(mScaleFactor, mScaleFactor, mFocusX, mFocusY);
        super.dispatchDraw(canvas);
        canvas.restore();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        mDispatchTouchEventWorkingArray[0] = ev.getX();
        mDispatchTouchEventWorkingArray[1] = ev.getY();
        mDispatchTouchEventWorkingArray = screenPointsToScaledPoints(mDispatchTouchEventWorkingArray);
        ev.setLocation(mDispatchTouchEventWorkingArray[0],
                mDispatchTouchEventWorkingArray[1]);
        return super.dispatchTouchEvent(ev);
    }

    /**
     * Although the docs say that you shouldn't override this, I decided to do
     * so because it offers me an easy way to change the invalidated area to my
     * likening.
     */
    @Override
    public ViewParent invalidateChildInParent(int[] location, Rect dirty) {

        mInvalidateWorkingArray[0] = dirty.left;
        mInvalidateWorkingArray[1] = dirty.top;
        mInvalidateWorkingArray[2] = dirty.right;
        mInvalidateWorkingArray[3] = dirty.bottom;

        mInvalidateWorkingArray = scaledPointsToScreenPoints(mInvalidateWorkingArray);
        dirty.set(Math.round(mInvalidateWorkingArray[0]), Math.round(mInvalidateWorkingArray[1]),
                Math.round(mInvalidateWorkingArray[2]), Math.round(mInvalidateWorkingArray[3]));

        location[0] *= mScaleFactor;
        location[1] *= mScaleFactor;
        return super.invalidateChildInParent(location, dirty);
    }

    //UNTESTED
    public void scrollBy(float x, float y){
        scrollTo((-x+mLastTouchX)-mPosX,(-y+mLastTouchY)-mPosY);
    }

    public void scrollTo(float x, float y){
        if (mIsScaling) {
            // Don't move during a QuickScale. Todo?
            mLastTouchX = x;
            mLastTouchY = y;
            return;
        }

        float dx = x - mPosX;
        float dy = y - mPosY;

        float[] topLeft = {0f, 0f};
        float[] bottomRight = {getWidth(), getHeight()};
        /*
         * Corners of the view in screen coordinates, so dx/dy should not be allowed to
         * push these beyond the canvas bounds.
         */
        float[] scaledTopLeft = screenPointsToScaledPoints(topLeft);
        float[] scaledBottomRight = screenPointsToScaledPoints(bottomRight);

        dx = Math.min(Math.max(dx, scaledBottomRight[0] - mCanvasWidth), scaledTopLeft[0]);
        dy = Math.min(Math.max(dy, scaledBottomRight[1] - mCanvasHeight), scaledTopLeft[1]);

        mPosX += dx;
        mPosY += dy;

        mTranslateMatrix.preTranslate(dx, dy);
        mTranslateMatrix.invert(mTranslateMatrixInverse);

        mLastTouchX = x;
        mLastTouchY = y;

        invalidate();
    }

    private float[] scaledPointsToScreenPoints(float[] a) {
        mScaleMatrix.mapPoints(a);
        mTranslateMatrix.mapPoints(a);
        return a;
    }

    private float[] screenPointsToScaledPoints(float[] a){
        mTranslateMatrixInverse.mapPoints(a);
        mScaleMatrixInverse.mapPoints(a);
        return a;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        mOnTouchEventWorkingArray[0] = ev.getX();
        mOnTouchEventWorkingArray[1] = ev.getY();

        mOnTouchEventWorkingArray = scaledPointsToScreenPoints(mOnTouchEventWorkingArray);

        ev.setLocation(mOnTouchEventWorkingArray[0], mOnTouchEventWorkingArray[1]);
        mScaleDetector.onTouchEvent(ev);

        final int action = ev.getAction();
        switch (action & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN: {
                final float x = ev.getX();
                final float y = ev.getY();

                mLastTouchX = x;
                mLastTouchY = y;

                // Save the ID of this pointer
                mActivePointerId = ev.getPointerId(0);
                break;
            }

            case MotionEvent.ACTION_MOVE: {
                // Find the index of the active pointer and fetch its position
                final int pointerIndex = ev.findPointerIndex(mActivePointerId);
                final float x = ev.getX(pointerIndex);
                final float y = ev.getY(pointerIndex);

                if (mIsScaling && ev.getPointerCount() == 1) {
                    // Don't move during a QuickScale.
                    mLastTouchX = x;
                    mLastTouchY = y;

                    break;
                }

                float dx = x - mLastTouchX;
                float dy = y - mLastTouchY;

                float[] topLeft = {0f, 0f};
                float[] bottomRight = {getWidth(), getHeight()};

                //Corners of the view in screen coordinates, so dx/dy should not be allowed to
                //push these beyond the canvas bounds.
                float[] scaledTopLeft = screenPointsToScaledPoints(topLeft);
                float[] scaledBottomRight = screenPointsToScaledPoints(bottomRight);

                dx = Math.min(Math.max(dx, scaledBottomRight[0] - mCanvasWidth), scaledTopLeft[0]);
                dy = Math.min(Math.max(dy, scaledBottomRight[1] - mCanvasHeight), scaledTopLeft[1]);

                mPosX += dx;
                mPosY += dy;

                mTranslateMatrix.preTranslate(dx, dy);
                mTranslateMatrix.invert(mTranslateMatrixInverse);

                mLastTouchX = x;
                mLastTouchY = y;

                invalidate();
                break;
            }

            case MotionEvent.ACTION_UP: {
                mActivePointerId = INVALID_POINTER_ID;
                break;
            }

            case MotionEvent.ACTION_CANCEL: {
                mActivePointerId = INVALID_POINTER_ID;
                break;
            }

            case MotionEvent.ACTION_POINTER_UP: {
                // Extract the index of the pointer that left the touch sensor
                final int pointerIndex = (action & MotionEvent.ACTION_POINTER_INDEX_MASK) >> MotionEvent.ACTION_POINTER_INDEX_SHIFT;
                final int pointerId = ev.getPointerId(pointerIndex);
                if (pointerId == mActivePointerId) {
                    // This was our active pointer going up. Choose a new
                    // active pointer and adjust accordingly.
                    final int newPointerIndex = pointerIndex == 0 ? 1 : 0;
                    mLastTouchX = ev.getX(newPointerIndex);
                    mLastTouchY = ev.getY(newPointerIndex);
                    mActivePointerId = ev.getPointerId(newPointerIndex);
                }
                break;
            }
        }
        return true;
    }

    public float getMaxScale() {
        return 1.50f;
    }

    public float getMinScale() {
        //Must NEVER be under 0.5 or over 1.
        return 0.75f; //todo investigate division
        //todo also why function instead of constant?
    }

    @Override
    public void setScaleX(float scaleX) {
        setScale(scaleX);
    }

    @Override
    public float getScaleX() {
        return mScaleFactor;
    }

    @Override
    public float getScaleY() {
        return mScaleFactor;
    }

    public float getScale(){return mScaleFactor;}

    //todo temp??
    public float fooX(){
        return -mPosX;
    }

    public float fooY(){
        return -mPosY;
    }

    @Override
    public void setScaleY(float scaleY) {
        setScale(scaleY);
    }

    private void setScale(float scaleFactor){
        setScale(scaleFactor, (float[]) null);
    }

    private void setScale(float scaleFactor, MotionEvent e){
        setScale(scaleFactor,new float[]{e.getX(),e.getY()});
    }

    ValueAnimator scaleAnimator;
    ValueAnimator flingAnimatorX;
    ValueAnimator flingAnimatorY;

    private void setScaleSmooth(float scaleFactor){
        scaleAnimator = new ValueAnimator();
        scaleAnimator.setFloatValues(mScaleFactor,scaleFactor);
        scaleAnimator.setDuration((long)(Math.abs(scaleFactor-mScaleFactor)*900));
        scaleAnimator.addUpdateListener(scaleListener);
        scaleAnimator.start();
    }

    ValueAnimator.AnimatorUpdateListener scaleListener = new ValueAnimator.AnimatorUpdateListener() {
        @Override
        public void onAnimationUpdate(ValueAnimator valueAnimator) {
            setScale((float)valueAnimator.getAnimatedValue());
        }
    };

    private void startFling(float velocityX, float velocityY, long duration){
        Log.i("FLING","Velocity X: " + velocityX);
        Log.i("FLING","Velocity Y: " + velocityY);
        Log.i("FLING","Duration: " + duration);

        if(Math.abs(velocityX)>0) {
            flingAnimatorX = new ValueAnimator();
            flingAnimatorX.setFloatValues(velocityX/FLING_DILUTION_FACTOR,0);
            flingAnimatorX.setDuration(duration);
            flingAnimatorX.addUpdateListener(flingListenerX);
            flingAnimatorX.start();
        }

        if(Math.abs(velocityY)>0) {
            flingAnimatorY = new ValueAnimator();
            flingAnimatorY.setFloatValues(velocityY/FLING_DILUTION_FACTOR,0);
            flingAnimatorY.setDuration(duration);
            flingAnimatorY.addUpdateListener(flingListenerY);
            flingAnimatorY.start();
        }
    }

    ValueAnimator.AnimatorUpdateListener flingListenerX = new ValueAnimator.AnimatorUpdateListener() {
        @Override
        public void onAnimationUpdate(ValueAnimator valueAnimator) {
            scrollTo(mPosX+(float)valueAnimator.getAnimatedValue(),mPosY);

        }
    };

    ValueAnimator.AnimatorUpdateListener flingListenerY = new ValueAnimator.AnimatorUpdateListener() {
        @Override
        public void onAnimationUpdate(ValueAnimator valueAnimator) {
            scrollTo(mPosX,mPosY+(float)valueAnimator.getAnimatedValue());

        }
    };

    private void setScale(float scaleFactor, @Nullable float[] pivot){
        if(scaleFactor == mScaleFactor) return;

        mFocusX = pivot == null? mLastTouchX : pivot[0];
        mFocusY = pivot == null? mLastTouchY : pivot[1];


        float[] foci = {mFocusX, mFocusY};
        float[] scaledFoci = screenPointsToScaledPoints(foci);

        mFocusX = scaledFoci[0];
        mFocusY = scaledFoci[1];

        if(mActionRelayer!=null) mActionRelayer.onScale();

        mScaleFactor = scaleFactor;
        mScaleFactor = Math.max(getMinScale(), Math.min(mScaleFactor, getMaxScale()));
        mScaleMatrix.setScale(mScaleFactor, mScaleFactor, mFocusX, mFocusY);
        mScaleMatrix.invert(mScaleMatrixInverse);
        invalidate();
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScaleBegin(ScaleGestureDetector detector) {
            mIsScaling = true;

            mFocusX = detector.getFocusX();
            mFocusY = detector.getFocusY();

            float[] foci = {mFocusX, mFocusY};
            float[] scaledFoci = screenPointsToScaledPoints(foci);

            mFocusX = scaledFoci[0];
            mFocusY = scaledFoci[1];

            return true;
        }

        @Override
        public void onScaleEnd(ScaleGestureDetector detector) {
            mIsScaling = false;
        }

        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            if(mActionRelayer!=null) mActionRelayer.onScale();

            mScaleFactor *= detector.getScaleFactor();
            mScaleFactor = Math.max(getMinScale(), Math.min(mScaleFactor, getMaxScale()));
            mScaleMatrix.setScale(mScaleFactor, mScaleFactor, mFocusX, mFocusY);
            mScaleMatrix.invert(mScaleMatrixInverse);
            invalidate();

            return true;
        }
    }

}





///////////////////////////////////
////////// OLD AND DEBUG //////////
///////////////////////////////////


//                mLastTouchX = mPosX+mCanvasWidth/2;
//                mLastTouchY = mPosY+mCanvasHeight/2;
//    scrollTo(mPosX+0,mPosY+5);


//            Log.d("DEBUG",String.valueOf("------FLING X------"));
//            Log.d("DEBUG",String.valueOf(mPosX));
//            Log.d("DEBUG",String.valueOf(velocityX/10));

//            Log.d("DEBUG",String.valueOf("------Y------"));
//            Log.d("DEBUG",String.valueOf(mPosY));
//            Log.d("DEBUG",String.valueOf(velocityY/10));

//            Log.d("DEBUG","X: " + String.valueOf(mPosX));
//            Log.d("DEBUG","+X: " + String.valueOf(valueAnimator.getAnimatedValue()));

//            Log.d("DEBUG","Y: " + String.valueOf(valueAnimator.getAnimatedValue()));
//            setScrollY(Math.round((float)valueAnimator.getAnimatedValue()));

//            startFling(Math.min(Math.abs(velocityX) > FLING_THRESHOLD_MIN?velocityX:0,FLING_THRESHOLD_MAX),
//                       Math.min(Math.abs(velocityY) > FLING_THRESHOLD_MIN?velocityY:0,FLING_THRESHOLD_MAX),
//                       Math.max(mScroller.getDuration(),FLING_DURATION_MIN));

// Log.d("DEBUG",String.valueOf(velocityX>0?Math.min(velocityX>FLING_THRESHOLD_MIN?velocityX:0,FLING_THRESHOLD_MAX)
//         :Math.max(velocityX<-FLING_THRESHOLD_MIN?velocityX:0,-FLING_THRESHOLD_MAX)));
//        Log.d("DEBUG",String.valueOf(velocityY>0?Math.min(velocityY>FLING_THRESHOLD_MIN?velocityY:0,FLING_THRESHOLD_MAX)
//        :Math.max(velocityY<-FLING_THRESHOLD_MIN?velocityY:0,-FLING_THRESHOLD_MAX)));
//        Log.d("DEBUG",String.valueOf(Math.max(mScroller.getDuration(),FLING_DURATION_MIN)));

////            Scroller mScroller = new Scroller(getContext());
////            mScroller.fling((int)mPosX,(int)mPosY,(int)velocityX,(int)velocityY,0,(int)(getWidth()/getScaleX()),
////                                                                                 0,(int)(getHeight()/getScaleY()));
////
////            Log.d("DEBUG", "StartX: " + String.valueOf(mScroller.getStartX()));
////            Log.d("DEBUG", "FinalX: " + String.valueOf(mScroller.getFinalX()));
////            Log.d("DEBUG", "DSTX: "+ String.valueOf(mScroller.getFinalX()-mScroller.getStartX()));
////            Log.d("DEBUG", "VELX: "+ String.valueOf(velocityX));