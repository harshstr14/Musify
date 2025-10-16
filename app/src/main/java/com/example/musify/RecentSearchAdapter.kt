package com.example.musify

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class RecentSearchAdapter(private val items: MutableList<String>,private val onItemClick: (String) -> Unit)
    : RecyclerView.Adapter<RecentSearchAdapter.ViewHolder>(){

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_recent_search,parent,false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val query = items[position]
        holder.textView?.text = query
        holder.itemView.setOnClickListener { onItemClick(query) }
    }

    override fun getItemCount(): Int {
        return items.size
    }
    fun updateList(newItems: List<String>) {
        items.apply {
            clear()
            addAll(newItems)
        }
        notifyDataSetChanged()
    }
    inner class ViewHolder(view: View): RecyclerView.ViewHolder(view) {
        val textView: TextView? = view.findViewById(R.id.recentSearchTextView)
    }
}