package com.mthtamim.bubbledock

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.app.ActivityOptions
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Build

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

    /**
     * Previous recent app: recent()[0] is the app the bubble is currently
     * floating over (the overlay itself is not an activity, so it never
     * appears here), so [1] is the app the user was in before the current
     * one. That is the app a double tap should switch to. If only one
     * recent app exists, fall back to it.
     */
    fun previousRecent(context: Context): AppInfo? {
        val recentApps = recent(context, 3)
        if (recentApps.size > 1) return recentApps[1]
        if (recentApps.isNotEmpty()) return recentApps[0]
        return null
    }

    /**
     * Floating-window launch, best effort only. A regular app cannot force
     * another app into a floating window: ActivityOptions.setLaunchBounds
     * only takes effect on devices that support freeform windows. If the
     * device ignores the bounds, the app opens fullscreen instead, and
     * this function falls back to a normal launch on any failure.
     */
    fun launchFloating(context: Context, packageName: String): Boolean {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
                ?: return false
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val metrics = context.resources.displayMetrics
                val width = metrics.widthPixels
                val height = metrics.heightPixels
                val bounds = Rect(
                    width / 10,
                    height / 6,
                    width * 9 / 10,
                    height * 5 / 6
                )
                val options = ActivityOptions.makeBasic().setLaunchBounds(bounds)
                context.startActivity(intent, options.toBundle())
                return true
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            launch(context, packageName)
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
