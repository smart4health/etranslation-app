package de.hpi.etranslation

import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding

class ViewBindingViewHolder<T : ViewBinding>(
    val binding: T,
) : RecyclerView.ViewHolder(binding.root)
