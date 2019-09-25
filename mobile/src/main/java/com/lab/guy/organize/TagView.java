package com.lab.guy.organize;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.LayerDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.TouchDelegate;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.AnticipateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputConnectionWrapper;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;

import com.lab.guy.organize.Elements.ElementView;

//todo implement zoom reactive
//TODO Focus idea when long press (allowing copy-pasting, click outside to un-focus)
public class TagView extends android.support.v7.widget.AppCompatEditText implements ZoomReactive {

    private float dX, dY, dRatio, dScale;
    private static int MIN_MOVING_DISTANCE = 5;
    private static int MIN_MASTER_DISTANCE = 100;
    private static int MAX_MASTER_DISTANCE = 500;
    private static int DEFAULT_MASTER_DISTANCE = 300;

    private ValueAnimator scaleAnimator =     new ValueAnimator();
    private ValueAnimator widthAnimator =     new ValueAnimator();
    private ValueAnimator posXAnimator =      new ValueAnimator();
    private ValueAnimator posYAnimator =      new ValueAnimator();
    private ValueAnimator alphaAnimator =     new ValueAnimator();
    private ValueAnimator alphaIconAnimator = new ValueAnimator();

    private AnticipateInterpolator onOvershootPositionInterpolator = new AnticipateInterpolator();
    private AnticipateInterpolator onTouchScaleInterpolator = new AnticipateInterpolator();
    private AccelerateInterpolator onDeleteModeAlphaInterpolator = new AccelerateInterpolator();
    private AccelerateInterpolator onMoveWidthInterpolator = new AccelerateInterpolator();
    private AccelerateInterpolator onMoveAlphaInterpolator = new AccelerateInterpolator();

    private InputMethodManager mInputMethodManager;
    private GestureDetector mGestureDetector;

    private BitmapDrawable tempDeleteIcon;

    private ElementView mMasterView;

    private boolean tempDeleteMode = false;
    private boolean canConfirmTap = false;
    private boolean isMoving = false;
    private boolean isRetracted = false;
    private boolean canChangeColor = false;

    private int mTagColor;
    private int mTagHintColor;
    private int mTagBackgroundColor;
    private int tempDeleteIconAlpha;

    private int initialWidth;

    public TagView(Context context, AttributeSet attrs, int defStyleAttr, ElementView masterView) {
        super(context, attrs, defStyleAttr);
        mMasterView = masterView;
        init();
    }

    private void init(){ //(todo: this is a LOT of listeners)
        //Kicks off touch event handling
        setOnTouchListener(getElementTouchListener());
        setOnFocusChangeListener(new SimpleFocusListener());
        setOnEditorActionListener(new CustomOnEditorActionListener());
        addTextChangedListener(new SimpleTextListener());
        mInputMethodManager = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        mGestureDetector = new GestureDetector(getContext(), new SimpleGestureListener());

        //Saves reference attributes
        mTagColor = getCurrentTextColor();
        mTagHintColor = getCurrentHintTextColor();
        mTagBackgroundColor = getResources().getColor(R.color.tagBackground);

        //Sets up color calculation
        getBackground().setColorFilter(mTagBackgroundColor, PorterDuff.Mode.MULTIPLY);
        tempDeleteIcon = (BitmapDrawable)((LayerDrawable)getBackground()).findDrawableByLayerId(R.id.icon);
        tempDeleteIcon.setAlpha(0); tempDeleteIconAlpha = 0;

        //Sets up necessary attributes
        setCursorVisible(false);
    }

    public void setupHitbox(){ //Thank you to Morris Lin for this solution (https://stackoverflow.com/a/24424933/8387364)
        final View tag = this;
        ((View)tag.getParent()).post(new Runnable() {
            @Override public void run() {
                final Rect newHitbox = new Rect();
                tag.getHitRect(newHitbox);
                newHitbox.top     -= 50;         //This increases the hitbox to render
                newHitbox.bottom  += 50;         //tapping the tags easier.
                ((View)tag.getParent()).setTouchDelegate(new TouchDelegate(newHitbox, tag));
            }
        });

        //TODO MESS HERE
        setPadding((int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 10, getResources().getDisplayMetrics()),0,
                (int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 10, getResources().getDisplayMetrics()),0);
    }


