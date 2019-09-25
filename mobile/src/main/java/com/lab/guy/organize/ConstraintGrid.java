package com.lab.guy.organize;

import android.animation.ValueAnimator;
import android.support.constraint.ConstraintLayout;
import android.view.animation.AnticipateOvershootInterpolator;
import com.lab.guy.organize.Elements.ElementView;
import com.lab.guy.organize.Utils.ViewIdGenerator;

import java.util.ArrayList;

//Helper class for working with simple integer grid positions in constraint layout.

//Originally intended to use GuideLines (thus the ConstraintLayout), but that was unsatisfactory.
//I'm keeping it this way in case it does end up being useful (and as long as it isn't restrictive in any way).
public class ConstraintGrid {
    private ConstraintLayout mLayout;

    private float mScreenWidth;
    private float mScreenHeight;

    private PreView mLastSelected;
    private float mLastTouchX;
    private float mLastTouchY;

    private ArrayList<Float> horLines = new ArrayList<>();
    private ArrayList<Float> verLines = new ArrayList<>();

    private ArrayList<PreView> preViews = new ArrayList<>();
    private ValueAnimator scaleAnimator = new ValueAnimator();
    private ValueAnimator alphaAnimator = new ValueAnimator();
    private boolean isShown = false;

    private static float UNFOUND_VALUE = -1f;

    //todo custom density
    @SuppressWarnings("FieldCanBeLocal")
    private static int HOR_INCREMENT = 5;
    @SuppressWarnings("FieldCanBeLocal")
    private static int VER_INCREMENT = 7;
    @SuppressWarnings("FieldCanBeLocal")
    private static int PREVIEW_EXCESS = 3;

    ConstraintGrid(ConstraintLayout layout, float screenWidth, float screenHeight){
        int o = layout.getResources().getConfiguration().orientation;
        mLayout = layout;
        mScreenWidth = screenWidth;
        mScreenHeight = screenHeight;

        for(float xi = 0; xi <= layout.getLayoutParams().width ; xi += screenWidth/(o==1? HOR_INCREMENT:VER_INCREMENT)){
            verLines.add(xi);
        }

        for(float yi = 0; yi <= layout.getLayoutParams().height ; yi += screenHeight/(o==1? VER_INCREMENT:HOR_INCREMENT)) {
            horLines.add(yi);
        }

        int maxPreviews = (getGridX(screenWidth  * (2 - ((ZoomableScrollView)mLayout.getParent()).getMinScale())) + PREVIEW_EXCESS)       //7+2 (temp)
                        * (getGridY(screenHeight * (2 - ((ZoomableScrollView)mLayout.getParent()).getMinScale())) + PREVIEW_EXCESS);      //10+2 (temp todo del)

        RootPreView rootPre = new RootPreView(layout.getContext());
        rootPre.setId(ViewIdGenerator.generateViewId());
        preViews.add(rootPre);
        layout.addView(rootPre, new ConstraintLayout.LayoutParams(ConstraintLayout.LayoutParams.WRAP_CONTENT, ConstraintLayout.LayoutParams.WRAP_CONTENT));
        rootPre.setup(getWorldX(getVerLinesCount()/2), getWorldY(getHorLinesCount()/2), ((ZoomableScrollView)mLayout.getParent()).getScale());

        for(int i = 1; i < maxPreviews+1; i++) {
                final PreView preview = new PreView(layout.getContext());
                preview.setId(ViewIdGenerator.generateViewId());
                preViews.add(preview);
                layout.addView(preview, new ConstraintLayout.LayoutParams(ConstraintLayout.LayoutParams.WRAP_CONTENT, ConstraintLayout.LayoutParams.WRAP_CONTENT));
        }
    }

    public int getGridX(float x){
        float closestLine = UNFOUND_VALUE;
        for(float verLine : verLines) {
            if (Math.abs(x - verLine) < Math.abs(x - closestLine)) closestLine = verLine;
        } return closestLine==UNFOUND_VALUE? 0 : verLines.indexOf(closestLine);
    }

    public int getGridY(float y){
        float closestLine = UNFOUND_VALUE;
        for(float horLine : horLines) {
            if (Math.abs(y - horLine) < Math.abs(y - closestLine)) closestLine = horLine;
        } return closestLine==UNFOUND_VALUE? 0 : horLines.indexOf(closestLine);
    }

    public float getWorldX(int x){
        return verLines.get(x);
    }

    public float getWorldY(int y){
        return horLines.get(y);
    }

    public int getHorLinesCount(){
        return horLines.size();
    }

    public int getVerLinesCount(){
        return verLines.size();
    }

