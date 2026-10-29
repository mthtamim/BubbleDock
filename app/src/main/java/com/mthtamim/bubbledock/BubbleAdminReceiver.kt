package com.mthtamim.bubbledock

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

class BubbleAdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
    }
}
