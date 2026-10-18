package com.example.simplelauncher

import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

/** Satu halaman home = satu grid ikon / folder. */
class PagesAdapter(
    private val cfg: UiConfig,
    private val onClick: (AppItem, View) -> Unit,
    private val onLongClick: (AppItem, View) -> Unit,
    private val onEmptyLongClick: () -> Unit
) : RecyclerView.Adapter<PagesAdapter.VH>() {

    private var pages: List<List<AppItem>> = emptyList()
    var columns: Int = 4

    fun submit(list: List<List<AppItem>>) {
        pages = list
        notifyDataSetChanged()
    }

    fun refresh() = notifyDataSetChanged()

    class VH(val rv: RecyclerView, val adapter: AppAdapter) : RecyclerView.ViewHolder(rv)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val rv = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_page, parent, false) as RecyclerView
        rv.layoutManager = GridLayoutManager(parent.context, columns)
        val adapter = AppAdapter(true, cfg, true, onClick, onLongClick)
        rv.adapter = adapter

        // Tekan lama di area kosong -> Pengaturan
        val gd = GestureDetector(rv.context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onLongPress(e: MotionEvent) {
                onEmptyLongClick()
            }
        })
        rv.setOnTouchListener { _, ev ->
            gd.onTouchEvent(ev)
            false
        }
        return VH(rv, adapter)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        (holder.rv.layoutManager as GridLayoutManager).spanCount = columns
        holder.adapter.submit(pages[position])
    }

    override fun getItemCount() = pages.size
}
