package com.sevis.photos.autoupload

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val prefs = context.getSharedPreferences("photos_prefs", Context.MODE_PRIVATE)
            val autoUploadEnabled = prefs.getBoolean("auto_upload_enabled", false)
            val token = prefs.getString("token", null)

            if (autoUploadEnabled && token != null) {
                AutoUploadScheduler.schedule(context)
            }
        }
    }
}
