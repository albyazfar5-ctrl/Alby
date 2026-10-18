package com.example.simplelauncher

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

/** Satu halaman home = satu grid ikon (gaya HiOS / XOS: semua aplikasi di home). */
class PagesAdapter(
    private val onClick: (AppItem) -> Unit,
    private val onLongClick: (AppItem, View) -> Unit
) : RecyclerView.Adapter<PagesAdapter.VH>() {

    private var pages: List<List<AppItem>> = emptyList()

    fun submit(list: List<List<AppItem>>) {
        pages = list
        notifyDataSetChanged()
    }

    class VH(val rv: RecyclerView, val adapter: AppAdapter) : RecyclerView.ViewHolder(rv)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val rv = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_page, parent, false) as RecyclerView
        rv.layoutManager = GridLayoutManager(parent.context, 4)
        val adapter = AppAdapter(true, onClick, onLongClick)
        rv.adapter = adapter
        return VH(rv, adapter)
    }

    override fun onBindViewHolder(holder: VH, position: Int) =
        holder.adapter.submit(pages[position])

    override fun getItemCount() = pages.size
}
