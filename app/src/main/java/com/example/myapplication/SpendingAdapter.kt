package com.example.myapplication

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.databinding.ItemSpendingBinding
import java.text.SimpleDateFormat
import java.util.*

class SpendingAdapter(private var items: List<SpendingItem>) :
    RecyclerView.Adapter<SpendingAdapter.ViewHolder>() {

    inner class ViewHolder(private val binding: ItemSpendingBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: SpendingItem) {
            // 카테고리
            binding.categoryText.text = item.category.ifEmpty { "카테고리 없음" }

            // 금액
            binding.amountText.text = "${item.amount}원"

            // 자산
            binding.assetText.text = item.asset.ifEmpty { "자산 없음" }

            // 메모
            binding.memoText.text = item.memo.ifEmpty { "메모 없음" }

            // 날짜
            item.date?.let {
                val dateFormat = SimpleDateFormat("yyyy.MM.dd", Locale.getDefault())
                binding.dateText.text = dateFormat.format(it.toDate())
            } ?: run {
                binding.dateText.text = "날짜 없음"
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSpendingBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    fun updateList(newItems: List<SpendingItem>) {
        items = newItems
        notifyDataSetChanged()
    }
}
