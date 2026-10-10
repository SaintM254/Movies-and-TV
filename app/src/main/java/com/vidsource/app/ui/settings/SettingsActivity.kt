package com.vidsource.app.ui.settings

import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.vidsource.app.R
import com.vidsource.app.VidsourceApp
import com.vidsource.app.data.SourceProfile
import com.vidsource.app.util.FaviconStore
import kotlinx.coroutines.launch

/**
 * Manage source profiles: add website links manually, edit, delete,
 * and pick which one is the active "channel".
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var adapter: SourceListAdapter

    private val repo get() = (application as VidsourceApp).sources
    private val faviconStore get() = (application as VidsourceApp).favicons

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        recyclerView = findViewById(R.id.sourceList)
        emptyView = findViewById(R.id.emptyView)

        adapter = SourceListAdapter()
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        findViewById<MaterialButton>(R.id.addSourceButton).setOnClickListener { showEditDialog(null) }
        findViewById<MaterialButton>(R.id.doneButton).setOnClickListener { finish() }

        refresh()
    }

    private fun refresh() {
        val sources = repo.getSources()
        adapter.submit(sources)
        emptyView.visibility = if (sources.isEmpty()) View.VISIBLE else View.GONE
    }

    // ------------------------------------------------------------------
    // Add / edit dialog (TV-friendly: every field is focusable)
    // ------------------------------------------------------------------

    private fun showEditDialog(existing: SourceProfile?) {
        val view = layoutInflater.inflate(R.layout.dialog_source_edit, null)
        val nameInput = view.findViewById<EditText>(R.id.inputName)
        val urlInput = view.findViewById<EditText>(R.id.inputUrl)

        if (existing != null) {
            nameInput.setText(existing.name)
            urlInput.setText(existing.url)
        } else {
            urlInput.setText("https://")
        }

        val dialog = AlertDialog.Builder(this, R.style.Vidsource_AlertDialog)
            .setTitle(if (existing == null) R.string.add_source else R.string.edit_source)
            .setView(view)
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(R.string.cancel, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = nameInput.text.toString().trim()
                val url = urlInput.text.toString().trim()
                when {
                    name.isEmpty() -> nameInput.error = getString(R.string.error_name_required)
                    url.isEmpty() || url == "https://" ->
                        urlInput.error = getString(R.string.error_url_required)
                    else -> {
                        val normalized = SourceProfile.normalizeUrl(url)
                        if (!normalized.contains(".")) {
                            urlInput.error = getString(R.string.error_url_invalid)
                        } else {
                            if (existing == null) {
                                repo.add(SourceProfile.create(name, normalized))
                                Toast.makeText(this, R.string.source_added, Toast.LENGTH_SHORT).show()
                            } else {
                                repo.update(
                                    SourceProfile(existing.id, name, normalized)
                                )
                                Toast.makeText(this, R.string.source_updated, Toast.LENGTH_SHORT).show()
                            }
                            refresh()
                            dialog.dismiss()
                        }
                    }
                }
            }
        }
        dialog.show()
    }

    private fun confirmDelete(source: SourceProfile) {
        AlertDialog.Builder(this, R.style.Vidsource_AlertDialog)
            .setTitle(R.string.delete_source)
            .setMessage(getString(R.string.delete_confirm, source.name))
            .setPositiveButton(R.string.delete) { _, _ ->
                repo.delete(source.id)
                refresh()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ------------------------------------------------------------------
    // List
    // ------------------------------------------------------------------

    inner class SourceListAdapter : RecyclerView.Adapter<SourceListAdapter.Holder>() {

        private val items = mutableListOf<SourceProfile>()

        fun submit(list: List<SourceProfile>) {
            items.clear()
            items.addAll(list)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
            Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_source_setting, parent, false))

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: Holder, position: Int) {
            holder.bind(items[position])
        }

        inner class Holder(view: View) : RecyclerView.ViewHolder(view) {
            private val icon: ImageView = view.findViewById(R.id.sourceIcon)
            private val name: TextView = view.findViewById(R.id.sourceName)
            private val url: TextView = view.findViewById(R.id.sourceUrl)
            private val activeBadge: TextView = view.findViewById(R.id.activeBadge)

            fun bind(source: SourceProfile) {
                name.text = source.name
                url.text = source.url

                val isActive = repo.activeSourceId() == source.id ||
                    (repo.activeSourceId() == null && adapterPosition == 0)
                activeBadge.visibility = if (isActive) View.VISIBLE else View.GONE

                val bitmap: Bitmap = faviconStore.load(source.url)
                    ?: faviconStore.letterAvatar(source.name, source.url)
                icon.setImageBitmap(bitmap)
                if (faviconStore.load(source.url) == null) {
                    lifecycleScope.launch {
                        faviconStore.fetchRemoteIcon(source.url)
                        icon.setImageBitmap(
                            faviconStore.load(source.url)
                                ?: faviconStore.letterAvatar(source.name, source.url)
                        )
                    }
                }

                itemView.setOnClickListener {
                    repo.setActive(source.id)
                    Toast.makeText(
                        this@SettingsActivity,
                        getString(R.string.set_active, source.name),
                        Toast.LENGTH_SHORT
                    ).show()
                    refresh()
                }
                itemView.findViewById<ImageButton>(R.id.editButton).setOnClickListener {
                    showEditDialog(source)
                }
                itemView.findViewById<ImageButton>(R.id.deleteButton).setOnClickListener {
                    confirmDelete(source)
                }
            }
        }
    }
}