    private View.OnTouchListener getElementTouchListener(){
        return new OnTouchListener() {

            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                performClick();

                switch(motionEvent.getAction()){
                    case MotionEvent.ACTION_MOVE:
                        if (!isMoving) { //When movement is confirmed,
                            if (Math.abs((getX()+getY()) - ((motionEvent.getRawX() / dScale + dX) + (motionEvent.getRawY() / dScale + dY))) > MIN_MOVING_DISTANCE) {
                                //(1) Adapts appropriate values
                                isMoving = true;
                                if (canConfirmTap) canConfirmTap = false;

                                //(2) Turns into a bubble
                                if (!tempDeleteMode) bubble(true);
                                else {
                                    if (alphaIconAnimator.isStarted()) alphaIconAnimator.end();
                                    alphaIconAnimator.setIntValues(tempDeleteIconAlpha, 0);
                                    alphaIconAnimator.addUpdateListener(onDeleteModeAlphaAnimator);
                                    alphaIconAnimator.setInterpolator(onDeleteModeAlphaInterpolator);
                                    alphaIconAnimator.setDuration(150);
                                    alphaIconAnimator.start();

                                    if (alphaAnimator.isStarted()) alphaAnimator.end();
                                    alphaAnimator.setIntValues(255, 0);
                                    alphaAnimator.addUpdateListener(onMoveAlphaAnimator);
                                    alphaAnimator.setInterpolator(onMoveAlphaInterpolator);
                                    alphaAnimator.start();

                                    //Ugly solution but fixes a displacement problem.
                                    dRatio = 0;

                                    tempDeleteMode = false;
                                }
                            }
                        } else {

                            setX(motionEvent.getRawX() / dScale + dX + (initialWidth - getWidth()) * dRatio);
                            setY(motionEvent.getRawY() / dScale + dY);

                            invalidate();

                        } return true;

                    case MotionEvent.ACTION_DOWN: //On down,
                        //(1)Shrinks,
                        if (scaleAnimator.isStarted()) scaleAnimator.end();
                        scaleAnimator.setFloatValues(1, 0.9f);
                        scaleAnimator.addUpdateListener(onTouchScaleAnimator);
                        scaleAnimator.setInterpolator(onTouchScaleInterpolator);
                        scaleAnimator.start();

                        //(2) Updates delta position for moving,
                        //dScale = mFolderReference.getZoomableLayout().getScale(); todo on move
                        dScale = 1.0f;
                        dRatio = motionEvent.getX() / getWidth();
                        dX = getX() - motionEvent.getRawX()/dScale;
                        dY = getY() - motionEvent.getRawY()/dScale;

                        //(3) Adapts appropriate booleans;
                        if(isMoving) isMoving = false;
                        canConfirmTap = true;

                        mGestureDetector.onTouchEvent(motionEvent);
                        return true;

                    case MotionEvent.ACTION_UP: //On up,
                        //(1)Scales back,
                        if (scaleAnimator.isStarted()) scaleAnimator.end();
                        scaleAnimator.setFloatValues(0.9f, 1f);
                        scaleAnimator.addUpdateListener(onTouchScaleAnimator);
                        scaleAnimator.setInterpolator(onTouchScaleInterpolator);
                        scaleAnimator.start();

                        if(isMoving) { //When movement has ended,
                            //(1) Adapts appropriate values todo optimize?
                            boolean revertIntent = false;
                            isMoving = false;

                            float distance = (float)Math.hypot((mMasterView.getX()+mMasterView.getWidth()/2f) - (getX()+getWidth()/2f),
                                                               (mMasterView.getY()+mMasterView.getHeight()/2f) - (getY()+getHeight()/2f));

                            //(2) Updates saved position and caps distance
                            if(distance<=MIN_MASTER_DISTANCE || distance>=MAX_MASTER_DISTANCE){
                                //todo ToDegree ToRadian: Redundancy

                                if (posXAnimator.isStarted()) posXAnimator.end();
                                posXAnimator.setFloatValues(getX(), (mMasterView.getX()+mMasterView.getWidth()/2f) + DEFAULT_MASTER_DISTANCE*(float)Math.cos(Math.toRadians(getAngle())));
                                posXAnimator.addUpdateListener(onOvershootPositionXAnimator);
                                posXAnimator.setInterpolator(onOvershootPositionInterpolator);
                                posXAnimator.start();

                                if (posYAnimator.isStarted()) posYAnimator.end();
                                posYAnimator.setFloatValues(getY(), (mMasterView.getY()+mMasterView.getHeight()/2f) - DEFAULT_MASTER_DISTANCE*(float)Math.sin(Math.toRadians(getAngle())));
                                posYAnimator.addUpdateListener(onOvershootPositionYAnimator);
                                posYAnimator.setInterpolator(onOvershootPositionInterpolator);
                                posYAnimator.setDuration(posXAnimator.getDuration());
                                posYAnimator.start();

                                revertIntent = true;
                            } else {
                                mMasterView.setTagXOffset(getX() - mMasterView.getX());
                                mMasterView.setTagYOffset(getY() - mMasterView.getY());
                            }

                            //(3) Reverts from bubble
                            if(!revertIntent) bubble(false);
                        }

                        mGestureDetector.onTouchEvent(motionEvent);
                        return true;
                } return false;
            }
        };
    }

    public float getAngle() {
        float angle = (float)Math.toDegrees(Math.atan2(-1*((getY()+getHeight()/2f) - (mMasterView.getY()+mMasterView.getHeight()/2f)),
                                                           (getX()+getWidth()/2f) -  (mMasterView.getX()+mMasterView.getWidth()/2f)));

        if(angle < 0){
            angle += 360;
        }

        return angle;
    }

    private ValueAnimator.AnimatorUpdateListener onOvershootPositionXAnimator = new ValueAnimator.AnimatorUpdateListener() {
        @Override
        public void onAnimationUpdate(ValueAnimator valueAnimator) {
            //Changes X to the new value directly.
            setX((float)valueAnimator.getAnimatedValue());
            if(valueAnimator.getAnimatedFraction() == 1.0 && !isRetracted) {
                mMasterView.setTagXOffset(getX() - mMasterView.getX());
                bubble(false);
            }
        }
    };

    private ValueAnimator.AnimatorUpdateListener onOvershootPositionYAnimator = new ValueAnimator.AnimatorUpdateListener() {
        @Override
        public void onAnimationUpdate(ValueAnimator valueAnimator) {
            //Changes Y to the new value directly.
            setY((float)valueAnimator.getAnimatedValue());
            if(valueAnimator.getAnimatedFraction() == 1.0 && !isRetracted) {
                mMasterView.setTagYOffset(getY() - mMasterView.getY());
                bubble(false);
            }
        }
    };

    public void retract(boolean in){
//        isRetracted = in;
//        if(isRetracted)bubble(true); //todo centering issues ?
//
//        if (posXAnimator.isStarted()) posXAnimator.end();
//        posXAnimator.setFloatValues(getX(), (mMasterView.getX()+mMasterView.getWidth()/2f) + (isRetracted?0:mMasterView.getTagXOffset()));
//        posXAnimator.addUpdateListener(onOvershootPositionXAnimator);
//        posXAnimator.setInterpolator(onOvershootPositionInterpolator);
//        posXAnimator.start();
//
//        if (posYAnimator.isStarted()) posYAnimator.end();
//        posYAnimator.setFloatValues(getY(), (mMasterView.getY()+mMasterView.getHeight()/2f) + (isRetracted?0:mMasterView.getTagYOffset()));
//        posYAnimator.addUpdateListener(onOvershootPositionYAnimator);
//        posYAnimator.setInterpolator(onOvershootPositionInterpolator);
//        posYAnimator.setDuration(posXAnimator.getDuration());
//        posYAnimator.start();
    }

    private void bubble(boolean toBubble){
        //(1) Sets necessary variables
        if(toBubble) initialWidth = getWidth();

        //(2) Hides / shows text & icon
        if(alphaAnimator.isStarted()) alphaAnimator.end();
        alphaAnimator.setIntValues(toBubble?255:0, toBubble?0:255);
        alphaAnimator.addUpdateListener(onMoveAlphaAnimator);
        alphaAnimator.setInterpolator(onMoveAlphaInterpolator);
        alphaAnimator.start();

        if(toBubble && tempDeleteMode){
            if(alphaAnimator.isStarted()) alphaAnimator.end(); //No animation so Hint text doesn't show up temporarily
            setTextColor(Color.argb(0, Color.red(mTagColor), Color.green(mTagColor), Color.blue(mTagColor)));
            setHintTextColor(Color.argb(0, Color.red(mTagHintColor), Color.green(mTagHintColor), Color.blue(mTagHintColor)));

            if(alphaIconAnimator.isStarted()) alphaIconAnimator.end();
            alphaIconAnimator.setIntValues(0,255);
            alphaIconAnimator.addUpdateListener(onDeleteModeAlphaAnimator);
            alphaIconAnimator.setInterpolator(onDeleteModeAlphaInterpolator);
            alphaIconAnimator.setDuration(300);
            alphaIconAnimator.start();
        }

        if(!toBubble && (tempDeleteMode || tempDeleteIconAlpha != 0)){
            if(alphaIconAnimator.isStarted()) alphaIconAnimator.end();
            alphaIconAnimator.setIntValues(tempDeleteIconAlpha, 0);
            alphaIconAnimator.addUpdateListener(onDeleteModeAlphaAnimator);
            alphaIconAnimator.setInterpolator(onDeleteModeAlphaInterpolator);
            alphaIconAnimator.setDuration(150);
            alphaIconAnimator.start();
        }

        //(3) Turns into / revert from bubble.
        if(widthAnimator.isStarted()) widthAnimator.end();
        widthAnimator.setIntValues(toBubble?initialWidth:getHeight(), toBubble?getHeight():initialWidth);
        widthAnimator.addUpdateListener(onMoveWidthAnimator);
        widthAnimator.setInterpolator(onMoveWidthInterpolator);
        widthAnimator.start();
    }

    private void hideKeyboardAndCursor(){
        mInputMethodManager.hideSoftInputFromWindow(getWindowToken(), 0);
        setCursorVisible(false);
    }

    public void updateBubbleMode(){
        if(tempDeleteMode){
            tempDeleteMode = false;
            bubble(false);
        }
    }

    @Override //todo this better
    public void onParentScale(ZoomableScrollView zoomableLayout) {
//        setScaleX(1 / zoomableLayout.getScaleX());
//        setScaleY(1 / zoomableLayout.getScaleY());
//
//        invalidate();
    }

    @Override
    public boolean onKeyPreIme(int keyCode, KeyEvent event) {
        if (tempDeleteMode) {
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                hideKeyboardAndCursor();
                updateBubbleMode();
                return true;
            }
        } return false;
    }

    private class SimpleGestureListener extends GestureDetector.SimpleOnGestureListener{
        @Override
        public boolean onDown(MotionEvent e) {
            requestFocus();
            return super.onDown(e);
        }

        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {
            if(canConfirmTap) {
                if(tempDeleteMode) delete();
                else {
                    mInputMethodManager.toggleSoftInput(InputMethodManager.SHOW_FORCED, InputMethodManager.HIDE_IMPLICIT_ONLY);

                    setSelection(Math.round(length() * (e.getX() / getWidth())));
                    setCursorVisible(true);
                }
            } canConfirmTap = false;
            return true;
        }
    }

    private class SimpleTextListener implements TextWatcher{
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }

        @Override
        public void afterTextChanged(Editable s) {
            onTextUpdate(s.toString());
        }
    }

    private class SimpleFocusListener implements OnFocusChangeListener{
        @Override
        public void onFocusChange(View v, boolean hasFocus) {
            if(!hasFocus) {updateBubbleMode();}
        }
    }

    private class CustomOnEditorActionListener implements  OnEditorActionListener{
        @Override
        public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
            if ((actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE ||
                    event != null && event.getAction() == KeyEvent.ACTION_DOWN && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)
                    && event == null || (event != null && !event.isShiftPressed())) {

                hideKeyboardAndCursor();
                updateBubbleMode();

                return true;
            }
            return false;
        }
    }

    private void onTextUpdate(String newText){
        mMasterView.setTagText(newText);

        if(newText.length() == 0 && !tempDeleteMode){
            tempDeleteMode = true;
            bubble(true);

        } else if(newText.length() != 0 && tempDeleteMode){
            tempDeleteMode = false;
            bubble(false);
        }
    }

    public void receivedBackspace(){
        if(tempDeleteMode && getWidth() == getHeight()){
            delete(); //Todo (bug that shouldn't happen in final (going back to initial width, happens after one delete))
            return;
        }

        if(length()==0){
            tempDeleteMode = true;
            bubble(true);
        }
    }

    public void delete(){//(TODO BIG PLACE HOLDER)..?
//        if(alphaAnimator.isStarted()) alphaAnimator.end();
//        alphaAnimator.setIntValues(255,0);
//        alphaAnimator.addUpdateListener(onDeleteAlphaAnimator);
//        alphaAnimator.setInterpolator(onMoveAlphaInterpolator);
//        alphaAnimator.start();

        hideKeyboardAndCursor();

        ((ViewGroup)getParent()).removeView(this);
        setVisibility(View.GONE);
    }

    private ValueAnimator.AnimatorUpdateListener onTouchScaleAnimator = new ValueAnimator.AnimatorUpdateListener() {
        @Override
        public void onAnimationUpdate(ValueAnimator valueAnimator) {
            //Adapts the scale with value. Can't interact with normal scale changes.
            setScaleX((float)valueAnimator.getAnimatedValue());
            setScaleY((float)valueAnimator.getAnimatedValue());
        }
    };

    private ValueAnimator.AnimatorUpdateListener onMoveWidthAnimator = new ValueAnimator.AnimatorUpdateListener() {
        @Override
        public void onAnimationUpdate(ValueAnimator valueAnimator) {
            //Adapts the width. Caution with this, make sure animation has ended before doing anything.
            setWidth((int)valueAnimator.getAnimatedValue());

            //Necessary, as those are set to the new width every time.
            if(valueAnimator.getAnimatedFraction()==1.0 && (int)valueAnimator.getAnimatedValue() == initialWidth && getVisibility()!=View.GONE) {
                tempDeleteMode = false;

                //All of these have to be reset after width change.
                setupHitbox();
                setMaxWidth(1000);
                setMinWidth(0);

            }
        }
    };

    private ValueAnimator.AnimatorUpdateListener onMoveAlphaAnimator = new ValueAnimator.AnimatorUpdateListener() {
        @Override
        public void onAnimationUpdate(ValueAnimator valueAnimator) {
            //Adapts the text's alpha.
            setTextColor(Color.argb((int)valueAnimator.getAnimatedValue(), Color.red(mTagColor), Color.green(mTagColor), Color.blue(mTagColor)));
            setHintTextColor(Color.argb(Math.round(((int)valueAnimator.getAnimatedValue() / 255f) * Color.alpha(mTagHintColor)),
                    Color.red(mTagHintColor), Color.green(mTagHintColor), Color.blue(mTagHintColor)));

            //This also does color, slightly. Don't tell anyone.
            if(!canChangeColor && isMoving) canChangeColor = true;
            if(canChangeColor){
                getBackground().setColorFilter(Color.rgb(
                        Math.round(Color.red(mTagBackgroundColor)   - 10f * ((255 - (int)valueAnimator.getAnimatedValue()) / 255f)),
                        Math.round(Color.green(mTagBackgroundColor) - 10f * ((255 - (int)valueAnimator.getAnimatedValue()) / 255f)),
                        Math.round(Color.blue(mTagBackgroundColor)  - 10f * ((255 - (int)valueAnimator.getAnimatedValue()) / 255f))),
                        PorterDuff.Mode.MULTIPLY);
                if(valueAnimator.getAnimatedFraction() == 1.0 && (int)valueAnimator.getAnimatedValue() == 255) canChangeColor = false;
            }
        }
    };

    private ValueAnimator.AnimatorUpdateListener onDeleteModeAlphaAnimator = new ValueAnimator.AnimatorUpdateListener() {
        @Override //(todo probably other appropriate places to use this? not sure)
        public void onAnimationUpdate(ValueAnimator valueAnimator) {
            tempDeleteIcon.setAlpha((int)valueAnimator.getAnimatedValue());
            tempDeleteIconAlpha = (int)valueAnimator.getAnimatedValue();
        }
    };

    private ValueAnimator.AnimatorUpdateListener onDeleteAlphaAnimator = new ValueAnimator.AnimatorUpdateListener() {
        @Override
        public void onAnimationUpdate(ValueAnimator valueAnimator) {
            //Adapts the box's alpha. //Todo (Temporary)
            setAlpha((float)((int)valueAnimator.getAnimatedValue()));
            setTextColor(Color.argb((int)valueAnimator.getAnimatedValue(), Color.red(mTagColor), Color.green(mTagColor), Color.blue(mTagColor)));
            setHintTextColor(Color.argb(Math.round(((int)valueAnimator.getAnimatedValue() / 255f) * Color.alpha(mTagHintColor)),
                    Color.red(mTagHintColor), Color.green(mTagHintColor), Color.blue(mTagHintColor)));
        }
    };

