package com.example.simplelauncher

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AppAdapter(
    private val showLabel: Boolean,
    private val onClick: (AppItem) -> Unit,
    private val onLongClick: (AppItem, View) -> Unit
) : RecyclerView.Adapter<AppAdapter.VH>() {

    private var items: List<AppItem> = emptyList()

    fun submit(list: List<AppItem>) {
        items = list
        notifyDataSetChanged()
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.icon)
        val label: TextView = view.findViewById(R.id.label)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.icon.setImageDrawable(item.icon)
        holder.label.text = item.label
        holder.label.visibility = if (showLabel) View.VISIBLE else View.GONE
        holder.itemView.setOnClickListener { onClick(item) }
        holder.itemView.setOnLongClickListener { onLongClick(item, it); true }
    }

    override fun getItemCount() = items.size
}
