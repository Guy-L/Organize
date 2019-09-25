package com.lab.guy.organize;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Point;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.support.constraint.ConstraintLayout;
import android.support.constraint.ConstraintSet;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.Display;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;

import com.lab.guy.organize.Elements.ElementView;
import com.lab.guy.organize.Elements.FolderView;
import com.lab.guy.organize.Elements.NodeView;
import com.lab.guy.organize.Utils.ViewIdGenerator;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class Folder extends AppCompatActivity {

    Vibrator mVibrator;
    ZoomableScrollView mScroller;
    ConstraintLayout mLayout;
    ConstraintGrid mGrid;
    File mData;

    StringBuilder dataTemp;

    int maxID = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //Retrieves data
        mData = (File)getIntent().getSerializableExtra("DATA");

        //Returns to default tree if no data is given.
        //May result in an infinite loop if something really weird happens.
        if(mData==null){
            Log.e("ERROR","Cancelling opening (no file).");
            startActivity(new Intent(getApplicationContext(),Loader.class));
        } else {
            if(!mData.exists()){
                Log.e("ERROR","Cancelling opening (data doesn't exist).");
                cancelOpening(mData);
            }
        }

        //Folder should have all it needs to start up, load in the layout.
        setContentView(R.layout.activity_folder);

        //Assures editing area's size is 5x greater than the screen's.
        //Using pixels since it seems the easiest setter (layoutParams.width/height) requires them.
        mScroller = findViewById(R.id.scroller);
        mLayout = findViewById(R.id.main);
        Display display = getWindowManager().getDefaultDisplay();
        Point size = new Point(); display.getSize(size);
        ViewGroup.LayoutParams mainParams = mLayout.getLayoutParams();
        mainParams.width  = size.x*5;
        mainParams.height = size.y*5;

        mLayout.setLayoutParams(mainParams);

        //Starts up mGrid helper class instance and adjusts screen position accordingly.
        mGrid = new ConstraintGrid(mLayout,size.x,size.y);
        mScroller.passInitialPosition(mGrid.getWorldX(mGrid.getVerLinesCount()/2)-size.x/2f,
                                     mGrid.getWorldY(mGrid.getHorLinesCount()/2)-size.y/2f + getStatusBarHeight()/2f);

        //Starts up relayer for certain touch events
        mScroller.passTapRelayer(new ActionRelayer());

        //Revvs up vibrator
        mVibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        //Sets up data processing
        try {
            BufferedReader br = new BufferedReader(new FileReader(mData));
            dataTemp = new StringBuilder(); String line;
            while (!(((line = br.readLine()) == null ? "" : line).isEmpty())) {
                dataTemp.append(line).append("\n");
            } br.close();
        } catch(IOException e){
            e.printStackTrace();
            Log.e("ERROR","Cancelling opening (error parsing data).");
            cancelOpening(mData);
        }

        //Reads data and calculates latest ID
        String[] elements = dataTemp.toString().split("\n");
        String[] params;

        for(String element : elements){
            params = element.split("║");

            try{
                if(Integer.parseInt(params[1])>maxID) maxID = Integer.parseInt(params[1]);
            } catch(Exception e) {
                if(!params[0].equals("SUPER")) {
                    e.printStackTrace();
                    Log.w("ERROR", "Error while reading element ID");
                    //todo Cancel opening..?
                }
            }
        }

        save();

        //Uses data and places every known elements
        for(String element : elements){
            params = element.split("║");

            switch(params[0]) {
                case "FOLDER":
                    addView(new FolderView(this,Integer.parseInt(params[1]),                  //ID
                                                     Integer.parseInt(params[2]),                     //X
                                                     Integer.parseInt(params[3]),                     //Y
                                                     android.graphics.Color.rgb(                      //Color
                                                             Integer.parseInt(params[4]),             //RGB R
                                                             Integer.parseInt(params[5]),             //RGB G
                                                             Integer.parseInt(params[6])),            //RGB B
                                                     Float.parseFloat(params[7]),                     //Scale
                                                     params[8],                                       //Tag
                                                     FolderView.State.valueOf(params[9]),             //State
                                                     Float.parseFloat(params[10]),                    //TagXOffset
                                                     Float.parseFloat(params[11]),                    //TagYOffset
                                                     ElementView.TagDisplayMode.valueOf(params[12]),  //TagDisplayMode
                                                     params[13]));                                    //Data Reference
                    break;

                case "NODE":
                    addView(new NodeView(this,Integer.parseInt(params[1]),                    //ID
                                                      Integer.parseInt(params[2]),                     //X
                                                      Integer.parseInt(params[3]),                     //Y
                                                      android.graphics.Color.rgb(                      //Color
                                                              Integer.parseInt(params[4]),             //RGB R
                                                              Integer.parseInt(params[5]),             //RGB G
                                                              Integer.parseInt(params[6])),            //RGB B
                                                      Float.parseFloat(params[7]),                     //Scale
                                                      params[8],                                       //Tag
                                                      NodeView.State.valueOf(params[9]),               //State
                                                      Float.parseFloat(params[10]),                    //TagXOffset
                                                      Float.parseFloat(params[11]),                    //TagYOffset
                                                      ElementView.TagDisplayMode.valueOf(params[12])));//TagDisplayMode
                    break;

                case "SUPER": break;

                default: break; //Line will be ignored if element isn't recognized. //todo should it?
            }
        }

        //Supplies tags and covers to all elements
        for(int index = 0; index< mLayout.getChildCount(); ++index) {
            if(mLayout.getChildAt(index) instanceof ElementView){

                //todo is this necessary now after preview fix?
                ImageView newCover = new ImageView(this);
                newCover.setId(ViewIdGenerator.generateViewId());
                addView(newCover);

                TagView newTag = new TagView(new ContextThemeWrapper(getApplicationContext(), R.style.TagStyle), null, 0, (ElementView) mLayout.getChildAt(index));
                addTagView(newTag);

                ((ElementView) mLayout.getChildAt(index)).supplyAuxiliaryViews(newTag, newCover);
            }
        }
    }

    private void cancelOpening(File data){
        //Return to parent. If parent doesn't exist, return to default.
        if(data.getParentFile().exists()&&data.getParentFile().isDirectory()){
            Intent cancelIntent = new Intent(getApplicationContext(),Loader.class);
            cancelIntent.putExtra("LOADING", data.getParentFile().getName());
            cancelIntent.putExtra("CALLER", data.getParentFile().getName());
            startActivity(cancelIntent);
        } else startActivity(new Intent(getApplicationContext(),Loader.class));
    }

    private void addView(View view){ //Thank you to Navneeth G for this incredibly clever solution (https://stackoverflow.com/a/11656129)
        // Set up touch listener for non-text box views to hide keyboard.
        if (!(view instanceof EditText)) {
            if(view instanceof ElementView){
//                ((ComposeTouch) view).getCompositeTouchListener().registerListener(new View.OnTouchListener() {
//                    public boolean onTouch(View v, MotionEvent event) {
//                        v.performClick();
//                        if (getCurrentFocus() instanceof TagView)
//                            ((TagView) getCurrentFocus()).updateBubbleMode();
//                        hideSoftKeyboardAndCursor();
//                        return false;
//                    }
//                });
            } else {
                view.setOnTouchListener(new View.OnTouchListener() {
                    public boolean onTouch(View v, MotionEvent event) {
                        v.performClick();
                        if (getCurrentFocus() instanceof TagView)
                            ((TagView) getCurrentFocus()).updateBubbleMode();
                        hideSoftKeyboardAndCursor();
                        return false;
                    }
                });
            }
        }

        //If a layout container, iterate over children and seed recursion.
        if (view instanceof ViewGroup) {
            for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
                View innerView = ((ViewGroup) view).getChildAt(i);
                addView(innerView);
            }
        }

        mLayout.addView(view);
    }

    private void hideSoftKeyboardAndCursor() {
        if(getCurrentFocus() != null) {
            InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(Activity.INPUT_METHOD_SERVICE);
            inputMethodManager.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
        }

        for(int i = 0; i < mLayout.getChildCount(); i++){
            if(mLayout.getChildAt(i) instanceof  EditText){
                ((EditText) mLayout.getChildAt(i)).setCursorVisible(false); //todo untested code sample here
            }
        }
    }

    public void update(int id, Object... values){
        String targetElement = null;

        //Reads data and finds target ID.
        String[] elements = dataTemp.toString().split("\n");
        String[] params = null;

        for(String element : elements){
            params = element.split("║");

            try{
                if(Integer.parseInt(params[1])==id) {targetElement = element; break;}
            } catch(Exception e) {
                if(!params[0].equals("SUPER")) { //todo ensure this doesn't mess up init
                    e.printStackTrace();
                    Log.e("ERROR", "Error while reading element ID");
                    return;
                }
            }
        }

        if(targetElement == null) return; //todo look into this (why allow in init, will it work...)
        if(params.length != values.length+2) return;

        try {
            StringBuilder saveDataTemp = new StringBuilder();
            saveDataTemp.append(params[0]).append('║').append(id);

            int index = -1;
            for (Object value : values) { index++; //todo find a test to check if value type is expected/string-safe (using params)(if not, ClassCastException)
                saveDataTemp.append('║');
                if (value instanceof Float) value = truncate((float) value, 3);
                if (value.toString().equals(params[index + 2])) {saveDataTemp.append(params[index+2]); continue;}

                saveDataTemp.append(value);
            }

            if(params.length != saveDataTemp.toString().split("║").length) throw new UnknownError();

            if(targetElement.equals(saveDataTemp.toString())) return; //todo New thing. check if it doesn't break anything. shouldn't.

            Log.d("DEBUG","CHANGING: " + String.valueOf(targetElement));
            Log.d("DEBUG","WITH: " + String.valueOf(saveDataTemp.toString()));

            dataTemp.replace(0, dataTemp.length(), dataTemp.toString().replace(targetElement, saveDataTemp.toString()));

            save();

        } catch(Exception e){
            e.printStackTrace();
        }
    }

    private void save(){
        Log.i("DEBUG","Saving...");

        try {
            FileWriter writer = new FileWriter(mData,false);
            writer.write(dataTemp.toString());
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
            Log.w("CRITICAL ERROR", "Error while saving.");
        }
    }

    private ConstraintSet mConstraints = new ConstraintSet();

    //TODO Might delete
    //For context: https://stackoverflow.com/questions/57347015/same-xml-different-behaviour (by me)
    private void addTagView(TagView tag){
        tag.setId(ViewIdGenerator.generateViewId()); //if function form is kept, remove extra class
        mLayout.addView(tag);

        tag.setupHitbox(); //todo might have a better place to put this in!!

        mConstraints.clone(mLayout);

        mConstraints.connect(tag.getId(), ConstraintSet.START, mLayout.getId(), ConstraintSet.START);
        mConstraints.connect(tag.getId(), ConstraintSet.END, mLayout.getId(), ConstraintSet.END);

        mConstraints.applyTo(mLayout);

        //Terrible code here to fix a weird scaling issue. Might be the source of more problems. Todo investigate.
        for(int i = 0; i < mLayout.getChildCount(); i++){
            if(mLayout.getChildAt(i) instanceof ZoomReactive){
                ((ZoomReactive) mLayout.getChildAt(i)).onParentScale(mScroller);
            }
        }
    }

    public ConstraintGrid getGrid(){
        //Used for child positioning (handled by child).
        return mGrid;
    }

    void onTap(float x, float y) {
        //On single tap in empty space, bring up selector
        Log.i("FOLDER INFO", "Tap! (" + String.valueOf(x) + " " + String.valueOf(y) + ")");
    }

    void onLongTap(float x, float y){
        //On long tap in empty space, go back to center
        Log.i("FOLDER INFO","Long tap! (" + String.valueOf(x) + " " + String.valueOf(y) + ")");
    }

    class ActionRelayer{
        ActionRelayer(){}

        void singleTap(float x, float y) {onTap(x,y);}
        void longTap(float x, float y){onLongTap(x,y);}
        void onScale(){
            for(int index = 0; index< mLayout.getChildCount(); ++index) {
                if(mLayout.getChildAt(index) instanceof ZoomReactive) ((ZoomReactive) mLayout.getChildAt(index)).onParentScale(mScroller);
            }
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        if(!hasFocus) hideSoftKeyboardAndCursor();
        super.onWindowFocusChanged(hasFocus);
    } //todo usefulness check

    public ZoomableScrollView getZoomableLayout(){
        return mScroller; //todo All usages are to optain the scale of the layout. There has to be a better way to get this data!
    }

    public int getStatusBarHeight() { //Thank you to Jorgesys for this solution (https://stackoverflow.com/a/3356263/8387364).
        int result = 0;
        int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) result = getResources().getDimensionPixelSize(resourceId);

        return result;
    }

    public void vibrate(long duration){ //Thank you to Paresh Mayani for this solution (https://stackoverflow.com/a/13950364/8387364)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mVibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            //deprecated in API 26
            mVibrator.vibrate(duration);
        }
    }

    public float truncate(float number, int factor){
        int aux = (int)(number*Math.pow(10, factor));
        return (float)(aux/Math.pow(10, factor));
    }
}