    public void setPreview(boolean shown, float originX, float originY){
        if(shown==isShown) return;
        if(!shown && mLastSelected != null) mLastSelected.deselect();

        isShown = shown;

        float screenScale = ((ZoomableScrollView)mLayout.getParent()).getScale();

        if(shown){ //Brings visible previews & updates their scales
            //todo correctly fix origin displacement to reduce preview count, remove excess

//            Log.d("DEBUG",String.valueOf(getGridX(originX + mScreenWidth * (2-screenScale)) - getGridX(originX)));
//            Log.d("DEBUG",String.valueOf(getGridY(originY + mScreenHeight * (2-screenScale)) - getGridY(originY)));

            int i = 1;
            for(int x = getGridX(originX)-1; x < getGridX(originX + mScreenWidth * (2-screenScale))+1; x++){
                for(int y = getGridY(originY)-1; y < getGridY(originY + mScreenHeight * (2-screenScale))+1; y++) {
                    if(x==getVerLinesCount()/2 && y==getHorLinesCount()/2){continue;}
                    preViews.get(i).setup(getWorldX(Math.abs(x)), getWorldY(Math.abs(y)), screenScale);
                    i++;
                }
            }
        }

        //Scales from nothing or to nothing
        scaleAnimator.setFloatValues(shown?0f:0.5f/screenScale, shown?0.5f/screenScale:0);
        scaleAnimator.addUpdateListener(scaleAnimatorUpdateListener);
        scaleAnimator.setInterpolator(anticipateOvershootInterpolator);
        scaleAnimator.start();

        //Fades in or out at the same time
        alphaAnimator.setFloatValues(shown?0f:1f, shown?1f:0f);
        alphaAnimator.addUpdateListener(alphaAnimatorUpdateListener);
        alphaAnimator.setDuration(scaleAnimator.getDuration());
        alphaAnimator.start();
    }

    private AnticipateOvershootInterpolator anticipateOvershootInterpolator = new AnticipateOvershootInterpolator();

    private ValueAnimator.AnimatorUpdateListener scaleAnimatorUpdateListener = new ValueAnimator.AnimatorUpdateListener() {
        @Override
        public void onAnimationUpdate(ValueAnimator valueAnimator) {
            for(PreView view : preViews) {
                if(preViews.indexOf(view) != 0) {
                    view.setScaleX((float) valueAnimator.getAnimatedValue());
                    view.setScaleY((float) valueAnimator.getAnimatedValue());
                }
            }
        }
    };

    private ValueAnimator.AnimatorUpdateListener alphaAnimatorUpdateListener = new ValueAnimator.AnimatorUpdateListener() {
        @Override
        public void onAnimationUpdate(ValueAnimator valueAnimator) {
            for(PreView view : preViews) {
                if(preViews.indexOf(view) != 0) {
                    view.setAlpha((float) valueAnimator.getAnimatedValue());
                }
            }
        }
    };

    public void updateTouch(float x, float y){
        if(Math.abs(x-mLastTouchX)>5 || Math.abs(y-mLastTouchY)>5){
            if(getGridX(mLastTouchX) != getGridX(x) || getGridY(mLastTouchY) != getGridY(y)){

                if(preViews.size()>0) {
                    for (PreView view : preViews) {
                        if (view != null) {                                              //Todo assess necessity of this line
                            if (view.getX() == getWorldX(getGridX(x)) &&
                                    view.getY() == getWorldY(getGridY(y))) {
                                if (mLastSelected != null) mLastSelected.deselect();
                                mLastSelected = view;
                                view.select();
                            }
                        }
                    }
                }
            }

            mLastTouchX = x;
            mLastTouchY = y;
        }
    }

    public ElementView getElementAt(int x, int y){
        for(int index = 0; index < mLayout.getChildCount(); ++index){

            if(mLayout.getChildAt(index) instanceof ElementView){
                if(!((ElementView) mLayout.getChildAt(index)).isMoving){
                    if(((ElementView) mLayout.getChildAt(index)).getUsableX() == x && //todo If getGrid fix works, remove getUsables
                       ((ElementView) mLayout.getChildAt(index)).getUsableY() == y){
                        return (ElementView) mLayout.getChildAt(index);
                    }
                }
            }
        } return null;
    }
}

/////////////////////////////////////////////////////////////////////
////////////////////////   OLD CODE   ///////////////////////////////
/////////////////////////////////////////////////////////////////////

//for(int xj = 0; xj < verLines.size(); xj++) {
//            for(int yj = 0; yj < horLines.size(); yj++) {
//                final PreView preview = new PreView(layout.getContext());
//                layout.addView(preview, new ConstraintLayout.LayoutParams(ConstraintLayout.LayoutParams.WRAP_CONTENT, ConstraintLayout.LayoutParams.WRAP_CONTENT));
//                preview.setPosition(verLines.get(xj),horLines.get(yj));
//            }
//        }

//if(shown){
//Fills the array and updates scales for concerned views
//            for (int index = 0; index < mLayout.getChildCount(); ++index) {
//                if (mLayout.getChildAt(index) instanceof PreView) {
//                    PreView child = (PreView) mLayout.getChildAt(index);
//
//                    if (child.getX() + child.getRight() > originX - 50 && child.getX() < originX + (mScreenWidth + 50) * (2-screenScale) //todo or divide??
//                     && child.getY() + child.getBottom() > originY - 50 && child.getY() < originY + (mScreenHeight + 50) * (2-screenScale)) {
//
//                        preViews.add(child);
//
//                        child.setScaleX(1/screenScale);
//                        child.setScaleY(1/screenScale);
//                        child.invalidate();
//                    }
//                }
//            }
//        }

//if(shown && preViews.size()!=0)
//if(valueAnimator.getAnimatedFraction()==1.0f && (float)valueAnimator.getAnimatedValue()==0.0f) preViews.clear();