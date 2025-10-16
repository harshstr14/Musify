package com.example.musify

import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageView
import androidx.recyclerview.widget.RecyclerView
import com.squareup.picasso.Picasso

class AlbumAdapter(private val albumList: List<DataItem>): RecyclerView.Adapter<AlbumAdapter.AlbumViewHolder>() {
    private lateinit var myListener: OnItemClickListener
    interface OnItemClickListener {
        fun omItemClick(position: Int)
    }
    fun setOnItemClickListener(listener: OnItemClickListener) {
        myListener = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlbumViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_songs,parent,false)
        return AlbumViewHolder(view,myListener)
    }

    override fun onBindViewHolder(holder: AlbumViewHolder, position: Int) {
        Picasso.get().load(albumList[position].image).into(holder.image)
        holder.songName?.text = Html.fromHtml(albumList[position].name,Html.FROM_HTML_MODE_LEGACY)
        "by  ${albumList[position].artist}".also { holder.artistName?.text = it }
    }

    override fun getItemCount(): Int {
        return albumList.size
    }
    class AlbumViewHolder(view: View,listener: OnItemClickListener): RecyclerView.ViewHolder(view) {
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