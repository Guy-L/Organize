package com.lab.guy.organize;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Build;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.concurrent.TimeUnit;

public class Loader extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_loader);

        if(shouldAskPermissions()) askPermissions();

        Intent intent = getIntent();
        new LoadTask().execute(intent.getStringExtra("LOADING"),intent.getStringExtra("CALLER"));
    }

    @SuppressLint("StaticFieldLeak")
    private class LoadTask extends AsyncTask<String,Integer,Void>{

        @SuppressWarnings("ResultOfMethodCallIgnored")
        @Override
        protected Void doInBackground(String... strings) {

            //Gets Internal Storage Folder
            String workingDir = getFilesDir().getAbsolutePath();

            //Gets Loading Tree Name & Caller Tree Name
            String loading = strings[0];
            String caller = strings[1];

            //Sets up Readers and Writers
            BufferedReader br;
            FileOutputStream fOut;
            OutputStreamWriter osw;

            //Step 1. (Optional)
            //Checking for Default tree preference.
            //If non-existent, create with "default" value.
            String loadingTree = "default";
            if(loading!=null){loadingTree = loading;}
            else {
                try {
                    File defaultFile = new File(workingDir, "default.dat");
                    if (defaultFile.exists()) {
                        br = new BufferedReader(new FileReader(defaultFile));
                        loadingTree = br.readLine();
                        br.close();
                    } else {
                        fOut = new FileOutputStream(defaultFile.getAbsolutePath());
                        osw = new OutputStreamWriter(fOut);
                        osw.write(loadingTree);
                        osw.close();
                        fOut.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            //Step 2.
            //Checking for loading tree & root data existence.
            //If non-existent, creates with default root data.
            //If root data non-existent, creates default root data.
            File loadedTree = null;
            File rootData = null;
            try {
                loadedTree = new File(workingDir, loadingTree);
                rootData = new File(loadedTree, "root.dat");

                if (loadedTree.exists() && !loadedTree.isDirectory()) loadedTree.delete();
                if (!loadedTree.exists()) loadedTree.mkdir();
                if (!rootData.exists() || rootData.length()==0) {
                    fOut = new FileOutputStream(rootData.getAbsolutePath());
                    osw = new OutputStreamWriter(fOut);
                    osw.write("FOLDER║0║0║0║168║228║255║1.0║   ║DEFAULT║0.0║0.0║CENTER║defaultFolder" + "\n");
                    osw.close();
                    fOut.close();
                }

                //Step 3
                //TODO: Check for inaccessible folders and delete them.

                //Step 4
                //TODO: Load all resources of tree and pass the AssetLoader.

                //Step 5
                //Open the tree's root.
                Intent treeIntent = new Intent(getApplicationContext(), Folder.class);
                treeIntent.putExtra("DATA", rootData);
                TimeUnit.SECONDS.sleep(1); //todo Temporary (Proof of Concept). Later, more important loading be done here (pass string instead of file?)
                startActivity(treeIntent);
            } catch (FileNotFoundException e){
                Log.e("ERROR","Folder or file may be inaccessible. Deleting & retrying.");
                e.printStackTrace();
                if(loadedTree.exists()) {
                    if(rootData.exists()){
                        rootData.delete(); //todo fix this not working
                    } loadedTree.delete();
                }

                Intent retryIntent = new Intent(getApplicationContext(), Loader.class);
                retryIntent.putExtra("LOADING", loadedTree.getName());
                retryIntent.putExtra("CALLER", caller);
                startActivity(retryIntent);
            } catch (IOException e) {
                Log.e("ERROR","Cancelling loading.");
                e.printStackTrace();
                if(caller!=null) {
                    Intent cancelIntent = new Intent(getApplicationContext(), Loader.class);
                    cancelIntent.putExtra("LOADING", caller);
                    cancelIntent.putExtra("CALLER", loadingTree);
                    startActivity(cancelIntent);
                } else startActivity(new Intent(getApplicationContext(),Loader.class));
            } catch (InterruptedException e) {e.printStackTrace();}

            return null;
        }
    }

    //Courtesy of Piroxiljin (https://stackoverflow.com/a/40277322/8387364)
    protected boolean shouldAskPermissions() {
        return (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP_MR1);
    }

    @TargetApi(23)
    protected void askPermissions() {
        String[] permissions = {
                "android.permission.READ_EXTERNAL_STORAGE",
                "android.permission.WRITE_EXTERNAL_STORAGE"
        };
        int requestCode = 200;
        requestPermissions(permissions, requestCode);
    }
}
















///////////////////////////////////
////////// OLD AND DEBUG //////////
///////////////////////////////////

//Bundle bundle = ActivityOptionsCompat.makeCustomAnimation(thisReference.getApplicationContext(), android.R.anim.fade_in, android.R.anim.fade_out).toBundle();

//    final Loader thisReference = this;
//
//    Timer timer = new Timer();
//        timer.schedule(new TimerTask() {
//            @Override
//            public void run() {
//                Intent defaultIntent = new Intent(thisReference,Folder.class);
//                thisReference.startActivity(defaultIntent);
//            }
//        },5*1000);

//Step 3.
//Go through each listed folders in Data.
//If matching folder data doesn't exist and ID isn't root, delete from list.
//If matching folder data doesn't exist and ID is root, add with one child.

//Step 4.
//Go through each folder data file in Tree.
//If it isn't listed in Data and isn't tree.dat, add it to the list.