//    private ValueAnimator.AnimatorUpdateListener xPositionAnimator = new ValueAnimator.AnimatorUpdateListener() {
//        @Override
//        public void onAnimationUpdate(ValueAnimator valueAnimator) {
//            setX((float)valueAnimator.getAnimatedValue());
//        }
//    };
//
//    private ValueAnimator.AnimatorUpdateListener yPositionAnimator = new ValueAnimator.AnimatorUpdateListener() {
//        @Override
//        public void onAnimationUpdate(ValueAnimator valueAnimator) {
//            setY((float)valueAnimator.getAnimatedValue());
//        }
//    };

    //todo figure out after move over // can it dynamically update
//    public void setRawX(float x) {
//        Log.d("DEBUG",String.valueOf((float)parentWidth/2));
//        super.setX(x - (float)parentWidth/2);
//    }

    //The following is to handle backspace and backspace only.
    //Thank you to Idris for this solution (https://stackoverflow.com/a/11377462).
    @Override
    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        return new CustomInputConnection(super.onCreateInputConnection(outAttrs), true);
    }

    private class CustomInputConnection extends InputConnectionWrapper {
        CustomInputConnection(InputConnection target, boolean mutable) {
            super(target, mutable);
        }

        @Override
        public boolean sendKeyEvent(KeyEvent event) {
            if(!widthAnimator.isRunning()) {
                if (event.getAction() == KeyEvent.ACTION_UP && event.getKeyCode() == KeyEvent.KEYCODE_DEL)
                    receivedBackspace();
                return super.sendKeyEvent(event);
            } return false;
        }
    }
}


