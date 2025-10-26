package com.example.musify

import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.RecyclerView

class MyPlaylistAdapter (private val playlistList: MutableList<PlaylistData>,
                         private val onRenameClick: (PlaylistData) -> Unit,
                         private val onRemoveClick: (PlaylistData) -> Unit)
    : RecyclerView.Adapter<MyPlaylistAdapter.ViewHolder>() {

    private lateinit var myListener: OnItemClickListener

    interface OnItemClickListener {
        fun onItemClick(position: Int)
    }

    fun setOnItemClickListener(listener: OnItemClickListener) {
        myListener = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_myplaylist,parent,false)
        return ViewHolder(view,myListener)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.name?.text = playlistList[position].name
        "${playlistList[position].totalSongs} Songs".also { holder.item?.text = it }

        holder.threeDots?.setOnClickListener { view ->
            val wrapper = ContextThemeWrapper(view.context, R.style.CustomPopupThemeOverlay)
            val popup = PopupMenu(wrapper, view)
            popup.menuInflater.inflate(R.menu.playlist_menu, popup.menu)
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_rename -> {
                        onRenameClick(playlistList[position])
                        true
                    }
                    R.id.action_remove -> {
                        onRemoveClick(playlistList[position])
                        true
                    }
                    else -> false
                }
            }
            view.post {
                popup.show()
            }
        }
    }

    override fun getItemCount(): Int {
        return playlistList.size
    }
    class ViewHolder(view: View,listener: OnItemClickListener): RecyclerView.ViewHolder(view) {
        val name: TextView? = view.findViewById(R.id.playlistNameText)
        val item: TextView? = view.findViewById(R.id.itemNameText)
        val threeDots: AppCompatImageView? = view.findViewById(R.id.threeDots)

        init {
            view.setOnClickListener {
                listener.onItemClick(bindingAdapterPosition)
            }
        }
    }
}