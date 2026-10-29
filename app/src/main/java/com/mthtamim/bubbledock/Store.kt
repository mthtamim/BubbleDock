package com.mthtamim.bubbledock

import android.content.Context
import org.json.JSONObject

/**
 * All BubbleDock settings live on the phone only, in SharedPreferences.
 * No account, no server, no export except the JSON backup the user asks for.
 * Profiles are named sets: each profile keeps its own favourites, groups
 * and quick actions under profile_<name>_ keys. The current profile name
 * is stored in "profile".
 */
object Store {
    private const val PREFS = "bubbledock"

    fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun profile(context: Context): String =
        prefs(context).getString("profile", "General") ?: "General"

    fun setProfile(context: Context, name: String) {
        prefs(context).edit().putString("profile", name).apply()
    }

    fun profiles(context: Context): MutableList<String> {
        val raw = prefs(context).getString("profile_list", "General") ?: "General"
        val list = raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
        if (list.isEmpty()) list.add("General")
        return list
    }

    fun addProfile(context: Context, name: String) {
        val list = profiles(context)
        if (!list.contains(name)) list.add(name)
        prefs(context).edit().putString("profile_list", list.joinToString(",")).apply()
    }

    private fun key(context: Context, base: String): String {
        return "profile_" + profile(context) + "_" + base
    }

    fun stringList(context: Context, base: String, default: String): MutableList<String> {
        val raw = prefs(context).getString(key(context, base), default) ?: default
        if (raw.isBlank()) return mutableListOf()
        return raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
    }

    fun saveList(context: Context, base: String, list: List<String>) {
        prefs(context).edit().putString(key(context, base), list.joinToString(",")).apply()
    }

    fun favourites(context: Context): MutableList<String> =
        stringList(context, "favourites", "")

    fun saveFavourites(context: Context, list: List<String>) =
        saveList(context, "favourites", list)

    fun quickActions(context: Context): MutableList<String> =
        stringList(context, "quick_actions", "home,torch,volume_up,volume_down")

    fun saveQuickActions(context: Context, list: List<String>) =
        saveList(context, "quick_actions", list)

    fun hiddenApps(context: Context): MutableList<String> =
        stringList(context, "hidden_apps", "")

    fun saveHiddenApps(context: Context, list: List<String>) =
        saveList(context, "hidden_apps", list)

    fun groups(context: Context): JSONObject {
        val raw = prefs(context).getString(key(context, "groups"), "{}") ?: "{}"
        return try {
            JSONObject(raw)
        } catch (e: Exception) {
            JSONObject()
        }
    }

    fun saveGroups(context: Context, groups: JSONObject) {
        prefs(context).edit().putString(key(context, "groups"), groups.toString()).apply()
    }

    fun dockOrientation(context: Context): String =
        prefs(context).getString("dock_orientation", "horizontal") ?: "horizontal"

    fun setDockOrientation(context: Context, orientation: String) {
        prefs(context).edit().putString("dock_orientation", orientation).apply()
    }

    fun floatingWindow(context: Context): Boolean =
        prefs(context).getBoolean("floating_window", false)

    fun setFloatingWindow(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean("floating_window", on).apply()
    }

    fun bubbleSize(context: Context): Int =
        prefs(context).getInt("bubble_size", 56)

    fun setBubbleSize(context: Context, size: Int) {
        prefs(context).edit().putInt("bubble_size", size).apply()
    }

    fun panelLayout(context: Context): String =
        prefs(context).getString("panel_layout", "grid") ?: "grid"

    fun setPanelLayout(context: Context, layout: String) {
        prefs(context).edit().putString("panel_layout", layout).apply()
    }

    fun doubleTapAction(context: Context): String =
        prefs(context).getString("double_tap", "") ?: ""

    fun setDoubleTapAction(context: Context, action: String) {
        prefs(context).edit().putString("double_tap", action).apply()
    }

    fun bubbleOn(context: Context): Boolean =
        prefs(context).getBoolean("bubble_on", false)

    fun setBubbleOn(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean("bubble_on", on).apply()
    }

    fun backupJson(context: Context): String {
        val out = JSONObject()
        out.put("profile", profile(context))
        out.put("favourites", favourites(context).joinToString(","))
        out.put("quick_actions", quickActions(context).joinToString(","))
        out.put("hidden_apps", hiddenApps(context).joinToString(","))
        out.put("groups", groups(context).toString())
        out.put("bubble_size", bubbleSize(context))
        out.put("panel_layout", panelLayout(context))
        out.put("double_tap", doubleTapAction(context))
        out.put("dock_orientation", dockOrientation(context))
        out.put("floating_window", floatingWindow(context))
        return out.toString()
    }
}
