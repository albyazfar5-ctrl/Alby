package com.example.simplelauncher

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.MediaStore
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.PopupMenu
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSnapHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.example.simplelauncher.databinding.ActivityMainBinding
import kotlin.math.abs
import kotlin.math.min

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private lateinit var pagesAdapter: PagesAdapter
    private lateinit var dockAdapter: AppAdapter
    private lateinit var searchAdapter: AppAdapter
    private lateinit var recentsAdapter: RecentsAdapter

    private var allApps: List<AppItem> = emptyList()
    private var dockApps: List<AppItem> = emptyList()
    private val dismissedAt = HashMap<String, Long>()
    private val perPage = 20

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = loadApps()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        // Home pages
        pagesAdapter = PagesAdapter(::launchApp, ::showAppMenu)
        b.pager.adapter = pagesAdapter
        b.pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) = updateDots(position)
        })

        // Dock
        dockAdapter = AppAdapter(false, ::launchApp, ::showAppMenu)
        b.dock.layoutManager = GridLayoutManager(this, 4)
        b.dock.adapter = dockAdapter

        // Search
        searchAdapter = AppAdapter(true, ::launchApp, ::showAppMenu)
        b.searchResults.layoutManager = GridLayoutManager(this, 4)
        b.searchResults.adapter = searchAdapter
        b.searchBox.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) = filter(s?.toString().orEmpty())
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })

        setupRecents()
        setupHandleGesture()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (b.recentsOverlay.visibility == View.VISIBLE) closeRecents()
                else {
                    resetSearch()
                    b.pager.currentItem = 0
                }
            }
        })

        val f = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        registerReceiver(packageReceiver, f)

        loadApps()
    }

    override fun onResume() {
        super.onResume()
        if (b.recentsOverlay.visibility == View.VISIBLE) refreshRecents()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN) {
            closeRecents()
            resetSearch()
            b.pager.currentItem = 0
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(packageReceiver)
    }

    // ---------------------------------------------------------------- Apps

    private fun loadApps() {
        val pm = packageManager
        val q = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        allApps = pm.queryIntentActivities(q, 0)
            .map {
                AppItem(
                    label = it.loadLabel(pm).toString(),
                    packageName = it.activityInfo.packageName,
                    icon = it.loadIcon(pm)
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }

        dockApps = resolveDockApps()
        val dockPkgs = dockApps.map { it.packageName }.toSet()
        val pages = allApps.filter { it.packageName !in dockPkgs }.chunked(perPage)

        pagesAdapter.submit(pages)
        dockAdapter.submit(dockApps)
        buildDots(pages.size)
        filter(b.searchBox.text?.toString().orEmpty())
    }

    /** Dock: Telepon, Pesan, Browser, Kamera (aplikasi default HP). */
    private fun resolveDockApps(): List<AppItem> {
        val intents = listOf(
            Intent(Intent.ACTION_DIAL),
            Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")),
            Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")),
            Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        )
        return intents
            .mapNotNull {
                packageManager.resolveActivity(it, PackageManager.MATCH_DEFAULT_ONLY)
                    ?.activityInfo?.packageName
            }
            .filter { it != "android" }
            .distinct()
            .mapNotNull { p -> allApps.find { it.packageName == p } }
    }

    private fun launchApp(app: AppItem) {
        packageManager.getLaunchIntentForPackage(app.packageName)?.let { startActivity(it) }
    }

    private fun showAppMenu(app: AppItem, anchor: View) {
        PopupMenu(this, anchor).apply {
            menu.add(0, 1, 0, getString(R.string.app_info))
            menu.add(0, 2, 1, getString(R.string.uninstall))
            setOnMenuItemClickListener {
                val uri = Uri.parse("package:${app.packageName}")
                when (it.itemId) {
                    1 -> startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri))
                    2 -> startActivity(Intent(Intent.ACTION_DELETE, uri))
                }
                true
            }
            show()
        }
    }

    // -------------------------------------------------------------- Search

    private fun filter(q: String) {
        val text = q.trim().lowercase()
        if (text.isEmpty()) {
            b.searchResults.visibility = View.GONE
            b.pager.visibility = View.VISIBLE
        } else {
            b.pager.visibility = View.INVISIBLE
            b.searchResults.visibility = View.VISIBLE
            searchAdapter.submit(allApps.filter { it.label.lowercase().contains(text) })
        }
    }

    private fun resetSearch() {
        b.searchBox.setText("")
        b.searchBox.clearFocus()
    }

    // ----------------------------------------------------------- Page dots

    private fun buildDots(n: Int) {
        b.dots.removeAllViews()
        if (n > 1) {
            repeat(n) {
                val v = View(this)
                v.setBackgroundResource(R.drawable.dot)
                v.layoutParams = LinearLayout.LayoutParams(dp(7), dp(7)).apply {
                    marginStart = dp(4); marginEnd = dp(4)
                }
                b.dots.addView(v)
            }
        }
        updateDots(b.pager.currentItem)
    }

    private fun updateDots(selected: Int) {
        for (i in 0 until b.dots.childCount) {
            b.dots.getChildAt(i).alpha = if (i == selected) 1f else 0.4f
        }
    }

    // ------------------------------------------------- Recents (bertumpuk)

    private fun setupRecents() {
        val screenW = resources.displayMetrics.widthPixels
        val cardW = (screenW * 0.62f).toInt()
        recentsAdapter = RecentsAdapter(cardW) { app ->
            closeRecents()
            launchApp(app)
        }

        val rv = b.recentsList
        rv.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        rv.adapter = recentsAdapter
        val side = (screenW - cardW) / 2
        rv.setPadding(side, 0, side, 0)
        LinearSnapHelper().attachToRecyclerView(rv)

        // Kartu saling menumpuk
        rv.addItemDecoration(object : RecyclerView.ItemDecoration() {
            override fun getItemOffsets(
                outRect: android.graphics.Rect, view: View,
                parent: RecyclerView, state: RecyclerView.State
            ) {
                if (parent.getChildAdapterPosition(view) > 0) outRect.left = -dp(34)
            }
        })

        rv.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) =
                applyStackEffect(recyclerView)
        })
        rv.addOnChildAttachStateChangeListener(object : RecyclerView.OnChildAttachStateChangeListener {
            override fun onChildViewAttachedToWindow(view: View) {
                view.post { applyStackEffect(rv) }
            }
            override fun onChildViewDetachedFromWindow(view: View) {}
        })

        // Geser kartu ke atas untuk menutup (seperti iOS)
        ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.UP) {
            override fun onMove(
                r: RecyclerView, v: RecyclerView.ViewHolder, t: RecyclerView.ViewHolder
            ) = false

            override fun onSwiped(vh: RecyclerView.ViewHolder, direction: Int) {
                val pos = vh.bindingAdapterPosition
                if (pos == RecyclerView.NO_POSITION) return
                val item = recentsAdapter.removeAt(pos)
                dismissedAt[item.packageName] = System.currentTimeMillis()
                updateRecentsEmptyState()
                rv.post { applyStackEffect(rv) }
            }
        }).attachToRecyclerView(rv)

        b.grantButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
        b.recentsOverlay.setOnClickListener { closeRecents() }
    }

    private fun applyStackEffect(rv: RecyclerView) {
        val center = rv.width / 2f
        for (i in 0 until rv.childCount) {
            val c = rv.getChildAt(i)
            val d = min(abs((c.left + c.right) / 2f - center) / c.width, 1.5f)
            val s = 1f - 0.10f * d
            c.scaleX = s
            c.scaleY = s
            c.translationZ = (1.5f - d) * 10f
        }
    }

    private fun setupHandleGesture() {
        val gd = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent) = true
            override fun onSingleTapUp(e: MotionEvent): Boolean { openRecents(); return true }
            override fun onFling(
                e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float
            ): Boolean {
                if (vy < -600f) { openRecents(); return true }
                return false
            }
        })
        b.handleArea.setOnTouchListener { v, ev ->
            val handled = gd.onTouchEvent(ev)
            if (ev.action == MotionEvent.ACTION_UP) v.performClick()
            handled
        }
    }

    private fun openRecents() {
        refreshRecents()
        b.recentsOverlay.alpha = 0f
        b.recentsOverlay.visibility = View.VISIBLE
        b.recentsOverlay.animate().alpha(1f).setDuration(180).start()
    }

    private fun closeRecents() {
        if (b.recentsOverlay.visibility != View.VISIBLE) return
        b.recentsOverlay.animate().alpha(0f).setDuration(150).withEndAction {
            b.recentsOverlay.visibility = View.GONE
        }.start()
    }

    private fun refreshRecents() {
        if (hasUsageAccess()) {
            recentsAdapter.submit(loadRecentApps())
            b.recentsList.scrollToPosition(0)
            b.recentsList.post { applyStackEffect(b.recentsList) }
        } else {
            recentsAdapter.submit(emptyList())
        }
        updateRecentsEmptyState()
    }

    private fun updateRecentsEmptyState() {
        val granted = hasUsageAccess()
        val empty = recentsAdapter.isEmpty()
        b.recentsEmpty.visibility = if (empty) View.VISIBLE else View.GONE
        b.grantButton.visibility = if (granted) View.GONE else View.VISIBLE
        b.recentsMessage.text = getString(
            if (granted) R.string.recents_empty else R.string.recents_need_permission
        )
    }

    private fun hasUsageAccess(): Boolean {
        val ops = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            ops.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName
            )
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    @Suppress("DEPRECATION")
    private fun loadRecentApps(): List<AppItem> {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(now - 24L * 3600 * 1000, now)
        val e = UsageEvents.Event()
        val last = HashMap<String, Long>()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            if (e.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                last[e.packageName] = e.timeStamp
            }
        }
        val byPkg = allApps.associateBy { it.packageName }
        return last.entries
            .filter { (pkg, t) -> pkg != packageName && t > (dismissedAt[pkg] ?: 0L) }
            .sortedByDescending { it.value }
            .mapNotNull { byPkg[it.key] }
            .take(12)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
