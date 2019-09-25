package com.lab.guy.organize.Elements;

import android.animation.ValueAnimator;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.support.v7.widget.AppCompatImageView;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.AnticipateOvershootInterpolator;
import android.widget.ImageView;
import com.lab.guy.organize.ConstraintGrid;
import com.lab.guy.organize.Folder;
import com.lab.guy.organize.TagView;
import com.lab.guy.organize.ZoomReactive;
import com.lab.guy.organize.ZoomableScrollView;

public abstract class ElementView extends AppCompatImageView implements ZoomReactive {
    //todo the way scale generally is handled is inconsistent and incoherent... look into it.

    private static int DARKER_COLOR_ADDEND = 30;
    private static int MIN_MOVING_DISTANCE = 5;

    public enum TagDisplayMode{LEFT,CENTER,RIGHT}

    private ValueAnimator posXAnimator = new ValueAnimator();
    private ValueAnimator posYAnimator = new ValueAnimator();
    private ValueAnimator scaleAnimator = new ValueAnimator();
    private ValueAnimator colorAnimator = new ValueAnimator();

    private AnticipateOvershootInterpolator onMovePositionInterpolator = new AnticipateOvershootInterpolator();
    private AnticipateOvershootInterpolator onTouchScaleInterpolator = new AnticipateOvershootInterpolator();
    private AccelerateInterpolator onTouchColorInterpolator = new AccelerateInterpolator();

    private GestureDetector mGestureDetector;

    private Folder mFolderReference;
    private ConstraintGrid mGridReference;

    protected ImageView mCover;
    protected TagView mTagView;

    public boolean isLongPressed = false;
    public boolean isMoving = false;

    private float dX, dY, dScale;

    TagDisplayMode mTagDisplayMode;
    float mTagXOffset;
    float mTagYOffset;
    float mScale;
    String mTagText;
    int mColor;
    int mID;
    int mX;
    int mY;



    public ElementView(Folder folder, int imageResource, int id, int x, int y, int color, float scale, String tag, float tagXOffset, float tagYOffset, TagDisplayMode tagDisplayMode) {
        super(folder.getApplicationContext(), null);
        setImageResource(imageResource);
        mFolderReference = folder;

        //Interprets: Position
        mGridReference = mFolderReference.getGrid();
        position(mGridReference.getVerLinesCount()/2 + x, mGridReference.getHorLinesCount()/2 + y);

        //Interprets: Color
        setColorFilter(color, PorterDuff.Mode.SRC_IN);

        //Interprets: Scale
        setScaleX(scale);
        setScaleY(scale);

        //Interprets: ID
        setId(id);

        //Stores necessary info
        mID = id;
        mX = mGridReference.getVerLinesCount()/2 + x;
        mY = mGridReference.getHorLinesCount()/2 + y;
        mColor = color;
        mScale = scale;
        mTagText = tag;
        mTagDisplayMode = tagDisplayMode;
        mTagXOffset = tagXOffset;
        mTagYOffset = tagYOffset;

        //Kicks off touch event handling
        setOnTouchListener(getElementTouchListener());
        SimpleGestureListener listener = new SimpleGestureListener();
        mGestureDetector = new GestureDetector(getContext(), listener);
    }

    public void supplyAuxiliaryViews(TagView tagView, ImageView cover){
        try{
            if(tagView == null || cover == null ||
               mTagView != null || mCover != null){throw new Exception();}

            mCover = cover;
            mCover.setImageAlpha(0);

            mTagView = tagView; //todo Tags
            mTagView.setText(mTagText);
        } catch(Exception e){
            e.printStackTrace();
        }
    }

    //todo move these functions down
    //todo centering issue with saving
    public void setTagXOffset(float tagXOffset){
        mTagXOffset = tagXOffset;
        save();
    }

    public void setTagYOffset(float tagYOffset){
        mTagYOffset = tagYOffset;
        save();
    }

    public float getTagXOffset(){
        return mTagXOffset;
    }

    public float getTagYOffset(){
        return mTagYOffset;
    }

    public void setTagText(String tagText){
        mTagText = tagText;
        save();
    }

