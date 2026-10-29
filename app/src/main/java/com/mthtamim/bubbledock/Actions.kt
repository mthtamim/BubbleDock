package com.mthtamim.bubbledock

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.provider.Settings
import android.widget.Toast

/**
 * Quick actions, each triggered only by the user's own tap on a tile.
 * Where Android blocks a direct toggle (WiFi on Android 10+), the action
 * honestly opens the system panel instead of pretending it toggled.
 * Back / Recents / Screenshot need the optional Accessibility service;
 * Lock needs the optional Device Admin. Without those grants the tile
 * shows a plain explanation instead of failing silently.
 */
object Actions {
    data class Item(val code: String, val label: String)

    val catalogue: List<Item> = listOf(
        Item("home", "হোম"),
        Item("back", "ব্যাক (Accessibility)"),
        Item("recents", "রিসেন্ট (Accessibility)"),
        Item("screenshot", "স্ক্রিনশট (Accessibility)"),
        Item("lock", "স্ক্রিন লক (Device Admin)"),
        Item("torch", "ফ্ল্যাশলাইট"),
        Item("wifi_panel", "WiFi প্যানেল"),
        Item("bluetooth_settings", "Bluetooth সেটিংস"),
        Item("sound_normal", "সাউন্ড: সাধারণ"),
        Item("sound_vibrate", "সাউন্ড: ভাইব্রেট"),
        Item("dnd_toggle", "DND টগল"),
        Item("volume_up", "ভলিউম বাড়ান"),
        Item("volume_down", "ভলিউম কমান"),
        Item("brightness_up", "ব্রাইটনেস বাড়ান"),
        Item("brightness_down", "ব্রাইটনেস কমান")
    )

    fun labelFor(code: String): String {
        return catalogue.firstOrNull { it.code == code }?.label ?: code
    }

    fun iconFor(code: String): String {
        return when (code) {
            "home" -> "\uD83C\uDFE0"
            "back" -> "\u2190"
            "recents" -> "\u25A2"
            "screenshot" -> "\uD83D\uDCF7"
            "lock" -> "\uD83D\uDD12"
            "torch" -> "\uD83D\uDD26"
            "wifi_panel" -> "\uD83D\uDCF6"
            "bluetooth_settings" -> "\uD83D\uDD35"
            "sound_normal" -> "\uD83D\uDD0A"
            "sound_vibrate" -> "\uD83D\uDCF3"
            "dnd_toggle" -> "\uD83C\uDF19"
            "volume_up" -> "\uD83D\uDD0A"
            "volume_down" -> "\uD83D\uDD09"
            "brightness_up" -> "\u2600"
            "brightness_down" -> "\uD83C\uDF11"
            else -> "\u25CF"
        }
    }

    fun run(context: Context, code: String) {
        when (code) {
            "home" -> {
                val intent = Intent(Intent.ACTION_MAIN)
                intent.addCategory(Intent.CATEGORY_HOME)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
            "back" -> {
                if (!BubbleAccessibilityService.performBack()) {
                    Toast.makeText(context, context.getString(R.string.need_accessibility), Toast.LENGTH_SHORT).show()
                }
            }
            "recents" -> {
                if (!BubbleAccessibilityService.performRecents()) {
                    Toast.makeText(context, context.getString(R.string.need_accessibility), Toast.LENGTH_SHORT).show()
                }
            }
            "screenshot" -> {
                if (!BubbleAccessibilityService.performScreenshot()) {
                    Toast.makeText(context, context.getString(R.string.need_accessibility), Toast.LENGTH_SHORT).show()
                }
            }
            "lock" -> {
                val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                val admin = ComponentName(context, BubbleAdminReceiver::class.java)
                if (dpm.isAdminActive(admin)) {
                    dpm.lockNow()
                } else {
                    Toast.makeText(context, context.getString(R.string.need_admin), Toast.LENGTH_SHORT).show()
                }
            }
            "torch" -> {
                toggleTorch(context)
            }
            "wifi_panel" -> {
                val intent = Intent(Settings.Panel.ACTION_WIFI)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
            "bluetooth_settings" -> {
                val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
            "sound_normal" -> {
                audio(context).ringerMode = AudioManager.RINGER_MODE_NORMAL
            }
            "sound_vibrate" -> {
                audio(context).ringerMode = AudioManager.RINGER_MODE_VIBRATE
            }
            "dnd_toggle" -> {
                toggleDnd(context)
            }
            "volume_up" -> {
                audio(context).adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
            }
            "volume_down" -> {
                audio(context).adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
            }
            "brightness_up" -> {
                changeBrightness(context, 25)
            }
            "brightness_down" -> {
                changeBrightness(context, -25)
            }
            else -> {
                Toast.makeText(context, "অজানা অ্যাকশন: " + code, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun audio(context: Context): AudioManager {
        return context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    private var torchOn: Boolean = false

    private fun toggleTorch(context: Context) {
        try {
            val camera = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val ids = camera.cameraIdList
            if (ids.isEmpty()) {
                Toast.makeText(context, "এই ফোনে ফ্ল্যাশ পাওয়া যায়নি", Toast.LENGTH_SHORT).show()
                return
            }
            torchOn = !torchOn
            camera.setTorchMode(ids[0], torchOn)
        } catch (e: Exception) {
            Toast.makeText(context, "ফ্ল্যাশলাইট চালু করা যায়নি। ক্যামেরা পারমিশন দেখো।", Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleDnd(context: Context) {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            if (!nm.isNotificationPolicyAccessGranted) {
                val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return
            }
            if (nm.currentInterruptionFilter == android.app.NotificationManager.INTERRUPTION_FILTER_NONE) {
                nm.setInterruptionFilter(android.app.NotificationManager.INTERRUPTION_FILTER_ALL)
            } else {
                nm.setInterruptionFilter(android.app.NotificationManager.INTERRUPTION_FILTER_NONE)
            }
        } catch (e: Exception) {
            Toast.makeText(context, "DND বদলানো যায়নি", Toast.LENGTH_SHORT).show()
        }
    }

    private fun changeBrightness(context: Context, delta: Int) {
        try {
            if (!Settings.System.canWrite(context)) {
                val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
                intent.data = android.net.Uri.parse("package:" + context.packageName)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return
            }
            val resolver = context.contentResolver
            val current = Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS, 128)
            val next = (current + delta).coerceIn(10, 255)
            Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, next)
        } catch (e: Exception) {
            Toast.makeText(context, "ব্রাইটনেস বদলানো যায়নি", Toast.LENGTH_SHORT).show()
        }
    }
}
