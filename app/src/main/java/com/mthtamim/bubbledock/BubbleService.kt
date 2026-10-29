package com.mthtamim.bubbledock

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import kotlin.math.cos
import kotlin.math.sin

/**
 * BubbleDock v1.4.1: ring fix only, nothing else changed.
 * BubbleDock v1.4: gestures from Tamim's hand sketch.
 * - Normally only the bubble circle is visible.
 * - Single tap on the bubble: circular menu, mainly quick ACTIONS
 *   (not apps), chosen in settings. Icons only, no text.
 * - Double tap on the bubble: go directly to the previous recent app
 *   (the app before the one the bubble is floating over).
 * - Long press on the bubble: dock line. Dock orientation is chosen
 *   in settings: horizontal (bottom line) or vertical (side line).
 * - Dock apps open in a floating window when that optional setting
 *   is ON; see AppList.launchFloating for the honest freeform limit.
 * - Tapping an app or action hides the open overlay.
 */
class BubbleService : Service() {
    private lateinit var windowManager: WindowManager
    private var bubbleView: ImageView? = null
    private var dockView: LinearLayout? = null
    private var dockRow: LinearLayout? = null
    private var dockScrollView: View? = null
    private var dockSearch: EditText? = null
    private var circularView: FrameLayout? = null
    private var trashView: TextView? = null
    private var searchQuery: String = ""
    private var groupSelected: String? = null
    private var dockOpen: Boolean = false
    private var circularOpen: Boolean = false
    private var lastTapTime: Long = 0L
    private var bubbleX: Int = 0
    private var bubbleY: Int = 0
    private val handler = Handler(Looper.getMainLooper())
    private var screenWidth: Int = 1080
    private var screenHeight: Int = 1920

