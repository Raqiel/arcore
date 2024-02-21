package com.example.arcore.common.helpers

import android.app.Activity
import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.display.DisplayManager
import android.view.Display
import android.view.Surface
import android.view.View
import android.view.WindowManager
import com.google.ar.core.Session


object FullScreenHelper {

    /**
     * Sets the Android fullscreen flags. Expected to be called from [ ][Activity.onWindowFocusChanged].
     *
     * @param activity the Activity on which the full screen mode will be set.
     * @param hasFocus the hasFocus flag passed from the [Activity.onWindowFocusChanged] callback.
     */
    fun setFullScreenOnWindowFocusChanged(
        activity: Activity,
        hasFocus: Boolean
    ) {
        if (hasFocus) {
            activity
                .window
                .decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        }
    }

}