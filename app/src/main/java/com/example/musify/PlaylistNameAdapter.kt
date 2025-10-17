package com.example.musify

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageView
import androidx.recyclerview.widget.RecyclerView

class PlaylistNameAdapter(private val nameList: List<PlaylistData>,
                          private val onPlusIconClick: (Int) -> Unit)
    : RecyclerView.Adapter<PlaylistNameAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_playlistname,parent,false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.name?.text = nameList[position].name
        holder.plusIcon?.setOnClickListener {
            onPlusIconClick(position)
        }
    }

    override fun getItemCount(): Int {
        return nameList.size
    }

    class ViewHolder(view: View): RecyclerView.ViewHolder(view) {
        val name: TextView? = view.findViewById(R.id.playListName)
        val plusIcon: AppCompatImageView? = view.findViewById(R.id.plusIcon)
    }
}