package com.example.musify

import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.musify.songData.Artists
import com.squareup.picasso.Picasso
import de.hdodenhof.circleimageview.CircleImageView

class ArtistsAdapter(private val artistsList: List<Artists>): RecyclerView.Adapter<ArtistsAdapter.ArtistsViewHolder>() {
    private lateinit var myListener: OnItemClickListener
    interface OnItemClickListener {
        fun omItemClick(position: Int)
    }
    fun setOnItemClickListener(listener: OnItemClickListener) {
        myListener = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ArtistsViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_artists,parent,false)
        return ArtistsViewHolder(view,myListener)
    }

    override fun onBindViewHolder(holder: ArtistsViewHolder, position: Int) {
        if (artistsList[position].image == "" || artistsList[position].image == "/_i/share-image-2.png") {
            holder.image?.setImageResource(R.drawable.unknown)
        } else {
            Picasso.get().load(artistsList[position].image).into(holder.image)
        }
        holder.name?.text = Html.fromHtml(artistsList[position].name,Html.FROM_HTML_MODE_LEGACY)
    }

    override fun getItemCount(): Int {
        return artistsList.size
    }

    class ArtistsViewHolder(view: View,listener: OnItemClickListener): RecyclerView.ViewHolder(view) {
        val image: CircleImageView? = view.findViewById(R.id.profile_image)
        val name: TextView? = view.findViewById(R.id.artistNameText)

        init {
            view.setOnClickListener {
                listener.omItemClick(adapterPosition)
            }
        }
    }
}