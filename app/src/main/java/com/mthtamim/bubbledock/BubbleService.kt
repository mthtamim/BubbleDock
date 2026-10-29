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
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class BubbleService : Service() {
    private lateinit var windowManager: WindowManager
    private var bubbleView: ImageView? = null
    private var panelView: LinearLayout? = null
    private var panelContent: LinearLayout? = null
    private var trashView: TextView? = null
    private var searchQuery: String = ""
    private var panelOpen: Boolean = false
    private var lastTapTime: Long = 0L
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

    private val hideCheckRunnable = object : Runnable {
        override fun run() {
            checkHiddenApp()
            handler.postDelayed(this, 2000L)
        }
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
        removePanel()
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
        private var downTime: Long = 0L

        override fun onTouch(view: View, event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = params.x
                    startY = params.y
                    moved = false
                    downTime = System.currentTimeMillis()
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
                        val isDouble = now - lastTapTime < 350L
                        lastTapTime = now
                        if (isDouble) {
                            handleDoubleTap()
                        } else {
                            togglePanel()
                        }
                    } else {
                        val bubbleBottom = params.y + view.height
                        if (params.y > screenHeight - 220 || bubbleBottom > screenHeight - 40) {
                            stopBubble()
                            return true
                        }
                        snapToEdge(params, view)
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

    private fun handleDoubleTap() {
        val action = Store.doubleTapAction(this)
        if (action.isBlank()) {
            togglePanel()
            return
        }
        if (action.startsWith("app:")) {
            val pkg = action.removePrefix("app:")
            if (!AppList.launch(this, pkg)) {
                Toast.makeText(this, "অ্যাপ খোলা যায়নি", Toast.LENGTH_SHORT).show()
            }
        } else {
            Actions.run(this, action)
        }
        removePanel()
        panelOpen = false
    }

    private fun showTrash() {
        if (trashView != null) return
        val text = TextView(this)
        text.text = "✕ বাবল লুকাতে এখানে ছাড়ুন"
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

    private fun togglePanel() {
        if (panelOpen) {
            removePanel()
            panelOpen = false
        } else {
            showPanel()
            panelOpen = true
        }
    }

    private fun showPanel() {
        if (panelView != null) return
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundResource(R.drawable.bg_panel)
        root.setPadding(20, 20, 20, 20)

        val top = LinearLayout(this)
        top.orientation = LinearLayout.HORIZONTAL
        val title = TextView(this)
        title.text = getString(R.string.panel_title)
        title.setTextSize(18f)
        title.setTextColor(resources.getColor(R.color.bd_text, theme))
        val titleParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        title.layoutParams = titleParams
        val close = Button(this)
        close.text = getString(R.string.close)
        close.setOnClickListener {
            removePanel()
            panelOpen = false
        }
        top.addView(title)
        top.addView(close)
        root.addView(top)

        val search = EditText(this)
        search.hint = getString(R.string.search_hint)
        search.setTextColor(resources.getColor(R.color.bd_text, theme))
        search.setHintTextColor(resources.getColor(R.color.bd_text_secondary, theme))
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            }

            override fun afterTextChanged(s: Editable?) {
                searchQuery = s?.toString() ?: ""
                refreshPanelContent()
            }
        })
        root.addView(search)

        val scroll = ScrollView(this)
        val content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        scroll.addView(content)
        root.addView(scroll)
        panelContent = content
        refreshPanelContent()

        val width = (screenWidth * 9) / 10
        val height = (screenHeight * 3) / 4
        val params = WindowManager.LayoutParams(
            width, height,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.CENTER
        windowManager.addView(root, params)
        panelView = root
    }

    private fun removePanel() {
        panelView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
            }
        }
        panelView = null
        panelContent = null
        searchQuery = ""
    }

    private fun refreshPanelContent() {
        val content = panelContent ?: return
        content.removeAllViews()
        addSectionTitle(content, getString(R.string.recent_title))
        val recent = AppList.recent(this, 8)
        if (recent.isEmpty()) {
            addNote(content, getString(R.string.no_recent))
        } else {
            addAppRows(content, recent)
        }
        addSectionTitle(content, getString(R.string.fav_title))
        val favPackages = Store.favourites(this)
        val favApps = favPackages.map { AppInfo(it, AppList.labelFor(this, it)) }
        if (favApps.isEmpty()) {
            addNote(content, getString(R.string.no_fav))
        } else {
            addAppRows(content, favApps)
        }
        addSectionTitle(content, getString(R.string.groups_title))
        val groups = Store.groups(this)
        val keys = groups.keys()
        var hasGroup = false
        while (keys.hasNext()) {
            hasGroup = true
            val name = keys.next()
            val arr = groups.optJSONArray(name)
            addSectionTitle(content, "• " + name)
            if (arr != null) {
                val apps = ArrayList<AppInfo>()
                for (i in 0 until arr.length()) {
                    val pkg = arr.optString(i)
                    if (pkg.isNotEmpty()) apps.add(AppInfo(pkg, AppList.labelFor(this, pkg)))
                }
                addAppRows(content, apps)
            }
        }
        if (!hasGroup) {
            addNote(content, "এখনো কোনো গ্রুপ নেই। সেটিংস থেকে গ্রুপ বানাও।")
        }
        addSectionTitle(content, getString(R.string.actions_title))
        val actionCodes = Store.quickActions(this)
        for (code in actionCodes) {
            val button = appButton(Actions.labelFor(code))
            button.setOnClickListener {
                Actions.run(this, code)
                removePanel()
                panelOpen = false
            }
            content.addView(button)
        }
        addSectionTitle(content, getString(R.string.all_title))
        val all = AppList.launchable(this)
        val filtered = if (searchQuery.isBlank()) {
            all
        } else {
            all.filter { it.label.lowercase().contains(searchQuery.lowercase()) }
        }
        addAppRows(content, filtered)
    }

    private fun addSectionTitle(parent: LinearLayout, text: String) {
        val view = TextView(this)
        view.text = text
        view.setTextSize(16f)
        view.setTextColor(resources.getColor(R.color.bd_text, theme))
        view.setPadding(0, 20, 0, 8)
        parent.addView(view)
    }

    private fun addNote(parent: LinearLayout, text: String) {
        val view = TextView(this)
        view.text = text
        view.setTextColor(resources.getColor(R.color.bd_text_secondary, theme))
        view.setPadding(0, 4, 0, 4)
        parent.addView(view)
    }

    private fun appButton(label: String): Button {
        val button = Button(this)
        button.text = label
        button.setBackgroundResource(R.drawable.bg_chip)
        button.setTextColor(resources.getColor(R.color.bd_text, theme))
        button.setPadding(16, 16, 16, 16)
        return button
    }

    private fun addAppRows(parent: LinearLayout, apps: List<AppInfo>) {
        val useGrid = Store.panelLayout(this) == "grid"
        if (!useGrid) {
            for (app in apps) {
                val button = appButton(app.label)
                button.setOnClickListener {
                    openApp(app.packageName)
                }
                parent.addView(button)
            }
            return
        }
        var index = 0
        while (index < apps.size) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            for (col in 0 until 3) {
                if (index + col >= apps.size) break
                val app = apps[index + col]
                val button = appButton(app.label)
                val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                params.setMargins(4, 4, 4, 4)
                button.layoutParams = params
                button.setOnClickListener {
                    openApp(app.packageName)
                }
                row.addView(button)
            }
            parent.addView(row)
            index += 3
        }
    }

    private fun openApp(packageName: String) {
        if (!AppList.launch(this, packageName)) {
            Toast.makeText(this, "অ্যাপ খোলা যায়নি", Toast.LENGTH_SHORT).show()
        }
        removePanel()
        panelOpen = false
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
        removePanel()
        panelOpen = false
        stopSelf()
    }
}
