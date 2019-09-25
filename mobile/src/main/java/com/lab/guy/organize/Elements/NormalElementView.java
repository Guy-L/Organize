package com.lab.guy.organize.Elements;

import com.lab.guy.organize.Folder;

public abstract class NormalElementView extends ElementView {
    //This relay abstract class is necessary for the only purpose of allowing edge cases,
    //i.e. Node, Object and Super elements to handle states and setState interpretation on their own.
    //Every other element will be a child of this class and handle tags in a similar manner.

    public enum State{DEFAULT, LABELED, CONTAINING}
    State mState;

    public NormalElementView(Folder folder, int imageResource, int id, int x, int y, int color, float scale, String tag, State state, float tagXOffset, float tagYOffset, TagDisplayMode tagDisplayMode) {
        super(folder, imageResource, id, x, y, color, scale, tag, tagXOffset, tagYOffset, tagDisplayMode);

        mState = state;
    }

    public void setState(State state){
        switch(state){
            case DEFAULT:    defaultState(); break;
            case LABELED:    labeledState(); break;
            case CONTAINING: containingState(); break;
        }

        mState = state;
        save();
    }
}
