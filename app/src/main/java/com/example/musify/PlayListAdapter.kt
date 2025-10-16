package com.example.musify

import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageView
import androidx.recyclerview.widget.RecyclerView
import com.squareup.picasso.Picasso

class PlayListAdapter(private val playlist: List<DataItem>): RecyclerView.Adapter<PlayListAdapter.PlayListViewHolder>() {
    private lateinit var myListener: OnItemClickListener
    interface OnItemClickListener {
        fun omItemClick(position: Int)
    }
    fun setOnItemClickListener(listener: OnItemClickListener) {
        myListener = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlayListViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_playlist,parent,false)
        return PlayListViewHolder(view,myListener)
    }

    override fun onBindViewHolder(holder: PlayListViewHolder, position: Int) {
        Picasso.get().load(playlist[position].image).into(holder.image)
        holder.songName?.text = Html.fromHtml(playlist[position].name,Html.FROM_HTML_MODE_LEGACY)
    }

    override fun getItemCount(): Int {
        return playlist.size
    }

    class PlayListViewHolder(view: View,listener: OnItemClickListener): RecyclerView.ViewHolder(view) {
        val image: AppCompatImageView? = view.findViewById(R.id.backgroundImageView)
        val songName: TextView? = view.findViewById(R.id.playListNameText)

        init {
            view.setOnClickListener {
                listener.omItemClick(bindingAdapterPosition)
            }
        }
    }
}