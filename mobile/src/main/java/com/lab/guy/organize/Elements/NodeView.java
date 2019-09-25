package com.lab.guy.organize.Elements;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.widget.ImageView;

import com.lab.guy.organize.Folder;
import com.lab.guy.organize.R;
import com.lab.guy.organize.TagView;

@SuppressLint("ViewConstructor")
public class NodeView extends ElementView {

    public enum State{DEFAULT, LABELED, CONTAINING, LABEL}

    State mState;

    public NodeView(Folder folder, int id, int x, int y, int color, float scale, String tag, State state, float tagXOffset, float tagYOffset, TagDisplayMode tagDisplayMode){
        super(folder, R.drawable.node96, id, x, y, color, scale, tag, tagXOffset, tagYOffset, tagDisplayMode);

        setState(state);
    }

    @Override
    public void supplyAuxiliaryViews(TagView tagView, ImageView cover) {
        super.supplyAuxiliaryViews(tagView, cover);
        mCover.setImageResource(R.drawable.node96);
    }

    public void setState(State state){
        switch(state){
            case DEFAULT:    defaultState(); break;
            case LABELED:    labeledState(); break;
            case CONTAINING: containingState(); break;
            case LABEL: //todo CUSTOM STATE
                break;
        }

        mState = state;
        save();
    }

    @Override
    protected boolean singleTap() {
        switch(mState){
            case DEFAULT:    setState(State.LABELED); break;
            case LABELED:    setState(State.CONTAINING); break;
            case CONTAINING: setState(State.LABEL); break;
            case LABEL:      setState(State.DEFAULT); break;
        } return super.singleTap();
    }

    @Override
    protected Folder save() {
        super.save().update(mID, getReadableX(), getReadableY(), Color.red(mColor), Color.green(mColor), Color.blue(mColor), mScale, mTagText, mState, mTagXOffset, mTagYOffset, mTagDisplayMode);
        return null;
    }
}
