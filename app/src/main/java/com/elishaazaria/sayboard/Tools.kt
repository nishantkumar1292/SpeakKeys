package com.elishaazaria.sayboard

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat

object Tools {
    @JvmStatic
    fun isMicrophonePermissionGranted(activity: Activity): Boolean {
        val permissionCheck = ContextCompat.checkSelfPermission(
            activity.applicationContext,
            Manifest.permission.RECORD_AUDIO
        )
        return permissionCheck == PackageManager.PERMISSION_GRANTED
    }

    @JvmStatic
    fun isIMEEnabled(activity: Activity): Boolean {
        val imeManager =
            activity.applicationContext.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        for (i in imeManager.enabledInputMethodList) {
            if (i.packageName == activity.packageName) {
                return true
            }
        }
        return false
    }
}
