package com.example.myapplication

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.databinding.ItemMemoBinding

class MemoAdapter(private var items: List<Memo>) : RecyclerView.Adapter<MemoAdapter.ViewHolder>() {

    inner class ViewHolder(private val binding: ItemMemoBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: Memo) {
            binding.memoText.text = item.memo
            binding.dateText.text = item.date?.toDate().toString()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemMemoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    fun updateList(newItems: List<Memo>) {
        items = newItems
        notifyDataSetChanged()
    }
}