////////////////////////////////////////////////////////////////////////////////////
////////////////////////        OLD CODE        ////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////

//protected static int widthDiffThreshold = 4;
//
//    protected String rem;
//    protected String add;
//    protected Rect remBounds = new Rect();
//    protected Rect addBounds = new Rect();
//    protected Rect curBounds = new Rect();
//
//    //For context: https://stackoverflow.com/questions/57347015/same-xml-different-behaviour (by me)
//    private class SimpleTextListener implements TextWatcher{
//        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {
//            getPaint().getTextBounds(s.toString(),0,s.length(), curBounds);
//
//            if(((getWidth() - getResources().getDimension(R.dimen.tagPadding)*2) - curBounds.width()) <= widthDiffThreshold){
//                rem = s.subSequence(start, start+count).toString();
//            } else rem = null;
//        }
//        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
//            if(rem!=null) {
//                add = s.subSequence(start, start+count).toString();
//
//                getPaint().getTextBounds(rem,0,rem.length(), remBounds);
//                getPaint().getTextBounds(add,0,add.length(), addBounds);
//
//                if(foo) {
////                    if (positionAnimator.isStarted()) positionAnimator.end();
////                    positionAnimator.setFloatValues(getX(), getX() - (float) (addBounds.width() - remBounds.width()) / 2);
////                    positionAnimator.addUpdateListener(xPositionAnimator);
////                    positionAnimator.setInterpolator(positionInterpolator);
////                    positionAnimator.setDuration(100);
////                    positionAnimator.start();
//
//                    setX((getX() - (float) (addBounds.width() - remBounds.width()) / 2)*1.05f);
//                }
//            }
//        }
//
//        @Override
//        public void afterTextChanged(Editable s) {
//            onTextUpdate(s.toString());
//        }
//    }
//
//    //(temp)
//    boolean foo = false;
//    public void foo(){
//        foo = true;
//    }



