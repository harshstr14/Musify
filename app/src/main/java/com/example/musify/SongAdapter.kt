package com.example.musify

import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageView
import androidx.recyclerview.widget.RecyclerView
import com.squareup.picasso.Picasso

class SongAdapter(private val songList: List<SongItem>): RecyclerView.Adapter<SongAdapter.SongViewHolder>() {
    private lateinit var myListener: OnItemClickListener
    interface OnItemClickListener {
        fun omItemClick(position: Int)
    }
    fun setOnItemClickListener(listener: OnItemClickListener) {
        myListener = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_songs,parent,false)
        return SongViewHolder(view,myListener)
    }

    override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
        Picasso.get().load(songList[position].image[1].url).into(holder.image)
        holder.songName?.text = Html.fromHtml(songList[position].name,Html.FROM_HTML_MODE_LEGACY)
        "by  ${songList[position].artist}".also { holder.artistName?.text = it }
    }

    override fun getItemCount(): Int {
        return songList.size
    }
    class SongViewHolder(view: View,listener: OnItemClickListener): RecyclerView.ViewHolder(view) {
        val image: AppCompatImageView? = view.findViewById(R.id.backgroundImageView)
        val songName: TextView? = view.findViewById(R.id.songNameText)
        val artistName: TextView? = view.findViewById(R.id.songArtistNameText)

        init {
            view.setOnClickListener {
                listener.omItemClick(bindingAdapterPosition)
            }
        }
    }
}