package com.mthtamim.bubbledock

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

data class AppInfo(val packageName: String, val label: String)

object AppList {
    fun launchable(context: Context): List<AppInfo> {
        val pm: PackageManager = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN)
        intent.addCategory(Intent.CATEGORY_LAUNCHER)
        val found = pm.queryIntentActivities(intent, 0)
        val list = ArrayList<AppInfo>()
        for (info in found) {
            val pkg = info.activityInfo.packageName
            if (pkg == context.packageName) continue
            val label = info.loadLabel(pm).toString()
            list.add(AppInfo(pkg, label))
        }
        list.sortBy { it.label.lowercase() }
        return list
    }

    fun labelFor(context: Context, packageName: String): String {
        return try {
            val pm = context.packageManager
            val info = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(info).toString()
        } catch (e: Exception) {
            packageName
        }
    }

    fun hasUsageAccess(context: Context): Boolean {
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = appOps.checkOpNoThrow(
                "android:get_usage_stats",
                android.os.Process.myUid(),
                context.packageName
            )
            mode == AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) {
            false
        }
    }

    fun recent(context: Context, limit: Int): List<AppInfo> {
        if (!hasUsageAccess(context)) return emptyList()
        val launchable = launchable(context)
        val launchableSet = launchable.map { it.packageName }.toSet()
        return try {
            val usage = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val now = System.currentTimeMillis()
            val stats = usage.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                now - 24L * 60L * 60L * 1000L,
                now
            ) ?: return emptyList()
            val seen = LinkedHashSet<String>()
            val sorted = stats.sortedByDescending { it.lastTimeUsed }
            for (stat in sorted) {
                if (seen.size >= limit) break
                val pkg = stat.packageName
                if (pkg == context.packageName) continue
                if (!launchableSet.contains(pkg)) continue
                if (stat.lastTimeUsed <= 0L) continue
                seen.add(pkg)
            }
            val byPackage = launchable.associateBy { it.packageName }
            seen.mapNotNull { byPackage[it] }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun launch(context: Context, packageName: String): Boolean {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent == null) {
                return false
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }
}