//                            float k = 100;
//
//                            float failX = getX()+getWidth()/2f;
//                            float failY = getY()+getHeight()/2f;
//
//                            float centerX = mMasterView.getX() + mMasterView.getWidth()/2f;
//                            float centerY = mMasterView.getY() + mMasterView.getHeight()/2f;
//
//                            float m = (centerX - failX) / (centerY - failY);
//
////                            float ordOrg = failY - m*failX;
//
//                            float newX = (float)((centerX-failX>0)? (centerX+m*centerY-Math.sqrt(Math.pow(m,2)*Math.pow(k,2) - Math.pow(centerX,2)*Math.pow(m,2) + 2*m*centerX*centerY + Math.pow(k,2) - Math.pow(centerY,2)))/1+Math.pow(m,2)
//                                                                  : (centerX+m*centerY+Math.sqrt(Math.pow(m,2)*Math.pow(k,2) - Math.pow(centerX,2)*Math.pow(m,2) + 2*m*centerX*centerY + Math.pow(k,2) - Math.pow(centerY,2)))/1+Math.pow(m,2));
//                            float newY = m*newX;
//
//                            Log.d("DEBUG",String.valueOf(centerX));
////                            Log.d("DEBUG",String.valueOf(centerY));
//
////                            Log.d("DEBUG",String.valueOf((centerX+m*centerY-Math.sqrt(Math.pow(m,2)*Math.pow(k,2) - Math.pow(centerX,2)*Math.pow(m,2) + 2*m*centerX*centerY + Math.pow(k,2) - Math.pow(centerY,2)))));
////                            Log.d("DEBUG",String.valueOf(Math.pow(m,2)*Math.pow(k,2) - Math.pow(centerX,2)*Math.pow(m,2) + 2*m*centerX*centerY + Math.pow(k,2) - Math.pow(centerY,2)));
////                            Log.d("DEBUG",String.valueOf((centerX+m*centerY-Math.sqrt(-1*(Math.pow(m,2)*Math.pow(k,2) - Math.pow(centerX,2)*Math.pow(m,2) + 2*m*centerX*centerY + Math.pow(k,2) - Math.pow(centerY,2))))/1+Math.pow(m,2)));
////                            Log.d("DEBUG",String.valueOf(newY));



