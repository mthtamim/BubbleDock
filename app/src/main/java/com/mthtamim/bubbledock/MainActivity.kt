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

    private fun body(text: String) {
        val view = TextView(this)
        view.text = text
        view.setTextColor(resources.getColor(R.color.bd_text_body, theme))
        view.setPadding(0, 4, 0, 4)
        root.addView(view)
    }

    private fun button(label: String, onClick: () -> Unit) {
        val view = Button(this)
        view.text = label
        view.setBackgroundResource(R.drawable.bg_button)
        view.setTextColor(resources.getColor(R.color.bd_on_accent, theme))
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
        body("সাধারণত শুধু বাবল সার্কেল দেখা যাবে। বাবলে একবার ট্যাপ করলে ঐচ্ছিক অ্যাকশন আর গ্রুপ আসবে, ডাবল ট্যাপ করলে নিচে এক লাইনে বড় অ্যাপের ডক আসবে — একটাই লাইন, বাম-ডানে স্ক্রল করে দেখা যাবে। সব সেটিংস শুধু এই ফোনেই থাকে, কোথাও পাঠানো হয় না।")
        body("বর্তমান প্রোফাইল: " + Store.profile(this))
        body("Overlay: " + if (Settings.canDrawOverlays(this)) "চালু" else "বন্ধ")
        body("Usage Access: " + if (AppList.hasUsageAccess(this)) "চালু" else "বন্ধ")

        heading("বাবল")
        button("বাবল চালু করো") { startBubble() }
        button("বাবল বন্ধ করো") { stopBubble() }

        heading("পারমিশন")
        body("Overlay ছাড়া বাবল দেখা যাবে না। Usage Access ছাড়া রিসেন্ট অ্যাপ আসবে না। ব্যাটারি ছাড় না দিলে XOS বাবল মেরে ফেলতে পারে। Accessibility আর Device Admin শুধু নির্দিষ্ট কয়েকটা অ্যাকশনের জন্য, বেসিক অ্যাপ সুইচের জন্য লাগে না।")
        button("Overlay পারমিশন দাও") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + packageName)))
        }
        button("Usage Access দাও") {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
        button("ব্যাটারি অপটিমাইজেশন ছাড় দাও") {
            val pm = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                intent.data = Uri.parse("package:" + packageName)
                startActivity(intent)
            } else {
                Toast.makeText(this, "ব্যাটারি ছাড় আগেই দেওয়া আছে", Toast.LENGTH_SHORT).show()
            }
        }
        button("Accessibility সেটিংস খোলো (ঐচ্ছিক)") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        button("Device Admin চালু করো (ঐচ্ছিক, শুধু লকের জন্য)") {
            val admin = ComponentName(this, BubbleAdminReceiver::class.java)
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
            intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "শুধু তোমার ট্যাপে স্ক্রিন লক করার জন্য।")
            startActivity(intent)
        }
        button("ব্রাইটনেস লেখার পারমিশন (ঐচ্ছিক)") {
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
            intent.data = Uri.parse("package:" + packageName)
            startActivity(intent)
        }
        button("ক্যামেরা পারমিশন (ফ্ল্যাশলাইটের জন্য)") {
            requestPermissions(arrayOf(android.Manifest.permission.CAMERA), 101)
        }
        button("নোটিফিকেশন পারমিশন (বাবল চালু রাখতে)") {
            if (Build.VERSION.SDK_INT >= 33) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 102)
            } else {
                Toast.makeText(this, "এই Android ভার্সনে আলাদা নোটিফিকেশন পারমিশন লাগে না", Toast.LENGTH_SHORT).show()
            }
        }

        heading("ফেভারিট অ্যাপ")
        button("ফেভারিট বাছো") { pickApps("ফেভারিট বাছো", Store.favourites(this)) { chosen ->
            Store.saveFavourites(this, chosen)
            Toast.makeText(this, "ফেভারিট সেভ হয়েছে", Toast.LENGTH_SHORT).show()
        } }

        heading("গ্রুপ")
        val groupInput = EditText(this)
        groupInput.hint = "নতুন গ্রুপের নাম, যেমন পড়াশোনা"
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
            body("গ্রুপ: " + name)
            button(name + " — অ্যাপ বাছো") {
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
            button(name + " — গ্রুপ মোছো") {
                val updated = Store.groups(this)
                updated.remove(name)
                Store.saveGroups(this, updated)
                recreate()
            }
        }

        heading("কুইক অ্যাকশন")
        body("যেগুলোতে টিক দেবে, সেগুলোই বাবলে একবার ট্যাপ করলে দেখাবে।")
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
        button("কুইক অ্যাকশন সেভ করো") {
            val chosen = ArrayList<String>()
            for (pair in checks) {
                if (pair.second.isChecked) chosen.add(pair.first)
            }
            Store.saveQuickActions(this, chosen)
            Toast.makeText(this, "কুইক অ্যাকশন সেভ হয়েছে", Toast.LENGTH_SHORT).show()
        }

        heading("প্রোফাইল")
        val profileInput = EditText(this)
        profileInput.hint = "নতুন প্রোফাইলের নাম"
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
            button("প্রোফাইল: " + name) {
                Store.setProfile(this, name)
                recreate()
            }
        }

        heading("যেসব অ্যাপে বাবল লুকিয়ে থাকবে")
        button("লুকানোর অ্যাপ বাছো") { pickApps("লুকানোর অ্যাপ বাছো", Store.hiddenApps(this)) { chosen ->
            Store.saveHiddenApps(this, chosen)
            Toast.makeText(this, "লুকানোর তালিকা সেভ হয়েছে", Toast.LENGTH_SHORT).show()
        } }

        heading("বাবল আর ডকের চেহারা")
        button("বাবল: ছোট (48)") { Store.setBubbleSize(this, 48) }
        button("বাবল: মাঝারি (56)") { Store.setBubbleSize(this, 56) }
        button("বাবল: বড় (72)") { Store.setBubbleSize(this, 72) }
        body("বাবলের সাইজ বদলাতে বাবল বন্ধ করে আবার চালু করো। ডক সবসময় একটাই লাইন, বড় আইকনসহ।")
        body("বাবলে ডাবল ট্যাপ করলে সবসময় অ্যাপের লাইন খুলবে — v1.1-এ ডাবল-ট্যাপের আলাদা অ্যাকশন বাছাই আর নেই।")

        heading("ব্যাকআপ")
        button("সেটিংস JSON কপি করো") {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("BubbleDock backup", Store.backupJson(this))
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "ব্যাকআপ কপি হয়েছে", Toast.LENGTH_SHORT).show()
        }
        body("v1.1-তে নেই, v2-তে আসবে: ম্যাক্রো/অটো-ট্যাপ সিকোয়েন্স, PhoneLink/Automation Lab ইন্টিগ্রেশন।")
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
