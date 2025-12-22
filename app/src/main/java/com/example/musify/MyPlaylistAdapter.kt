package com.example.musify

import android.text.SpannableString
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.get
import androidx.core.view.size
import androidx.recyclerview.widget.RecyclerView
import com.squareup.picasso.Picasso

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
        if (playlistList[position].image != "") {
            Picasso.get().load(playlistList[position].image).into(holder.imageView)
        }
        holder.name?.text = playlistList[position].name
        "${playlistList[position].totalSongs} Songs".also { holder.item?.text = it }

        holder.threeDots?.setOnClickListener { view ->
            val wrapper = ContextThemeWrapper(view.context, R.style.CustomPopupThemeOverlay)
            val popup = PopupMenu(wrapper, view)
            popup.menuInflater.inflate(R.menu.playlist_menu, popup.menu)

            val typeface = ResourcesCompat.getFont(
                view.context,
                R.font.merriweathersans_regular
            )!!

            val textSizeSp = 15
            val textColor = ContextCompat.getColor(view.context, R.color.white)

            for (i in 0 until popup.menu.size) {
                val item = popup.menu[i]
                val title = SpannableString(item.title)
                title.setSpan(CustomTypefaceSpan(typeface), 0, title.length, 0)
                title.setSpan(AbsoluteSizeSpan(textSizeSp, true), 0, title.length, 0)
                title.setSpan(ForegroundColorSpan(textColor), 0, title.length, 0)

                item.title = title
            }

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
        val imageView: AppCompatImageView? = view.findViewById(R.id.playListImage)
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