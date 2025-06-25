package com.example.myapplication

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.R
import com.example.myapplication.MonthlySummary

class MonthlyAdapter(private val items: List<MonthlySummary>) :
    RecyclerView.Adapter<MonthlyAdapter.MonthlyViewHolder>() {

    inner class MonthlyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val monthText: TextView = itemView.findViewById(R.id.monthText)
        val amountText: TextView = itemView.findViewById(R.id.amountText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MonthlyViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_monthly_summary, parent, false)
        return MonthlyViewHolder(view)
    }

    override fun onBindViewHolder(holder: MonthlyViewHolder, position: Int) {
        val item = items[position]
        holder.monthText.text = formatMonth(item.yearMonth)
        holder.amountText.text = "${item.totalAmount}원"
    }

    override fun getItemCount(): Int = items.size

    private fun formatMonth(yearMonth: String): String {
        val parts = yearMonth.split("-")
        return "${parts[0]}년 ${parts[1]}월"
    }
}