    private val stopReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            stopBubble()
        }
    }

    private val idleRunnable = Runnable {
        bubbleView?.alpha = 0.5f
    }

    private val singleTapRunnable = Runnable {
        if (lastTapTime != 0L) {
            lastTapTime = 0L
            toggleCircular()
        }
    }

    private val hideCheckRunnable = object : Runnable {
        override fun run() {
            checkHiddenApp()
            handler.postDelayed(this, 2000L)
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getMetrics(metrics)
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
        startAsForeground()
        val filter = IntentFilter("com.mthtamim.bubbledock.STOP")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(stopReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(stopReceiver, filter)
        }
        handler.postDelayed(hideCheckRunnable, 2000L)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (Settings.canDrawOverlays(this)) {
            showBubble()
        } else {
            Toast.makeText(this, getString(R.string.need_overlay), Toast.LENGTH_SHORT).show()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        try {
            unregisterReceiver(stopReceiver)
        } catch (e: Exception) {
        }
        removeBubble()
        removeDock()
        removeCircular()
        removeTrash()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun startAsForeground() {
        val channelId = "bubbledock_bubble"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            val existing = manager.getNotificationChannel(channelId)
            if (existing == null) {
                val channel = NotificationChannel(
                    channelId,
                    getString(R.string.channel_name),
                    NotificationManager.IMPORTANCE_LOW
                )
                channel.description = getString(R.string.channel_desc)
                manager.createNotificationChannel(channel)
            }
        }
        val mainIntent = Intent(this, MainActivity::class.java)
        val mainPending = PendingIntent.getActivity(
            this, 0, mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = Intent("com.mthtamim.bubbledock.STOP")
        val stopPending = PendingIntent.getBroadcast(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification: Notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, channelId)
                .setContentTitle(getString(R.string.notif_title))
                .setContentText(getString(R.string.notif_text))
                .setSmallIcon(R.drawable.ic_bubble)
                .setContentIntent(mainPending)
                .addAction(0, getString(R.string.notif_stop), stopPending)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle(getString(R.string.notif_title))
                .setContentText(getString(R.string.notif_text))
                .setSmallIcon(R.drawable.ic_bubble)
                .setContentIntent(mainPending)
                .addAction(0, getString(R.string.notif_stop), stopPending)
                .build()
        }
        startForeground(1, notification)
    }

    private fun showBubble() {
        if (bubbleView != null) return
        val size = Store.bubbleSize(this).coerceIn(40, 96)
        val image = ImageView(this)
        image.setImageResource(R.drawable.ic_bubble)
        image.setBackgroundResource(R.drawable.bg_bubble)
        image.contentDescription = getString(R.string.bubble_desc)
        image.setPadding(10, 10, 10, 10)
        val params = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = screenWidth - size - 24
        params.y = screenHeight / 3
        bubbleX = params.x
        bubbleY = params.y
        image.setOnTouchListener(BubbleTouch(params))
        windowManager.addView(image, params)
        bubbleView = image
        scheduleIdle()
    }

    private fun scheduleIdle() {
        handler.removeCallbacks(idleRunnable)
        handler.postDelayed(idleRunnable, 5000L)
    }

    private inner class BubbleTouch(private val params: WindowManager.LayoutParams) : View.OnTouchListener {
        private var downX: Float = 0f
        private var downY: Float = 0f
        private var startX: Int = 0
        private var startY: Int = 0
        private var moved: Boolean = false
        private var longPressed: Boolean = false

        private val longPressRunnable = Runnable {
            if (!moved) {
                longPressed = true
                removeCircular()
                showDock()
            }
        }

        override fun onTouch(view: View, event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = params.x
                    startY = params.y
                    moved = false
                    longPressed = false
                    view.alpha = 1.0f
                    handler.removeCallbacks(idleRunnable)
                    showTrash()
                    handler.postDelayed(longPressRunnable, 500L)
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downX).toInt()
                    val dy = (event.rawY - downY).toInt()
                    if (kotlin.math.abs(dx) > 12 || kotlin.math.abs(dy) > 12) {
                        moved = true
                        handler.removeCallbacks(longPressRunnable)
                    }
                    if (moved && !longPressed) {
                        params.x = startX + dx
                        params.y = startY + dy
                        bubbleX = params.x
                        bubbleY = params.y
                        try {
                            windowManager.updateViewLayout(view, params)
                        } catch (e: Exception) {
                        }
                    }
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    handler.removeCallbacks(longPressRunnable)
                    removeTrash()
                    if (longPressed) {
                        scheduleIdle()
                        return true
                    }
                    if (!moved) {
                        val now = System.currentTimeMillis()
                        if (lastTapTime != 0L && now - lastTapTime < 350L) {
                            handler.removeCallbacks(singleTapRunnable)
                            lastTapTime = 0L
                            goToPreviousRecent()
                        } else {
                            lastTapTime = now
                            handler.postDelayed(singleTapRunnable, 360L)
                        }
                    } else {
                        val bubbleBottom = params.y + view.height
                        if (params.y > screenHeight - 220 || bubbleBottom > screenHeight - 40) {
                            stopBubble()
                            return true
                        }
                        snapToEdge(params, view)
                        bubbleX = params.x
                        bubbleY = params.y
                    }
                    scheduleIdle()
                    return true
                }
            }
            return false
        }
    }

    private fun snapToEdge(params: WindowManager.LayoutParams, view: View) {
        val middle = screenWidth / 2
        params.x = if (params.x + view.width / 2 < middle) 16 else screenWidth - view.width - 16
        try {
            windowManager.updateViewLayout(view, params)
        } catch (e: Exception) {
        }
    }

    private fun showTrash() {
        if (trashView != null) return
        val text = TextView(this)
        text.text = "✕"
        text.setTextColor(resources.getColor(R.color.bd_text, theme))
        text.setBackgroundResource(R.drawable.bg_panel)
        text.setPadding(24, 24, 24, 24)
        text.gravity = Gravity.CENTER
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        params.y = 48
        windowManager.addView(text, params)
        trashView = text
    }

    private fun removeTrash() {
        trashView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
            }
        }
        trashView = null
    }

    private fun removeBubble() {
        bubbleView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
            }
        }
        bubbleView = null
    }

    private fun toggleCircular() {
        if (circularOpen) {
            removeCircular()
        } else {
            removeDock()
            showCircular()
        }
    }

    private fun showCircular() {
        if (circularView != null) return
        val actions = Store.quickActions(this).take(8)
        if (actions.isEmpty()) {
            Toast.makeText(this, "সেটিংস থেকে অ্যাকশন বাছো", Toast.LENGTH_SHORT).show()
            return
        }
        val size = dp(160)
        val root = FrameLayout(this)
        // v1.4.1: NO panel background. A square card here is exactly what
        // made v1.4 look like a box instead of a ring. The overlay is
        // transparent; only the icons float, and the bubble underneath
        // stays visible in the centre and closes the menu when tapped.
        val center = size / 2
        val radius = dp(56)
        for ((index, code) in actions.withIndex()) {
            val angle = Math.toRadians(-90.0 + index * 360.0 / actions.size)
            val iconView = TextView(this)
            iconView.text = Actions.iconFor(code)
            iconView.textSize = 14f
            iconView.gravity = Gravity.CENTER
            iconView.contentDescription = Actions.labelFor(code)
            iconView.setBackgroundResource(R.drawable.bg_ring_icon)
            iconView.setTextColor(resources.getColor(R.color.bd_text, theme))
            val iconSize = dp(32)
            val params = FrameLayout.LayoutParams(iconSize, iconSize)
            params.leftMargin = (center + radius * cos(angle) - iconSize / 2).toInt()
            params.topMargin = (center + radius * sin(angle) - iconSize / 2).toInt()
            iconView.layoutParams = params
            iconView.setOnClickListener {
                Actions.run(this, code)
                removeCircular()
            }
            root.addView(iconView)
        }
        val bubbleSize = Store.bubbleSize(this).coerceIn(40, 96)
        val params = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = (bubbleX + bubbleSize / 2 - size / 2).coerceIn(dp(8), (screenWidth - size - dp(8)).coerceAtLeast(dp(8)))
        params.y = (bubbleY + bubbleSize / 2 - size / 2).coerceIn(dp(8), (screenHeight - size - dp(8)).coerceAtLeast(dp(8)))
        windowManager.addView(root, params)
        circularView = root
        circularOpen = true
    }

    private fun removeCircular() {
        circularView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
            }
        }
        circularView = null
        circularOpen = false
    }

    private fun showDock() {
        if (dockView != null) return
        val vertical = Store.dockOrientation(this) == "vertical"
        val root = LinearLayout(this)
        root.orientation = if (vertical) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
        root.gravity = Gravity.CENTER
        root.setBackgroundResource(R.drawable.bg_panel)
        root.setPadding(dp(6), dp(6), dp(6), dp(6))

        val searchIcon = TextView(this)
        searchIcon.text = "\uD83D\uDD0D"
        searchIcon.textSize = 18f
        searchIcon.gravity = Gravity.CENTER
        searchIcon.setBackgroundResource(R.drawable.bg_chip)
        searchIcon.setTextColor(resources.getColor(R.color.bd_text, theme))
        searchIcon.layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))

        val row = LinearLayout(this)
        row.orientation = if (vertical) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER
        dockRow = row

        val scrollView: View
        if (vertical) {
            val scroll = ScrollView(this)
            scroll.isVerticalScrollBarEnabled = false
            scroll.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(360)
            )
            scroll.addView(row)
            scrollView = scroll
        } else {
            val scroll = HorizontalScrollView(this)
            scroll.isHorizontalScrollBarEnabled = false
            scroll.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            scroll.addView(row)
            scrollView = scroll
        }
        dockScrollView = scrollView

        val search = EditText(this)
        search.visibility = View.GONE
        search.setTextColor(resources.getColor(R.color.bd_text, theme))
        if (vertical) {
            search.layoutParams = LinearLayout.LayoutParams(dp(160), LinearLayout.LayoutParams.WRAP_CONTENT)
        } else {
            search.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            }

            override fun afterTextChanged(s: Editable?) {
                searchQuery = s?.toString() ?: ""
                refreshDockRow()
            }
        })
        dockSearch = search
        searchIcon.setOnClickListener {
            if (search.visibility == View.VISIBLE) {
                search.visibility = View.GONE
                search.setText("")
                scrollView.visibility = View.VISIBLE
            } else {
                scrollView.visibility = View.GONE
                search.visibility = View.VISIBLE
                search.requestFocus()
            }
        }

        val close = TextView(this)
        close.text = "✕"
        close.textSize = 18f
        close.gravity = Gravity.CENTER
        close.setBackgroundResource(R.drawable.bg_chip)
        close.setTextColor(resources.getColor(R.color.bd_text, theme))
        close.layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
        close.setOnClickListener {
            if (search.visibility == View.VISIBLE) {
                search.visibility = View.GONE
                search.setText("")
                scrollView.visibility = View.VISIBLE
            } else {
                removeDock()
            }
        }

        root.addView(searchIcon)
        root.addView(scrollView)
        root.addView(search)
        root.addView(close)
        refreshDockRow()

        val params: WindowManager.LayoutParams
        if (vertical) {
            params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            )
            params.gravity = Gravity.START or Gravity.CENTER_VERTICAL
            params.x = dp(8)
        } else {
            params = WindowManager.LayoutParams(
                screenWidth - dp(20),
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            )
            params.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            params.y = dp(16)
        }
        windowManager.addView(root, params)
        dockView = root
        dockOpen = true
    }

    private fun removeDock() {
        dockView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
            }
        }
        dockView = null
        dockRow = null
        dockScrollView = null
        dockSearch = null
        dockOpen = false
        searchQuery = ""
        groupSelected = null
    }

    private fun refreshDockRow() {
        val row = dockRow ?: return
        row.removeAllViews()
        if (searchQuery.isNotBlank()) {
            val query = searchQuery.lowercase()
            val filtered = AppList.launchable(this).filter { it.label.lowercase().contains(query) }
            for (app in filtered) addDockAppIcon(row, app)
            return
        }
        val selected = groupSelected
        if (selected != null) {
            val back = TextView(this)
            back.text = "↩"
            back.textSize = 18f
            back.gravity = Gravity.CENTER
            back.setBackgroundResource(R.drawable.bg_chip)
            back.setTextColor(resources.getColor(R.color.bd_text, theme))
            val backParams = LinearLayout.LayoutParams(dp(36), dp(36))
            backParams.setMargins(dp(2), dp(2), dp(2), dp(2))
            back.layoutParams = backParams
            back.setOnClickListener {
                groupSelected = null
                refreshDockRow()
            }
            row.addView(back)
            val groups = Store.groups(this)
            val arr = groups.optJSONArray(selected)
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val pkg = arr.optString(i)
                    if (pkg.isNotEmpty()) addDockAppIcon(row, AppInfo(pkg, AppList.labelFor(this, pkg)))
                }
            }
            return
        }
        val groups = Store.groups(this)
        val keys = groups.keys()
        val groupIcons = listOf("\uD83D\uDC65", "\uD83D\uDCDA", "\uD83C\uDFAE", "\uD83D\uDCBC", "\uD83C\uDFE0", "\u2B50")
        var groupIndex = 0
        while (keys.hasNext()) {
            val name = keys.next()
            val iconView = TextView(this)
            iconView.text = groupIcons[groupIndex % groupIcons.size]
            iconView.textSize = 18f
            iconView.gravity = Gravity.CENTER
            iconView.contentDescription = name
            iconView.setBackgroundResource(R.drawable.bg_chip)
            iconView.setTextColor(resources.getColor(R.color.bd_text, theme))
            val iconParams = LinearLayout.LayoutParams(dp(36), dp(36))
            iconParams.setMargins(dp(2), dp(2), dp(2), dp(2))
            iconView.layoutParams = iconParams
            iconView.setOnClickListener {
                groupSelected = name
                refreshDockRow()
            }
            row.addView(iconView)
            groupIndex++
        }
        for (app in dockApps()) addDockAppIcon(row, app)
    }

    private fun addDockAppIcon(row: LinearLayout, app: AppInfo) {
        val icon = ImageView(this)
        val iconParams = LinearLayout.LayoutParams(dp(36), dp(36))
        iconParams.setMargins(dp(2), dp(2), dp(2), dp(2))
        icon.layoutParams = iconParams
        try {
            icon.setImageDrawable(packageManager.getApplicationIcon(app.packageName))
        } catch (e: Exception) {
            icon.setImageResource(R.drawable.ic_bubble)
        }
        icon.setOnClickListener { openApp(app.packageName) }
        row.addView(icon)
    }

    private fun dockApps(): List<AppInfo> {
        val favPackages = Store.favourites(this)
        val favApps = favPackages.map { AppInfo(it, AppList.labelFor(this, it)) }
        val favSet = favPackages.toSet()
        val recent = AppList.recent(this, 8).filter { !favSet.contains(it.packageName) }
        val combined = favApps + recent
        if (combined.isNotEmpty()) return combined
        return AppList.launchable(this)
    }

    private fun launchApp(packageName: String): Boolean {
        return if (Store.floatingWindow(this)) {
            AppList.launchFloating(this, packageName)
        } else {
            AppList.launch(this, packageName)
        }
    }

    private fun openApp(packageName: String) {
        if (!launchApp(packageName)) {
            Toast.makeText(this, "অ্যাপ খোলা যায়নি", Toast.LENGTH_SHORT).show()
        }
        removeDock()
        removeCircular()
    }

    private fun goToPreviousRecent() {
        if (!AppList.hasUsageAccess(this)) {
            Toast.makeText(this, getString(R.string.need_usage), Toast.LENGTH_SHORT).show()
            return
        }
        val previous = AppList.previousRecent(this)
        if (previous == null) {
            Toast.makeText(this, "রিসেন্ট অ্যাপ পাওয়া যায়নি", Toast.LENGTH_SHORT).show()
            return
        }
        if (!launchApp(previous.packageName)) {
            Toast.makeText(this, "অ্যাপ খোলা যায়নি", Toast.LENGTH_SHORT).show()
        }
        removeDock()
        removeCircular()
    }

    private fun checkHiddenApp() {
        val hidden = Store.hiddenApps(this)
        if (hidden.isEmpty()) {
            bubbleView?.visibility = View.VISIBLE
            return
        }
        if (!AppList.hasUsageAccess(this)) return
        try {
            val usage = getSystemService(USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager
            val now = System.currentTimeMillis()
            val stats = usage.queryUsageStats(
                android.app.usage.UsageStatsManager.INTERVAL_DAILY,
                now - 5L * 60L * 1000L,
                now
            ) ?: return
            val top = stats.maxByOrNull { it.lastTimeUsed }?.packageName ?: return
            bubbleView?.visibility = if (hidden.contains(top)) View.GONE else View.VISIBLE
        } catch (e: Exception) {
        }
    }

    private fun stopBubble() {
        Store.setBubbleOn(this, false)
        removeDock()
        removeCircular()
        stopSelf()
    }
}
