package com.mthtamim.bubbledock

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray

class MainActivity : AppCompatActivity() {
    private lateinit var root: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        root = findViewById(R.id.root_container)
        buildUi()
    }

    private fun heading(text: String) {
        val view = TextView(this)
        view.text = text
        view.setTextSize(18f)
        view.setTextColor(resources.getColor(R.color.bd_text, theme))
        view.setPadding(0, 24, 0, 8)
        root.addView(view)
    }

    private fun button(label: String, onClick: () -> Unit) {
        val view = Button(this)
        view.text = label
        view.setBackgroundResource(R.drawable.bg_panel)
        view.setTextColor(resources.getColor(R.color.bd_text, theme))
        view.setOnClickListener { onClick() }
        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.setMargins(0, 6, 0, 6)
        view.layoutParams = params
        root.addView(view)
    }

    private fun buildUi() {
        heading("BubbleDock")

        heading("বাবল")
        button("বাবল চালু") { startBubble() }
        button("বাবল বন্ধ") { stopBubble() }

        heading("পারমিশন")
        button("Overlay") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + packageName)))
        }
        button("Usage Access") {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
        button("ব্যাটারি ছাড়") {
            val pm = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                intent.data = Uri.parse("package:" + packageName)
                startActivity(intent)
            } else {
                Toast.makeText(this, "ব্যাটারি ছাড় আগেই দেওয়া আছে", Toast.LENGTH_SHORT).show()
            }
        }
        button("Accessibility") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        button("Device Admin") {
            val admin = ComponentName(this, BubbleAdminReceiver::class.java)
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
            intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "শুধু তোমার ট্যাপে স্ক্রিন লক করার জন্য।")
            startActivity(intent)
        }
        button("ব্রাইটনেস") {
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
            intent.data = Uri.parse("package:" + packageName)
            startActivity(intent)
        }
        button("ক্যামেরা") {
            requestPermissions(arrayOf(android.Manifest.permission.CAMERA), 101)
        }
        button("নোটিফিকেশন") {
            if (Build.VERSION.SDK_INT >= 33) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 102)
            } else {
                Toast.makeText(this, "এই Android ভার্সনে আলাদা নোটিফিকেশন পারমিশন লাগে না", Toast.LENGTH_SHORT).show()
            }
        }

        heading("ফেভারিট")
        button("ফেভারিট বাছো") { pickApps("ফেভারিট বাছো", Store.favourites(this)) { chosen ->
            Store.saveFavourites(this, chosen)
            Toast.makeText(this, "ফেভারিট সেভ হয়েছে", Toast.LENGTH_SHORT).show()
        } }

        heading("গ্রুপ")
        val groupInput = EditText(this)
        groupInput.hint = "গ্রুপের নাম"
        root.addView(groupInput)
        button("গ্রুপ বানাও") {
            val name = groupInput.text.toString().trim()
            if (name.isEmpty()) {
                Toast.makeText(this, "গ্রুপের নাম লেখো", Toast.LENGTH_SHORT).show()
            } else {
                val groups = Store.groups(this)
                if (!groups.has(name)) groups.put(name, JSONArray())
                Store.saveGroups(this, groups)
                recreate()
            }
        }
        val groups = Store.groups(this)
        val groupKeys = groups.keys()
        while (groupKeys.hasNext()) {
            val name = groupKeys.next()
            button(name + " অ্যাপ") {
                val current = ArrayList<String>()
                val arr = groups.optJSONArray(name)
                if (arr != null) {
                    for (i in 0 until arr.length()) current.add(arr.optString(i))
                }
                pickApps(name + " এর অ্যাপ", current) { chosen ->
                    val updated = Store.groups(this)
                    val jsonArr = JSONArray()
                    for (pkg in chosen) jsonArr.put(pkg)
                    updated.put(name, jsonArr)
                    Store.saveGroups(this, updated)
                    Toast.makeText(this, "গ্রুপ সেভ হয়েছে", Toast.LENGTH_SHORT).show()
                }
            }
            button(name + " মোছো") {
                val updated = Store.groups(this)
                updated.remove(name)
                Store.saveGroups(this, updated)
                recreate()
            }
        }

        heading("অ্যাকশন")
        val checks = ArrayList<Pair<String, CheckBox>>()
        val currentActions = Store.quickActions(this)
        for (item in Actions.catalogue) {
            val check = CheckBox(this)
            check.text = item.label
            check.isChecked = currentActions.contains(item.code)
            check.setTextColor(resources.getColor(R.color.bd_text, theme))
            root.addView(check)
            checks.add(Pair(item.code, check))
        }
        button("অ্যাকশন সেভ") {
            val chosen = ArrayList<String>()
            for (pair in checks) {
                if (pair.second.isChecked) chosen.add(pair.first)
            }
            Store.saveQuickActions(this, chosen)
            Toast.makeText(this, "কুইক অ্যাকশন সেভ হয়েছে", Toast.LENGTH_SHORT).show()
        }

        heading("প্রোফাইল")
        val profileInput = EditText(this)
        profileInput.hint = "প্রোফাইলের নাম"
        root.addView(profileInput)
        button("প্রোফাইল বানাও") {
            val name = profileInput.text.toString().trim()
            if (name.isEmpty()) {
                Toast.makeText(this, "প্রোফাইলের নাম লেখো", Toast.LENGTH_SHORT).show()
            } else {
                Store.addProfile(this, name)
                Store.setProfile(this, name)
                recreate()
            }
        }
        for (name in Store.profiles(this)) {
            button(name) {
                Store.setProfile(this, name)
                recreate()
            }
        }

        heading("লুকানো")
        button("লুকানোর অ্যাপ বাছো") { pickApps("লুকানোর অ্যাপ বাছো", Store.hiddenApps(this)) { chosen ->
            Store.saveHiddenApps(this, chosen)
            Toast.makeText(this, "লুকানোর তালিকা সেভ হয়েছে", Toast.LENGTH_SHORT).show()
        } }

        heading("সাইজ")
        button("বাবল ৪৮") { Store.setBubbleSize(this, 48) }
        button("বাবল ৫৬") { Store.setBubbleSize(this, 56) }
        button("বাবল ৭২") { Store.setBubbleSize(this, 72) }

        heading("ডক")
        button("ডক হরিজন্টাল") {
            Store.setDockOrientation(this, "horizontal")
            Toast.makeText(this, "ডক হরিজন্টাল সেভ হয়েছে", Toast.LENGTH_SHORT).show()
        }
        button("ডক ভার্টিকাল") {
            Store.setDockOrientation(this, "vertical")
            Toast.makeText(this, "ডক ভার্টিকাল সেভ হয়েছে", Toast.LENGTH_SHORT).show()
        }

        heading("ফ্লোটিং উইন্ডো")
        button("ফ্লোটিং উইন্ডো চালু") {
            Store.setFloatingWindow(this, true)
            Toast.makeText(this, "ফ্লোটিং উইন্ডো চালু হয়েছে", Toast.LENGTH_SHORT).show()
        }
        button("ফ্লোটিং উইন্ডো বন্ধ") {
            Store.setFloatingWindow(this, false)
            Toast.makeText(this, "ফ্লোটিং উইন্ডো বন্ধ হয়েছে", Toast.LENGTH_SHORT).show()
        }

        heading("ব্যাকআপ")
        button("ব্যাকআপ কপি") {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("BubbleDock backup", Store.backupJson(this))
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "ব্যাকআপ কপি হয়েছে", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startBubble() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, getString(R.string.need_overlay), Toast.LENGTH_SHORT).show()
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + packageName)))
            return
        }
        Store.setBubbleOn(this, true)
        val intent = Intent(this, BubbleService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        Toast.makeText(this, "বাবল চালু হয়েছে", Toast.LENGTH_SHORT).show()
    }

    private fun stopBubble() {
        Store.setBubbleOn(this, false)
        stopService(Intent(this, BubbleService::class.java))
        Toast.makeText(this, "বাবল বন্ধ হয়েছে", Toast.LENGTH_SHORT).show()
    }

    private fun pickApps(title: String, current: List<String>, onDone: (List<String>) -> Unit) {
        val apps = AppList.launchable(this)
        val labels = apps.map { it.label }.toTypedArray()
        val checked = BooleanArray(apps.size) { index ->
            current.contains(apps[index].packageName)
        }
        val chosen = current.toMutableList()
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMultiChoiceItems(labels, checked) { _, which, isChecked ->
                val pkg = apps[which].packageName
                if (isChecked) {
                    if (!chosen.contains(pkg)) chosen.add(pkg)
                } else {
                    chosen.remove(pkg)
                }
            }
            .setPositiveButton("সেভ") { _, _ -> onDone(chosen) }
            .setNegativeButton("বাতিল", null)
            .show()
    }

}
