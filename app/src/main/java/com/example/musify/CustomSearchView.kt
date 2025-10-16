package com.example.musify

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.widget.addTextChangedListener

class CustomSearchView (context: Context, attributeSet: AttributeSet ?= null) : LinearLayout(context,attributeSet) {
    interface OnQueryTextListener {
        fun onQueryTextSubmit(query: String): Boolean
        fun onQueryTextChange(newText: String): Boolean
    }
    private var listener: OnQueryTextListener ?= null
    private val editText: EditText
    private val clearIcon: AppCompatImageView

    init {
        val view = LayoutInflater.from(context).inflate(R.layout.custom_search_view,this,true)
        editText = view.findViewById(R.id.editText)
        clearIcon = view.findViewById(R.id.clearText)

        editText.addTextChangedListener {
            listener?.onQueryTextChange(it.toString())
            clearIcon.visibility = if (it.isNullOrEmpty()) GONE else VISIBLE
        }

        editText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                listener?.onQueryTextSubmit(editText.text.toString().trim())
                hideKeyboard(editText)
                true
            } else false
        }

        clearIcon.setOnClickListener {
            editText.text.clear()
            listener?.onQueryTextChange("")
        }
    }
    fun setQuery(query: String, submit: Boolean) {
        editText.setText(query)
        editText.setSelection(query.length)
        if (submit) {
            listener?.onQueryTextSubmit(query)
            hideKeyboard(editText)
        } else {
            listener?.onQueryTextChange(query)
        }
    }
    fun setOnQueryTextListener(l: OnQueryTextListener) {
        listener = l
    }
    private fun hideKeyboard(view: View) {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken,0)
    }
}