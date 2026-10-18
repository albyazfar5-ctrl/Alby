package com.example.simplelauncher

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class RecentsAdapter(
    private val cardWidth: Int,
    private val onClick: (AppItem) -> Unit
) : RecyclerView.Adapter<RecentsAdapter.VH>() {

    private val items = mutableListOf<AppItem>()

    fun submit(list: List<AppItem>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    fun removeAt(position: Int): AppItem {
        val item = items.removeAt(position)
        notifyItemRemoved(position)
        return item
    }

    fun isEmpty() = items.isEmpty()

    class VH(view: android.view.View) : RecyclerView.ViewHolder(view) {
        val smallIcon: ImageView = view.findViewById(R.id.smallIcon)
        val name: TextView = view.findViewById(R.id.name)
        val bigIcon: ImageView = view.findViewById(R.id.bigIcon)
        val card: android.view.View = view.findViewById(R.id.card)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_recent, parent, false)
        v.layoutParams = RecyclerView.LayoutParams(cardWidth, ViewGroup.LayoutParams.MATCH_PARENT)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.smallIcon.setImageDrawable(item.icon)
        holder.bigIcon.setImageDrawable(item.icon)
        holder.name.text = item.label
        holder.card.setOnClickListener { onClick(item) }
    }

    override fun getItemCount() = items.size
}
