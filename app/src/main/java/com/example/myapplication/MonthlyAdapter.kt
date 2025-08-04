package com.example.myapplication

import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.NumberFormat
import java.util.*

class MonthlyAdapter : RecyclerView.Adapter<MonthlyAdapter.MonthlyViewHolder>() {

    private val monthlySummaries = mutableListOf<MonthlySummary>()
    private val numberFormat = NumberFormat.getInstance(Locale.KOREA)

    fun updateData(newData: List<MonthlySummary>) {
        monthlySummaries.clear()
        monthlySummaries.addAll(newData)
        notifyDataSetChanged()
    }

    inner class MonthlyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val monthText: TextView = itemView.findViewWithTag("month")
        private val amountText: TextView = itemView.findViewWithTag("amount")
        private val barView: View = itemView.findViewWithTag("bar")

        fun bind(summary: MonthlySummary, maxAmount: Int) {
            monthText.text = summary.month
            amountText.text = "${numberFormat.format(summary.totalAmount)}원"

            // 프로그레스 바 너비 설정
            val progress = if (maxAmount > 0) {
                (summary.totalAmount.toFloat() / maxAmount * 100).toInt()
            } else 0

            val layoutParams = barView.layoutParams
            layoutParams.width = (progress * 3) // 3dp per percent
            barView.layoutParams = layoutParams
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MonthlyViewHolder {
        val itemView = createMonthlyItemView(parent)
        return MonthlyViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: MonthlyViewHolder, position: Int) {
        val maxAmount = monthlySummaries.maxOfOrNull { it.totalAmount } ?: 0
        holder.bind(monthlySummaries[position], maxAmount)
    }

    override fun getItemCount(): Int = monthlySummaries.size

    private fun createMonthlyItemView(parent: ViewGroup): View {
        val context = parent.context

        val mainLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 12, 0, 12)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        // 상단 (월, 금액)
        val topLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }

        val monthText = TextView(context).apply {
            textSize = 16f
            layoutParams = LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
            )
            tag = "month"
        }

        val amountText = TextView(context).apply {
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.parseColor("#D32F2F"))
            tag = "amount"
        }

        topLayout.addView(monthText)
        topLayout.addView(amountText)

        // 프로그레스 바
        val barBackground = View(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 8.dpToPx()
            )
            setBackgroundColor(Color.parseColor("#E0E0E0"))
        }

        val barView = View(context).apply {
            layoutParams = ViewGroup.LayoutParams(0, 8.dpToPx())
            setBackgroundColor(Color.parseColor("#1976D2"))
            tag = "bar"
        }

        val barContainer = android.widget.FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setPadding(0, 8, 0, 0)
        }

        barContainer.addView(barBackground)
        barContainer.addView(barView)

        mainLayout.addView(topLayout)
        mainLayout.addView(barContainer)

        return mainLayout
    }

    private fun Int.dpToPx(): Int {
        val density = android.content.res.Resources.getSystem().displayMetrics.density
        return (this * density).toInt()
    }
}