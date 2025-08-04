package com.example.myapplication

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AnalysisAdapter(private val items: List<AnalysisItem>) :
    RecyclerView.Adapter<AnalysisAdapter.AnalysisViewHolder>() {

    class AnalysisViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val categoryText: TextView = itemView.findViewById(R.id.tv_category)
        val amountText: TextView = itemView.findViewById(R.id.tv_predicted_spending)
        val monthText: TextView = itemView.findViewById(R.id.tv_month)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AnalysisViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_analysis, parent, false)
        return AnalysisViewHolder(view)
    }

    override fun onBindViewHolder(holder: AnalysisViewHolder, position: Int) {
        val item = items[position]
        val context = holder.itemView.context

        holder.categoryText.text = "${context.getString(R.string.category)}: ${item.category}"
        holder.amountText.text = "${context.getString(R.string.predicted_spending)}: ${String.format("%,d", item.predictedAmount)}원"
        holder.monthText.text = "${context.getString(R.string.month)}: ${item.month}"
    }

    override fun getItemCount() = items.size
}
