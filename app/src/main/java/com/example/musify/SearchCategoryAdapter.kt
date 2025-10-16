package com.example.musify

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class SearchCategoryAdapter(private val searchCategories: List<String>, private val onItemSelected: (Int) -> Unit) :
    RecyclerView.Adapter<SearchCategoryAdapter.SearchCategoryViewHolder>() {
    private var selectedPosition = 0
    private var lastPosition = 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SearchCategoryViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_search_category,parent,false)
        return SearchCategoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: SearchCategoryViewHolder, position: Int) {
        holder.name?.text = searchCategories[position]
        holder.container?.isSelected = position == selectedPosition
        holder.name?.setTextColor(if (position == selectedPosition) Color.WHITE else Color.GRAY)


        holder.itemView.setOnClickListener {
            val previous = selectedPosition
            lastPosition = selectedPosition
            selectedPosition = holder.bindingAdapterPosition
            if (selectedPosition != RecyclerView.NO_POSITION) {
                notifyItemChanged(previous)
                notifyItemChanged(position)
                onItemSelected(position)
            }
        }
    }

    override fun getItemCount(): Int {
        return searchCategories.size
    }

    inner class SearchCategoryViewHolder(view: View): RecyclerView.ViewHolder(view) {
        val name: TextView? = view.findViewById(R.id.CategoryName)
        val container: View? = view.findViewById(R.id.CategoryItem)
    }
}