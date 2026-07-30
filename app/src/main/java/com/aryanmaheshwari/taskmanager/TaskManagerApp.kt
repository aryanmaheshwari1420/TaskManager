package com.aryanmaheshwari.taskmanager

import android.app.Application
import com.google.android.material.color.DynamicColors

class TaskManagerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Applies Material You dynamic color on API 31+; no-op on older devices,
        // where the static Theme.TaskManager palette above is used as-is.
        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}