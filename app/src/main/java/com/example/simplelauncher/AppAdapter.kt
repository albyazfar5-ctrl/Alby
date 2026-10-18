package com.example.simplelauncher

import android.graphics.Outline
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class UiConfig {
    var iconStyle = 0   // 0 = persegi bulat, 1 = lingkaran, 2 = asli
    var rowHeight = 0
}

class AppAdapter(
    private val showLabel: Boolean,
    private val cfg: UiConfig,
    private val useRowHeight: Boolean,
    private val onClick: (AppItem) -> Unit,
    private val onLongClick: (AppItem, View) -> Unit
) : RecyclerView.Adapter<AppAdapter.VH>() {

    private var items: List<AppItem> = emptyList()

    fun submit(list: List<AppItem>) {
        items = list
        notifyDataSetChanged()
    }

    fun refresh() = notifyDataSetChanged()

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.icon)
        val label: TextView = view.findViewById(R.id.label)
        val badge: TextView = view.findViewById(R.id.badge)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.icon.setImageDrawable(item.icon)
        holder.label.text = item.label
        holder.label.visibility = if (showLabel) View.VISIBLE else View.GONE
        applyStyle(holder.icon)

        val count = if (!BadgeStore.enabled) 0 else if (item.isFolder) {
            item.folderApps.fold(0) { acc, a -> acc + (BadgeStore.counts[a.packageName] ?: 0) }
        } else {
            BadgeStore.counts[item.packageName] ?: 0
        }
        if (count > 0) {
            holder.badge.text = if (count > 99) "99+" else count.toString()
            holder.badge.visibility = View.VISIBLE
        } else {
            holder.badge.visibility = View.GONE
        }

        val lp = holder.itemView.layoutParams
        lp.height = if (useRowHeight && cfg.rowHeight > 0) cfg.rowHeight
        else ViewGroup.LayoutParams.WRAP_CONTENT
        holder.itemView.layoutParams = lp

        holder.itemView.setOnClickListener { onClick(item) }
        holder.itemView.setOnLongClickListener { onLongClick(item, it); true }
    }

    private fun applyStyle(iv: ImageView) {
        when (cfg.iconStyle) {
            1 -> {
                iv.setBackgroundColor(0x22FFFFFF)
                iv.outlineProvider = CIRCLE
                iv.clipToOutline = true
            }
            2 -> {
                iv.background = null
                iv.clipToOutline = false
            }
            else -> {
                iv.setBackgroundColor(0x22FFFFFF)
                iv.outlineProvider = ROUND
                iv.clipToOutline = true
            }
        }
    }

    override fun getItemCount() = items.size

    companion object {
        private val ROUND = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, view.width * 0.26f)
            }
        }
        private val CIRCLE = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setOval(0, 0, view.width, view.height)
            }
        }
    }
}