////                            if(Math.abs(getX() - mMasterView.getX()) > MAX_MASTER_DISTANCE) {
////                                if (posXAnimator.isStarted()) posXAnimator.end();
////                                posXAnimator.setFloatValues(getX(), (getX() - mMasterView.getX() > 0) ? mMasterView.getX() + MAX_MASTER_DISTANCE: mMasterView.getX() - MAX_MASTER_DISTANCE);
////                                posXAnimator.addUpdateListener(onOvershootPositionXAnimator);
////                                posXAnimator.setInterpolator(onOvershootPositionInterpolator);
////                                posXAnimator.start();
////                                revertIntent = true;
////                            } else mMasterView.setTagXOffset(getX() - mMasterView.getX());
////
////                            if(Math.abs(getY() - mMasterView.getY()) > MAX_MASTER_DISTANCE){
////                                if (posYAnimator.isStarted()) posYAnimator.end();
////                                posYAnimator.setFloatValues(getY(),(getY() - mMasterView.getY() > 0) ? mMasterView.getY() + MAX_MASTER_DISTANCE: mMasterView.getY() - MAX_MASTER_DISTANCE);
////                                posYAnimator.addUpdateListener(onOvershootPositionYAnimator);
////                                posYAnimator.setInterpolator(onOvershootPositionInterpolator);
////                                posYAnimator.setDuration(posXAnimator.getDuration());
////                                posYAnimator.start();
////                                revertIntent = true;
////                            } else mMasterView.setTagYOffset(getY() - mMasterView.getY());