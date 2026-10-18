package com.example.simplelauncher

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
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
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSnapHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.example.simplelauncher.databinding.ActivityMainBinding
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private lateinit var prefs: Prefs
    private val cfg = UiConfig()
    private lateinit var pagesAdapter: PagesAdapter
    private lateinit var dockAdapter: AppAdapter
    private lateinit var searchAdapter: AppAdapter
    private lateinit var folderAdapter: AppAdapter
    private lateinit var recentsAdapter: RecentsAdapter

    private var allApps: List<AppItem> = emptyList()
    private var visibleApps: List<AppItem> = emptyList()
    private var dockApps: List<AppItem> = emptyList()
    private var openFolderId: String? = null
    private val dismissedAt = HashMap<String, Long>()
    private val iconStyleNames = arrayOf("Persegi bulat", "Lingkaran", "Asli (tanpa potong)")

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = loadApps()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        prefs = Prefs(this)
        cfg.iconStyle = prefs.iconStyle
        BadgeStore.enabled = prefs.badgesEnabled
        BadgeStore.onChange = { refreshAll() }

        val menuTop: (AppItem, View) -> Unit = { a, v -> showItemMenu(a, v, false) }
        val menuFolder: (AppItem, View) -> Unit = { a, v -> showItemMenu(a, v, true) }

        // Home pages
        pagesAdapter = PagesAdapter(cfg, ::onItemClick, menuTop) { showSettings() }
        b.pager.adapter = pagesAdapter
        b.pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) = updateDots(position)
        })
        b.dots.setOnLongClickListener { showSettings(); true }

        // Dock
        dockAdapter = AppAdapter(false, cfg, false, ::onItemClick, menuTop)
        b.dock.layoutManager = GridLayoutManager(this, 4)
        b.dock.adapter = dockAdapter

        // Search
        searchAdapter = AppAdapter(true, cfg, false, ::onItemClick, menuTop)
        b.searchResults.layoutManager = GridLayoutManager(this, 4)
        b.searchResults.adapter = searchAdapter
        b.searchBox.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) = filter(s?.toString().orEmpty())
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })

        // Folder
        folderAdapter = AppAdapter(true, cfg, false, { app ->
            closeFolder()
            launchApp(app)
        }, menuFolder)
        b.folderGrid.layoutManager = GridLayoutManager(this, 4)
        b.folderGrid.adapter = folderAdapter
        b.folderOverlay.setOnClickListener { closeFolder() }
        b.folderTitle.setOnClickListener { openFolderId?.let { renameFolder(it) } }

        setupRecents()
        setupHandleGesture()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    b.folderOverlay.visibility == View.VISIBLE -> closeFolder()
                    b.recentsOverlay.visibility == View.VISIBLE -> closeRecents()
                    else -> {
                        resetSearch()
                        b.pager.currentItem = 0
                    }
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
        BadgeStore.enabled = prefs.badgesEnabled
        refreshAll()
        if (b.recentsOverlay.visibility == View.VISIBLE) refreshRecents()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN) {
            closeFolder()
            closeRecents()
            resetSearch()
            b.pager.currentItem = 0
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        BadgeStore.onChange = null
        unregisterReceiver(packageReceiver)
    }

    private fun refreshAll() {
        pagesAdapter.refresh()
        dockAdapter.refresh()
        searchAdapter.refresh()
        folderAdapter.refresh()
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

        val hidden = prefs.hidden
        visibleApps = allApps.filter { it.packageName !in hidden }
        dockApps = resolveDockApps()
        rebuildHome()
    }

    private fun rebuildHome() {
        val byPkg = visibleApps.associateBy { it.packageName }
        val dockPkgs = dockApps.map { it.packageName }.toSet()

        val stored = prefs.folders
        if (visibleApps.isNotEmpty()) {
            stored.forEach { f -> f.pkgs.removeAll { it !in byPkg || it in dockPkgs } }
            stored.removeAll { it.pkgs.isEmpty() }
            prefs.folders = stored
        }

        val folderItems = stored.map { f ->
            val apps = f.pkgs.mapNotNull { byPkg[it] }
            AppItem(f.name, "folder:${f.id}", makeFolderIcon(apps), f.id, apps)
        }
        val inFolder = stored.flatMap { it.pkgs }.toSet()
        val topApps = visibleApps.filter { it.packageName !in dockPkgs && it.packageName !in inFolder }
        val pages = (folderItems + topApps).chunked(prefs.columns * prefs.rows)

        dockAdapter.submit(dockApps)
        b.pager.post {
            val h = b.pager.height - b.pager.paddingTop - b.pager.paddingBottom
            cfg.rowHeight = if (h > 0) max(h / prefs.rows, dp(84)) else 0
            pagesAdapter.columns = prefs.columns
            pagesAdapter.submit(pages)
            buildDots(pages.size)
        }
        filter(b.searchBox.text?.toString().orEmpty())

        openFolderId?.let { id ->
            if (stored.any { it.id == id }) openFolder(id) else closeFolder()
        }
    }

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
            .mapNotNull { p -> visibleApps.find { it.packageName == p } }
    }

    private fun makeFolderIcon(apps: List<AppItem>): Drawable {
        val size = 192
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = 0x66FFFFFF
        c.drawRoundRect(0f, 0f, size.toFloat(), size.toFloat(), size * 0.26f, size * 0.26f, p)
        val pad = 18
        val gap = 8
        val cell = (size - pad * 2 - gap * 2) / 3
        apps.take(9).forEachIndexed { i, a ->
            val l = pad + (i % 3) * (cell + gap)
            val t = pad + (i / 3) * (cell + gap)
            val d = a.icon.constantState?.newDrawable()?.mutate() ?: a.icon
            d.setBounds(l, t, l + cell, t + cell)
            d.draw(c)
        }
        return BitmapDrawable(resources, bmp)
    }

    private fun onItemClick(item: AppItem) {
        if (item.isFolder) item.folderId?.let { openFolder(it) } else launchApp(item)
    }

    private fun launchApp(app: AppItem) {
        packageManager.getLaunchIntentForPackage(app.packageName)?.let { startActivity(it) }
    }

    // ------------------------------------------------------ Menu tekan lama

    private fun showItemMenu(app: AppItem, anchor: View, inFolder: Boolean) {
        val inDock = dockApps.any { it.packageName == app.packageName }
        PopupMenu(this, anchor).apply {
            if (app.isFolder) {
                menu.add(0, 10, 0, "Ganti nama folder")
                menu.add(0, 11, 1, "Bubarkan folder")
            } else {
                menu.add(0, 1, 0, "Bagikan")
                menu.add(0, 2, 1, "Info aplikasi")
                menu.add(0, 3, 2, "Uninstal")
                menu.add(0, 4, 3, "Sembunyikan Aplikasi Ini")
                if (inFolder) menu.add(0, 5, 4, "Keluarkan dari folder")
                else if (!inDock) menu.add(0, 6, 4, "Masukkan ke folder")
            }
            setOnMenuItemClickListener {
                val uri = Uri.parse("package:${app.packageName}")
                when (it.itemId) {
                    1 -> shareApp(app)
                    2 -> startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri))
                    3 -> startActivity(Intent(Intent.ACTION_DELETE, uri))
                    4 -> hideApp(app)
                    5 -> removeFromFolder(app)
                    6 -> addToFolderDialog(app)
                    10 -> app.folderId?.let { id -> renameFolder(id) }
                    11 -> app.folderId?.let { id -> disbandFolder(id) }
                }
                true
            }
            show()
        }
    }

    private fun shareApp(app: AppItem) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(
                Intent.EXTRA_TEXT,
                "${app.label}: https://play.google.com/store/apps/details?id=${app.packageName}"
            )
        }
        startActivity(Intent.createChooser(send, null))
    }

    private fun hideApp(app: AppItem) {
        val h = prefs.hidden
        h.add(app.packageName)
        prefs.hidden = h
        Toast.makeText(this, "Disembunyikan. Kelola di Pengaturan launcher.", Toast.LENGTH_LONG).show()
        loadApps()
    }

    // -------------------------------------------------------------- Folder

    private fun promptName(initial: String, onOk: (String) -> Unit) {
        val et = EditText(this)
        et.setText(initial)
        et.setSelection(initial.length)
        AlertDialog.Builder(this)
            .setTitle("Nama folder")
            .setView(et)
            .setPositiveButton("Simpan") { _, _ ->
                onOk(et.text.toString().trim().ifEmpty { "Folder" })
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun addToFolderDialog(app: AppItem) {
        val folders = prefs.folders
        val names = folders.map { it.name } + "+ Folder baru…"
        AlertDialog.Builder(this)
            .setTitle("Masukkan ke folder")
            .setItems(names.toTypedArray()) { _, which ->
                if (which < folders.size) {
                    folders[which].pkgs.add(app.packageName)
                    prefs.folders = folders
                    rebuildHome()
                } else {
                    promptName("Folder") { name ->
                        folders.add(
                            FolderData(
                                UUID.randomUUID().toString().take(8),
                                name,
                                mutableListOf(app.packageName)
                            )
                        )
                        prefs.folders = folders
                        rebuildHome()
                    }
                }
            }
            .show()
    }

    private fun removeFromFolder(app: AppItem) {
        val folders = prefs.folders
        folders.forEach { it.pkgs.remove(app.packageName) }
        prefs.folders = folders
        rebuildHome()
    }

    private fun renameFolder(id: String) {
        val folders = prefs.folders
        val f = folders.find { it.id == id } ?: return
        promptName(f.name) { name ->
            f.name = name
            prefs.folders = folders
            rebuildHome()
        }
    }

    private fun disbandFolder(id: String) {
        val folders = prefs.folders
        folders.removeAll { it.id == id }
        prefs.folders = folders
        if (openFolderId == id) closeFolder()
        rebuildHome()
    }

    private fun openFolder(id: String) {
        val f = prefs.folders.find { it.id == id } ?: return
        val byPkg = visibleApps.associateBy { it.packageName }
        val apps = f.pkgs.mapNotNull { byPkg[it] }
        openFolderId = id
        b.folderTitle.text = f.name

        val lp = b.folderGrid.layoutParams
        lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
        b.folderGrid.layoutParams = lp
        folderAdapter.submit(apps)
        b.folderGrid.post {
            if (b.folderGrid.height > dp(420)) {
                val l2 = b.folderGrid.layoutParams
                l2.height = dp(420)
                b.folderGrid.layoutParams = l2
            }
        }

        if (b.folderOverlay.visibility != View.VISIBLE) {
            b.folderOverlay.alpha = 0f
            b.folderOverlay.visibility = View.VISIBLE
            b.folderOverlay.animate().alpha(1f).setDuration(160).start()
        }
    }

    private fun closeFolder() {
        openFolderId = null
        if (b.folderOverlay.visibility == View.VISIBLE) {
            b.folderOverlay.animate().alpha(0f).setDuration(130).withEndAction {
                b.folderOverlay.visibility = View.GONE
            }.start()
        }
    }

    // ------------------------------------------------------------ Pengaturan

    private fun showSettings() {
        val items = arrayOf(
            "Ukuran kisi: ${prefs.columns}×${prefs.rows}",
            "Gaya ikon: ${iconStyleNames[prefs.iconStyle]}",
            "Badge notifikasi: ${if (prefs.badgesEnabled) "Aktif" else "Mati"}",
            "Aplikasi tersembunyi (${prefs.hidden.size})"
        )
        AlertDialog.Builder(this)
            .setTitle("Pengaturan Transsion Launcher")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> chooseGrid()
                    1 -> chooseIconStyle()
                    2 -> toggleBadges()
                    3 -> manageHidden()
                }
            }
            .show()
    }

    private fun chooseGrid() {
        val options = listOf(4 to 5, 4 to 6, 5 to 5, 5 to 6)
        val names = options.map { "${it.first}×${it.second}" }.toTypedArray()
        val current = options.indexOfFirst { it.first == prefs.columns && it.second == prefs.rows }
        AlertDialog.Builder(this)
            .setTitle("Ukuran kisi (kolom × baris)")
            .setSingleChoiceItems(names, current) { d, which ->
                prefs.columns = options[which].first
                prefs.rows = options[which].second
                d.dismiss()
                rebuildHome()
            }
            .show()
    }

    private fun chooseIconStyle() {
        AlertDialog.Builder(this)
            .setTitle("Gaya ikon")
            .setSingleChoiceItems(iconStyleNames, prefs.iconStyle) { d, which ->
                prefs.iconStyle = which
                cfg.iconStyle = which
                d.dismiss()
                rebuildHome()
                refreshAll()
            }
            .show()
    }

    private fun toggleBadges() {
        val on = !prefs.badgesEnabled
        prefs.badgesEnabled = on
        BadgeStore.enabled = on
        if (on && !NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)) {
            Toast.makeText(
                this,
                "Aktifkan akses notifikasi untuk Transsion Launcher",
                Toast.LENGTH_LONG
            ).show()
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        refreshAll()
    }

    private fun manageHidden() {
        val hidden = prefs.hidden
        val apps = allApps.filter { it.packageName in hidden }
        if (apps.isEmpty()) {
            Toast.makeText(this, "Tidak ada aplikasi tersembunyi", Toast.LENGTH_SHORT).show()
            return
        }
        val checked = BooleanArray(apps.size) { true }
        AlertDialog.Builder(this)
            .setTitle("Aplikasi tersembunyi")
            .setMultiChoiceItems(apps.map { it.label }.toTypedArray(), checked) { _, i, c ->
                checked[i] = c
            }
            .setPositiveButton("Simpan") { _, _ ->
                val h = prefs.hidden
                apps.forEachIndexed { i, a -> if (!checked[i]) h.remove(a.packageName) }
                prefs.hidden = h
                loadApps()
            }
            .setNegativeButton("Batal", null)
            .show()
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
            searchAdapter.submit(visibleApps.filter { it.label.lowercase().contains(text) })
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
        val byPkg = visibleApps.associateBy { it.packageName }
        return last.entries
            .filter { (pkg, t) -> pkg != packageName && t > (dismissedAt[pkg] ?: 0L) }
            .sortedByDescending { it.value }
            .mapNotNull { byPkg[it.key] }
            .take(12)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
