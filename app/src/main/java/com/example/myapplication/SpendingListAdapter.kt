package com.example.myapplication

import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class SpendingListAdapter(
    private val spendingList: List<SpendingItem>,
    private val onItemClick: (SpendingItem) -> Unit
) : RecyclerView.Adapter<SpendingListAdapter.SpendingViewHolder>() {

    private val dateFormat = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
    private val numberFormat = NumberFormat.getInstance(Locale.KOREA)

    inner class SpendingViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val categoryText: TextView = itemView.findViewWithTag("category")
        private val amountText: TextView = itemView.findViewWithTag("amount")
        private val assetText: TextView = itemView.findViewWithTag("asset")
        private val memoText: TextView = itemView.findViewWithTag("memo")
        private val dateText: TextView = itemView.findViewWithTag("date")
        private val ocrBadge: TextView = itemView.findViewWithTag("ocr_badge")

        init {
            itemView.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(spendingList[position])
                }
            }
        }

        fun bind(item: SpendingItem) {
            categoryText.text = getCategoryIcon(item.category) + " " + item.category
            amountText.text = "-${numberFormat.format(item.amount)}원"
            assetText.text = getAssetIcon(item.asset) + " " + item.asset
            memoText.text = if (item.memo.isNotEmpty()) item.memo else "메모 없음"

            item.date?.let { timestamp ->
                dateText.text = dateFormat.format(timestamp.toDate())
            }

            if (item.isOcrGenerated) {
                ocrBadge.visibility = View.VISIBLE
                ocrBadge.text = "🤖"
            } else {
                ocrBadge.visibility = View.GONE
            }

            // 카테고리별 배경색
            val backgroundColor = getCategoryColor(item.category)
            itemView.setBackgroundColor(backgroundColor)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SpendingViewHolder {
        val itemView = createSpendingItemView(parent)
        return SpendingViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: SpendingViewHolder, position: Int) {
        holder.bind(spendingList[position])
    }

    override fun getItemCount(): Int = spendingList.size

    private fun createSpendingItemView(parent: ViewGroup): View {
        val context = parent.context

        val cardView = androidx.cardview.widget.CardView(context).apply {
            layoutParams = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 4, 0, 4)
            }
            radius = 12f
            cardElevation = 2f
        }

        val mainLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 12, 16, 12)
        }

        // 상단 (카테고리, OCR 뱃지, 금액)
        val topLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }

        val categoryText = TextView(context).apply {
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
            )
            tag = "category"
        }

        val ocrBadge = TextView(context).apply {
            textSize = 10f
            setPadding(6, 3, 6, 3)
            setBackgroundColor(Color.parseColor("#E3F2FD"))
            setTextColor(Color.parseColor("#1976D2"))
            visibility = View.GONE
            tag = "ocr_badge"
        }

        val amountText = TextView(context).apply {
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.parseColor("#D32F2F"))
            tag = "amount"
        }

        topLayout.addView(categoryText)
        topLayout.addView(ocrBadge)
        topLayout.addView(amountText)

        // 중간 (메모)
        val memoText = TextView(context).apply {
            textSize = 12f
            setTextColor(Color.parseColor("#666666"))
            setPadding(0, 4, 0, 4)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            tag = "memo"
        }

        // 하단 (결제수단, 날짜)
        val bottomLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val assetText = TextView(context).apply {
            textSize = 12f
            setTextColor(Color.parseColor("#999999"))
            layoutParams = LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
            )
            tag = "asset"
        }

        val dateText = TextView(context).apply {
            textSize = 12f
            setTextColor(Color.parseColor("#999999"))
            tag = "date"
        }

        bottomLayout.addView(assetText)
        bottomLayout.addView(dateText)

        mainLayout.addView(topLayout)
        mainLayout.addView(memoText)
        mainLayout.addView(bottomLayout)
        cardView.addView(mainLayout)

        return cardView
    }

    private fun getCategoryIcon(category: String): String {
        return when (category) {
            "식비" -> "🍽️"
            "카페" -> "☕"
            "교통" -> "🚗"
            "쇼핑" -> "🛍️"
            "문화생활" -> "🎬"
            "의료" -> "💊"
            "OCR" -> "🧾"
            else -> "📦"
        }
    }

    private fun getAssetIcon(asset: String): String {
        return when (asset) {
            "현금" -> "💵"
            "체크카드" -> "💳"
            "신용카드" -> "💎"
            "카카오페이" -> "💛"
            "토스" -> "💙"
            "OCR 인식" -> "🤖"
            else -> "💰"
        }
    }

    private fun getCategoryColor(category: String): Int {
        return when (category) {
            "식비" -> Color.parseColor("#FFF3E0")
            "카페" -> Color.parseColor("#EFEBE9")
            "교통" -> Color.parseColor("#E3F2FD")
            "쇼핑" -> Color.parseColor("#FCE4EC")
            "문화생활" -> Color.parseColor("#F3E5F5")
            "의료" -> Color.parseColor("#FFEBEE")
            else -> Color.parseColor("#F5F5F5")
        }
    }
}