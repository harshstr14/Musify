package com.example.musify

import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageView
import androidx.recyclerview.widget.RecyclerView
import com.squareup.picasso.Picasso
import java.util.Locale

class SuggestionSongAdapter(private val songList: List<SongItem>,private val onFavouriteClick: (SongItem) -> Unit)
    : RecyclerView.Adapter<SuggestionSongAdapter.SuggestionSongViewHolder>() {

    private lateinit var myListener: OnItemClickListener
    interface OnItemClickListener {
        fun onItemClick(position: Int)
    }
    fun setOnItemClickListener(listener: OnItemClickListener) {
        myListener = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SuggestionSongViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_suggestions,parent,false)
        return SuggestionSongViewHolder(view,myListener)
    }

    override fun onBindViewHolder(holder: SuggestionSongViewHolder, position: Int) {
        Picasso.get().load(songList[position].image[1].url).into(holder.image)
        holder.songName?.text = Html.fromHtml(songList[position].name,Html.FROM_HTML_MODE_LEGACY)
        val artistName = Html.fromHtml(songList[position].artist,Html.FROM_HTML_MODE_LEGACY)
        "Artist • $artistName".also { holder.artistName?.text = it }
        val duration = formatDuration(songList[position].duration)
        holder.duration?.text = duration

        if (songList[position].isFav) {
            holder.favoriteIcon?.setImageResource(R.drawable.heart_filled)
        } else {
            holder.favoriteIcon?.setImageResource(R.drawable.heart_outline)
        }

        holder.favoriteIcon?.setOnClickListener {
            songList[position].isFav = !songList[position].isFav
            notifyItemChanged(position)
            onFavouriteClick(songList[position]) // notify fragment
        }
    }

    override fun getItemCount(): Int {
        return songList.size
    }
    class SuggestionSongViewHolder(view: View,listener: OnItemClickListener): RecyclerView.ViewHolder(view) {
        val image: AppCompatImageView? = view.findViewById(R.id.songImage)
        val songName: TextView? = view.findViewById(R.id.songNameText)
        val artistName: TextView? = view.findViewById(R.id.artistNameText)
        val duration: TextView? = view.findViewById(R.id.durationText)
        val favoriteIcon: AppCompatImageView? = view.findViewById(R.id.appCompatImageView2)

        init {
            view.setOnClickListener {
                listener.onItemClick(bindingAdapterPosition)
            }
        }
    }
    fun formatDuration(seconds: Int): String {
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        return String.format(Locale.US,"%02d : %02d", minutes, remainingSeconds)
    }
}