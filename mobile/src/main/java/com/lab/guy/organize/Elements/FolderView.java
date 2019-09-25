package com.lab.guy.organize.Elements;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.widget.ImageView;
import com.lab.guy.organize.Folder;
import com.lab.guy.organize.R;
import com.lab.guy.organize.TagView;

@SuppressLint("ViewConstructor")
public class FolderView extends NormalElementView {

    String mDataReference;

    public FolderView(Folder folder, int id, int x, int y, int color, float scale, String tag, State state, float tagXOffset, float tagYOffset, TagDisplayMode tagDisplayMode, String dataReference){
        super(folder, R.drawable.folder96, id, x, y, color, scale, tag, state, tagXOffset, tagYOffset, tagDisplayMode);

        mDataReference = dataReference;
    }

    @Override
    public void supplyAuxiliaryViews(TagView tagView, ImageView cover) {
        super.supplyAuxiliaryViews(tagView, cover);
        mCover.setImageResource(R.drawable.folder96);
    }

    @Override
    protected Folder save() {
        super.save().update(mID, getReadableX(), getReadableY(), Color.red(mColor), Color.green(mColor), Color.blue(mColor), mScale, mTagText, mState, mTagXOffset, mTagYOffset, mTagDisplayMode, mDataReference);
        return null;
    }
}
