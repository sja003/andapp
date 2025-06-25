package com.example.myapplication

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.databinding.ItemExpenseBinding
import com.example.myapplication.databinding.ItemHeaderBinding
import java.text.SimpleDateFormat
import java.util.*

// 날짜 헤더와 지출 항목을 묶는 sealed class
sealed class ExpenseListItem {
    data class DateHeader(val date: String) : ExpenseListItem()
    data class ExpenseItem(val expense: Expense) : ExpenseListItem()
}

class DailyAdapter(private var items: List<ExpenseListItem>) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ITEM = 1
    }

    // 날짜 헤더용 뷰홀더
    inner class HeaderViewHolder(private val binding: ItemHeaderBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(date: String) {
            binding.textDateHeader.text = date
        }
    }

    // 지출 항목 뷰홀더
    inner class ItemViewHolder(private val binding: ItemExpenseBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(expense: Expense) {
            binding.amountText.text = "${expense.amount}원"
            binding.categoryText.text = expense.category
            binding.memoText.text = expense.memo
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is ExpenseListItem.DateHeader -> TYPE_HEADER
            is ExpenseListItem.ExpenseItem -> TYPE_ITEM
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_HEADER) {
            val binding = ItemHeaderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            HeaderViewHolder(binding)
        } else {
            val binding = ItemExpenseBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            ItemViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is ExpenseListItem.DateHeader -> (holder as HeaderViewHolder).bind(item.date)
            is ExpenseListItem.ExpenseItem -> (holder as ItemViewHolder).bind(item.expense)
        }
    }

    override fun getItemCount(): Int = items.size

    // 지출 내역을 날짜 기준으로 묶어주는 함수
    fun updateList(expenses: List<Expense>) {
        val dateFormat = SimpleDateFormat("yyyy.MM.dd (E)", Locale.getDefault())
        items = expenses.groupBy { dateFormat.format(it.date) }
            .flatMap { (date, expenseList) ->
                listOf(ExpenseListItem.DateHeader(date)) +
                        expenseList.map { ExpenseListItem.ExpenseItem(it) }
            }

        notifyDataSetChanged()
    }
}
