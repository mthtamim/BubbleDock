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
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/**
 * BubbleDock v1.2: overlay is icon-only, no text anywhere in bubble/dock/actions.
 * BubbleDock v1.1: the grid panel is gone.
 * - Normally only the bubble circle is visible.
 * - Single tap on the bubble: optional actions popup (quick actions + groups).
 * - Double tap on the bubble: ONE single-line dock, big and horizontally
 *   scrollable. The dock is exactly one row, never wrapping to a second row.
 *   The row can be big: 64dp icons with labels, in a tall dock card.
 * - Tapping an app in the dock launches it and hides the dock.
 * - Double tap again, the close button, or tapping an app hides the dock.
 */
class BubbleService : Service() {
    private lateinit var windowManager: WindowManager
    private var bubbleView: ImageView? = null
    private var dockView: LinearLayout? = null
    private var dockRow: LinearLayout? = null
    private var actionsView: LinearLayout? = null
    private var trashView: TextView? = null
    private var searchQuery: String = ""
    private var groupSelected: String? = null
    private var dockOpen: Boolean = false
    private var actionsOpen: Boolean = false
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
            toggleActions()
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
        removeActions()
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

        override fun onTouch(view: View, event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = params.x
                    startY = params.y
                    moved = false
                    view.alpha = 1.0f
                    handler.removeCallbacks(idleRunnable)
                    showTrash()
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downX).toInt()
                    val dy = (event.rawY - downY).toInt()
                    if (kotlin.math.abs(dx) > 12 || kotlin.math.abs(dy) > 12) {
                        moved = true
                    }
                    if (moved) {
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
                    removeTrash()
                    if (!moved) {
                        val now = System.currentTimeMillis()
                        if (lastTapTime != 0L && now - lastTapTime < 350L) {
                            handler.removeCallbacks(singleTapRunnable)
                            lastTapTime = 0L
                            toggleDock()
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

    private fun toggleDock() {
        if (dockOpen) {
            removeDock()
        } else {
            removeActions()
            showDock()
        }
    }

    private fun toggleActions() {
        if (actionsOpen) {
            removeActions()
        } else {
            removeDock()
            showActions()
        }
    }

    private fun showDock() {
        if (dockView != null) return
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundResource(R.drawable.bg_panel)
        root.setPadding(dp(16), dp(16), dp(16), dp(16))

        val top = LinearLayout(this)
        top.orientation = LinearLayout.HORIZONTAL
        val searchButton = Button(this)
        searchButton.text = "\uD83D\uDD0D"
        searchButton.textSize = 18f
        searchButton.setBackgroundResource(R.drawable.bg_chip)
        searchButton.setTextColor(resources.getColor(R.color.bd_text, theme))
        val spacer = TextView(this)
        val spacerParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        spacer.layoutParams = spacerParams
        val close = Button(this)
        close.text = "✕"
        close.textSize = 18f
        close.setBackgroundResource(R.drawable.bg_chip)
        close.setTextColor(resources.getColor(R.color.bd_text, theme))
        close.setOnClickListener { removeDock() }
        top.addView(searchButton)
        top.addView(spacer)
        top.addView(close)
        root.addView(top)

        val search = EditText(this)
        search.visibility = View.GONE
        search.setTextColor(resources.getColor(R.color.bd_text, theme))
        searchButton.setOnClickListener {
            search.visibility = if (search.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            if (search.visibility == View.VISIBLE) search.requestFocus()
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
        root.addView(search)

        val scroll = HorizontalScrollView(this)
        scroll.isHorizontalScrollBarEnabled = false
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        scroll.addView(row)
        root.addView(scroll)
        dockRow = row
        refreshDockRow()

        val params = WindowManager.LayoutParams(
            screenWidth - dp(20),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        params.y = dp(16)
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
        dockOpen = false
        searchQuery = ""
        groupSelected = null
    }

    private fun refreshDockRow() {
        val row = dockRow ?: return
        row.removeAllViews()
        val apps = dockApps()
        if (apps.isEmpty()) {
            val note = ImageView(this)
            note.setImageResource(R.drawable.ic_bubble)
            val noteParams = LinearLayout.LayoutParams(dp(64), dp(64))
            note.layoutParams = noteParams
            row.addView(note)
            return
        }
        for (app in apps) {
            val item = LinearLayout(this)
            item.orientation = LinearLayout.VERTICAL
            item.gravity = Gravity.CENTER
            val itemParams = LinearLayout.LayoutParams(dp(76), LinearLayout.LayoutParams.WRAP_CONTENT)
            itemParams.setMargins(dp(4), dp(4), dp(4), dp(4))
            item.layoutParams = itemParams
            val icon = ImageView(this)
            val iconParams = LinearLayout.LayoutParams(dp(64), dp(64))
            icon.layoutParams = iconParams
            try {
                icon.setImageDrawable(packageManager.getApplicationIcon(app.packageName))
            } catch (e: Exception) {
                icon.setImageResource(R.drawable.ic_bubble)
            }
            item.addView(icon)
            item.setOnClickListener { openApp(app.packageName) }
            row.addView(item)
        }
    }

    private fun dockApps(): List<AppInfo> {
        if (searchQuery.isNotBlank()) {
            val query = searchQuery.lowercase()
            return AppList.launchable(this).filter { it.label.lowercase().contains(query) }
        }
        val selected = groupSelected
        if (selected != null) {
            val groups = Store.groups(this)
            val arr = groups.optJSONArray(selected)
            val apps = ArrayList<AppInfo>()
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val pkg = arr.optString(i)
                    if (pkg.isNotEmpty()) apps.add(AppInfo(pkg, AppList.labelFor(this, pkg)))
                }
            }
            return apps
        }
        val favPackages = Store.favourites(this)
        val favApps = favPackages.map { AppInfo(it, AppList.labelFor(this, it)) }
        val favSet = favPackages.toSet()
        val recent = AppList.recent(this, 8).filter { !favSet.contains(it.packageName) }
        return favApps + recent
    }

    private fun showActions() {
        if (actionsView != null) return
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundResource(R.drawable.bg_panel)
        root.setPadding(dp(16), dp(16), dp(16), dp(16))
        for (code in Store.quickActions(this)) {
            val button = Button(this)
            button.text = Actions.iconFor(code)
            button.textSize = 20f
            button.contentDescription = Actions.labelFor(code)
            button.setBackgroundResource(R.drawable.bg_chip)
            button.setTextColor(resources.getColor(R.color.bd_text, theme))
            button.setOnClickListener {
                Actions.run(this, code)
                removeActions()
            }
            root.addView(button)
        }
        val groups = Store.groups(this)
        val keys = groups.keys()
        val groupIcons = listOf("\uD83D\uDC65", "\uD83D\uDCDA", "\uD83C\uDFAE", "\uD83D\uDCBC", "\uD83C\uDFE0", "\u2B50")
        var groupIndex = 0
        while (keys.hasNext()) {
            val name = keys.next()
            val button = Button(this)
            button.text = groupIcons[groupIndex % groupIcons.size]
            button.textSize = 20f
            button.contentDescription = name
            button.setBackgroundResource(R.drawable.bg_chip)
            button.setTextColor(resources.getColor(R.color.bd_text, theme))
            button.setOnClickListener {
                groupSelected = name
                removeActions()
                showDock()
            }
            root.addView(button)
            groupIndex++
        }
        val close = Button(this)
        close.text = "✕"
        close.textSize = 20f
        close.setOnClickListener { removeActions() }
        root.addView(close)

        val width = dp(220)
        val x = (bubbleX - width).coerceAtLeast(16)
        val y = (bubbleY - dp(20)).coerceAtLeast(16)
        val params = WindowManager.LayoutParams(
            width,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = x
        params.y = y
        windowManager.addView(root, params)
        actionsView = root
        actionsOpen = true
    }

    private fun removeActions() {
        actionsView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
            }
        }
        actionsView = null
        actionsOpen = false
    }

    private fun openApp(packageName: String) {
        if (!AppList.launch(this, packageName)) {
            Toast.makeText(this, "অ্যাপ খোলা যায়নি", Toast.LENGTH_SHORT).show()
        }
        removeDock()
        removeActions()
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
        removeActions()
        stopSelf()
    }
}
