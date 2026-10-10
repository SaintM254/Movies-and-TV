package com.vidsource.app.ui

import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.vidsource.app.R
import com.vidsource.app.data.SourceProfile
import com.vidsource.app.util.FaviconStore

/**
 * Horizontal strip of source "channels" (favicon = profile switcher).
 */
class SourceSwitcherAdapter(
    private val faviconStore: FaviconStore,
    private val onClick: (SourceProfile) -> Unit
) : RecyclerView.Adapter<SourceSwitcherAdapter.Holder>() {

    private val items = mutableListOf<SourceProfile>()
    private var activeId: String? = null

    fun submit(list: List<SourceProfile>, activeId: String?) {
        items.clear()
        items.addAll(list)
        this.activeId = activeId
        notifyDataSetChanged()
    }

    fun setActiveId(id: String?) {
        val old = items.indexOfFirst { it.id == activeId }
        val new = items.indexOfFirst { it.id == id }
        activeId = id
        if (old >= 0) notifyItemChanged(old)
        if (new >= 0 && new != old) notifyItemChanged(new)
    }

    fun refreshIcons() = notifyDataSetChanged()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_source, parent, false)
        return Holder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(items[position])
    }

    inner class Holder(view: View) : RecyclerView.ViewHolder(view) {
        private val icon: ImageView = view.findViewById(R.id.sourceIcon)
        private val name: TextView = view.findViewById(R.id.sourceName)
        private val frame: View = view.findViewById(R.id.iconFrame)

        fun bind(source: SourceProfile) {
            name.text = source.name
            val bitmap: Bitmap = faviconStore.load(source.url)
                ?: faviconStore.letterAvatar(source.name, source.url)
            icon.setImageBitmap(bitmap)

            val selected = source.id == activeId
            frame.isSelected = selected
            name.isSelected = selected
            name.alpha = if (selected) 1f else 0.65f
            itemView.isActivated = selected

            itemView.setOnClickListener { onClick(source) }
            itemView.contentDescription = source.name
        }
    }
}
