package com.lab.guy.organize.Utils;

//Util Class made by Fantouch (thank you very much!)
//https://stackoverflow.com/questions/6790623/programmatic-views-how-to-set-unique-ids

import android.annotation.SuppressLint;
import android.os.Build;
import android.view.View;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * =============
 * <p>
 * while {@link View#generateViewId()} require API Level >= 17, this tool is compatible with all API.
 * <p>
 * according to current API Level, it decide weather using system API or not.<br>
 * so you can use {@link ViewIdGenerator#generateViewId()} and {@link View#generateViewId()} in the
 * same time and don't worry about getting same id
 *
 * @author fantouchx@gmail.com
 */
public class ViewIdGenerator {
    private static final AtomicInteger sNextGeneratedId = new AtomicInteger(1);

    @SuppressLint("NewApi")
    public static int generateViewId() {

        if (Build.VERSION.SDK_INT < 17) {
            for (;;) {
                final int result = sNextGeneratedId.get();
                // aapt-generated IDs have the high byte nonzero; clamp to the range under that.
                int newValue = result + 1;
                if (newValue > 0x00FFFFFF)
                    newValue = 1; // Roll over to 1, not 0.
                if (sNextGeneratedId.compareAndSet(result, newValue)) {
                    return result;
                }
            }
        } else {
            return View.generateViewId();
        }

    }
}