    private void position(final int x, final int y){ //Initial moving method, doesn't update mX & mY.
        if(x<0 || x>=mGridReference.getVerLinesCount() || y<0 || y>=mGridReference.getVerLinesCount()) return;
        post(new Runnable() { //Ensures correct bound calculations at startup.
            @Override
            public void run() {
                setX(mGridReference.getWorldX(x)-getWidth()/2f);
                setY(mGridReference.getWorldY(y)-getHeight()/2f);

                mTagView.setX(getX()+mTagXOffset);
                mTagView.setY(getY()+mTagYOffset);
            }
        });
    }

    protected Folder save(){
        return mFolderReference;
        //Must be overridden.
    }

    private View.OnTouchListener getElementTouchListener(){
        return new OnTouchListener() {

            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                performClick();

                switch(motionEvent.getAction()){
                    case MotionEvent.ACTION_MOVE:

                        if(isLongPressed){ //Todo make it so you need to go all the way to the distance you left it at before being able to change it again.
                            //Changes scale. For later use: I translated the object-finger distance to fit within [1.0 ; 2.0].
                            //               I take the greater of the horizontal and vertical version of this value so the movement works all ways.
                            mScale = Math.max(Math.min(Math.abs(Math.abs((motionEvent.getRawX() / dScale + dX) - (mGridReference.getWorldX(mX) - getWidth() / 2f)) - 10) / 100 + 1, 2.0f),
                                     Math.min(Math.abs(Math.abs((motionEvent.getRawY() / dScale + dY) - (mGridReference.getWorldY(mY) - getHeight() / 2f)) - 10) / 100 + 1, 2.0f));

                            //And here I update it.
                            setScaleX(getCalculatedScale(-0.2f));
                            setScaleY(getCalculatedScale(-0.2f));

                            invalidate();
                        } else {
                            if(Math.abs((motionEvent.getRawX() / dScale + dX) - (mGridReference.getWorldX(mX) - getWidth()/2f))  <= MIN_MOVING_DISTANCE
                            && Math.abs((motionEvent.getRawY() / dScale + dY) - (mGridReference.getWorldY(mY) - getHeight()/2f)) <= MIN_MOVING_DISTANCE){

                                setX(mGridReference.getWorldX(mX) - getWidth()/2f);                  //todo scale here?
                                setY(mGridReference.getWorldY(mY) - getHeight()/2f);

                            } else {

                                setX(motionEvent.getRawX() / dScale + dX);
                                setY(motionEvent.getRawY() / dScale + dY);

                            }

                            mCover.setX(getX());
                            mCover.setY(getY());

                            mGridReference.updateTouch(getX() + getWidth() / 2f, getY() + getHeight() / 2f); //todo scale here?

                            invalidate();
                        }

                        return true;

                    case MotionEvent.ACTION_DOWN: //On down,
                        //(1)Shrinks,
                        if(scaleAnimator.isStarted()) scaleAnimator.end();
                        scaleAnimator.setFloatValues(getCalculatedScale(0),getCalculatedScale(-0.2f));
                        scaleAnimator.addUpdateListener(onTouchScaleAnimator);
                        scaleAnimator.setInterpolator(onTouchScaleInterpolator);
                        scaleAnimator.start();

                        //(2) Darkens the view,
                        if(colorAnimator.isStarted()) colorAnimator.end();
                        colorAnimator.setIntValues(0,DARKER_COLOR_ADDEND);
                        colorAnimator.addUpdateListener(onTouchColorAnimator);
                        colorAnimator.setDuration(scaleAnimator.getDuration());
                        colorAnimator.setInterpolator(onTouchColorInterpolator);
                        colorAnimator.start();

                        //(3) Updates delta position for moving,
                        dScale = mFolderReference.getZoomableLayout().getScale();
                        dX = getX() - motionEvent.getRawX()/dScale;
                        dY = getY() - motionEvent.getRawY()/dScale;

                        //(4) Updates cover.
                        updateCover();

                        //(5) Retract tag.
                        mTagView.retract(true);

                        isMoving = true;
                        mGridReference.setPreview(true, ((ZoomableScrollView)getParent().getParent()).fooX(), ((ZoomableScrollView)getParent().getParent()).fooY());
                        mGestureDetector.onTouchEvent(motionEvent);
                        return true;

                    case MotionEvent.ACTION_UP: //On up,
                        //(1)Checks if position changed and if so put it on the grid,
                        if(!(getX()+getWidth()/2f == mGridReference.getWorldX(mX)
                           &&getY()+getHeight()/2f== mGridReference.getWorldY(mY))){

                            move(getX()+(getWidth()*getScaleX())/2, getY()+(getHeight()*getScaleY())/2);
                        }

                        //(2)Scales back,
                        if(scaleAnimator.isStarted()) scaleAnimator.end();
                        scaleAnimator.setFloatValues(getCalculatedScale(-0.2f),getCalculatedScale(0));
                        scaleAnimator.addUpdateListener(onTouchScaleAnimator);
                        scaleAnimator.setInterpolator(onTouchScaleInterpolator);
                        scaleAnimator.start();

                        //(3) Gets previous color value,
                        int startingValue = DARKER_COLOR_ADDEND;
                        if(colorAnimator.isStarted()) {
                            startingValue = (int)colorAnimator.getAnimatedValue();
                            colorAnimator.end();
                        }

                        //(4) Colors back,
                        colorAnimator.setIntValues(startingValue,0);
                        colorAnimator.addUpdateListener(onTouchColorAnimator);
                        colorAnimator.setDuration(scaleAnimator.getDuration());
                        colorAnimator.setInterpolator(onTouchColorInterpolator);
                        colorAnimator.start();

                        //(5) Hides cover.
                        mCover.setImageAlpha(0);

                        if(isLongPressed) {isLongPressed = false; save();}
                        isMoving = false;

                        //(6) Un-retract tag.
                        mTagView.retract(false);

                        mGridReference.setPreview(false, ((ZoomableScrollView)getParent().getParent()).fooX(), ((ZoomableScrollView)getParent().getParent()).fooY()); //todo why use dimensions here
                        mGestureDetector.onTouchEvent(motionEvent);
                        return true;
                } return false;
            }
        };
    }

    private ValueAnimator.AnimatorUpdateListener onTouchScaleAnimator = new ValueAnimator.AnimatorUpdateListener() {
        @Override
        public void onAnimationUpdate(ValueAnimator valueAnimator) {
            //Adapts the scale with value. Can't interact with normal scale changes.
            setScaleX((float)valueAnimator.getAnimatedValue());
            setScaleY((float)valueAnimator.getAnimatedValue());

            if(valueAnimator.getAnimatedFraction() == 1.0 && (float)valueAnimator.getAnimatedValue() == getCalculatedScale(-0.2f)) {
                mCover.setImageAlpha(255);
                mCover.setColorFilter(getColorFilter());
            }
        }
    };

    private ValueAnimator.AnimatorUpdateListener onTouchColorAnimator = new ValueAnimator.AnimatorUpdateListener() {
        @Override
        public void onAnimationUpdate(ValueAnimator valueAnimator) {
            //Darkens the current color filter by the current value. Will not go under 0.
            setColorFilter(Color.rgb(Math.max(Color.red(mColor)  -(int)valueAnimator.getAnimatedValue(),0),
                                     Math.max(Color.green(mColor)-(int)valueAnimator.getAnimatedValue(),0),
                                     Math.max(Color.blue(mColor) -(int)valueAnimator.getAnimatedValue(),0)
                        ), PorterDuff.Mode.SRC_IN);
        }
    };

    private ValueAnimator.AnimatorUpdateListener onMovePositionXAnimator = new ValueAnimator.AnimatorUpdateListener() {
        @Override
        public void onAnimationUpdate(ValueAnimator valueAnimator) {
            //Changes X to the new value directly.
            setX((float)valueAnimator.getAnimatedValue());
        }
    };

    private ValueAnimator.AnimatorUpdateListener onMovePositionYAnimator = new ValueAnimator.AnimatorUpdateListener() {
        @Override
        public void onAnimationUpdate(ValueAnimator valueAnimator) {
            //Changes Y to the new value directly.
            setY((float)valueAnimator.getAnimatedValue());
        }
    };

    private void move(float x, float y){ //General moving method, animated, updates mX and mY //TODO LOG
        Log.d("DEBUG", "Moving to " + mGridReference.getGridX(x) + ";" + mGridReference.getGridY(y) + " : " + (mGridReference.getElementAt(mGridReference.getGridX(x),mGridReference.getGridY(y))==null?"no one there.":"someone there."));
        ElementView mTempViewRef = mGridReference.getElementAt(mGridReference.getGridX(x+getFactoredWidth()), mGridReference.getGridY(y+getFactoredHeight()));

        if(mTempViewRef != null){  //Swaps with any element present.
            mTempViewRef.move(mGridReference.getWorldX(mX), mGridReference.getWorldY(mY));
        }

        //Updates position
        mX = mGridReference.getGridX(x+getFactoredWidth());
        mY = mGridReference.getGridY(y+getFactoredHeight());

        //Launches position animations
        posXAnimator.setFloatValues(getX(),mGridReference.getWorldX(mX)-getWidth()/2f);
        posXAnimator.addUpdateListener(onMovePositionXAnimator);
        posXAnimator.setInterpolator(onMovePositionInterpolator);
        posXAnimator.start();

        posYAnimator.setFloatValues(getY(),mGridReference.getWorldY(mY)-getHeight()/2f);
        posYAnimator.addUpdateListener(onMovePositionYAnimator);
        posYAnimator.setInterpolator(onMovePositionInterpolator);
        posYAnimator.setDuration(posXAnimator.getDuration());
        posYAnimator.start();

        save();
    }

    public void updateCover(){
        mCover.setX(getX());
        mCover.setY(getY());
        mCover.setColorFilter(mColor, PorterDuff.Mode.SRC_IN);
        mCover.setScaleX(mScale / mFolderReference.getZoomableLayout().getScaleX());
        mCover.setScaleY(mScale / mFolderReference.getZoomableLayout().getScaleY());
    }

    void defaultState(){}

    void labeledState(){
        defaultState(); //Todo tags
    }

    void containingState(){
        defaultState();
    }

    private class SimpleGestureListener extends GestureDetector.SimpleOnGestureListener{
        @Override
        public boolean onDoubleTap(MotionEvent e) {
            return doubleTap();
        }

        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {
            return singleTap();
        }

        @Override
        public void onLongPress(MotionEvent e) {
            longPress();
        }
    }

    protected boolean singleTap(){
        //Must be overridden.
        return true;
    }

    protected boolean doubleTap(){
        //Can be overridden. //TODO Options
        return true;
    }

    protected void longPress(){
        if( Math.abs(mGridReference.getWorldX(mX) - (getX()+getWidth()/2f))  <= MIN_MOVING_DISTANCE //Todo account for scale?
         && Math.abs(mGridReference.getWorldY(mY) - (getY()+getHeight()/2f)) <= MIN_MOVING_DISTANCE) {
            isLongPressed = true;
            mFolderReference.vibrate(50L);
        }
    }

    @Override
    public void onParentScale(ZoomableScrollView zoomableLayout) {
        setScaleX(mScale / zoomableLayout.getScaleX());
        setScaleY(mScale / zoomableLayout.getScaleY());

        invalidate();
    }

    private float getCalculatedScale(float addend){ //todo should this be used everywhere?
        return (mScale + addend) / mFolderReference.getZoomableLayout().getScaleX();
    }

    private float getFactoredWidth(){
        return getWidth()/4f;
    }

    private float getFactoredHeight(){
        return getHeight()/4f;
    }

    @Override //Adapts scale for +48px resources + keeps cover updated
    public void setScaleX(float scaleX) {
        super.setScaleX(scaleX/2);
        if(mCover != null) mCover.setScaleX(scaleX/2);
    }

    @Override //Adapts scale for +48px resources + keeps cover updated
    public void setScaleY(float scaleY) {
        super.setScaleY(scaleY/2);
        if(mCover != null) mCover.setScaleY(scaleY/2);
    }

    public int getUsableX(){
        return mX;
    }

    public int getUsableY(){
        return mY;
    }

    protected int getReadableX(){
        return -1*(mGridReference.getVerLinesCount()/2 - mX);
    }

    protected int getReadableY(){
        return -1*(mGridReference.getHorLinesCount()/2 - mY);
    }
}


//Old Code (to be Stored)

//CAN MANUALLY CHANGE SCALE (bugged)

//if(getScaleX() - (scaleX/2) > 0.4 && canManuallyChangeScale) {
//            canManuallyChangeScale = false;
//
//            //Smoothly animates great scale changes
//            if(scaleAnimator.isStarted()) scaleAnimator.end();
//            scaleAnimator.setFloatValues(getScaleX(), scaleX/2);
//            scaleAnimator.addUpdateListener(onTouchScaleAnimator);
//            scaleAnimator.setInterpolator(onTouchScaleInterpolator);
//            scaleAnimator.setDuration(5000L);
//            scaleAnimator.start();