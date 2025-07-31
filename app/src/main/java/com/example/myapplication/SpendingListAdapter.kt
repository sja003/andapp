package com.example.myapplication

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.Timestamp
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class SpendingListAdapter(
    private val spendingList: List<SpendingItem>,
    private val onItemClick: (SpendingItem) -> Unit
) : RecyclerView.Adapter<SpendingListAdapter.SpendingViewHolder>() {

    // 더 명확한 시간 표시 (전체 목록에서는 날짜도 포함)
    private val fullDateFormat = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
    private val numberFormat = NumberFormat.getNumberInstance(Locale.getDefault())

    // 상대적 시간 표시 함수 (전체 목록용 - 더 상세한 정보)
    private fun getDetailedRelativeTime(timestamp: Timestamp): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp.toDate().time
        val calendar = Calendar.getInstance()
        val timestampCalendar = Calendar.getInstance().apply {
            time = timestamp.toDate()
        }

        return when {
            // 오늘인 경우
            calendar.get(Calendar.DAY_OF_YEAR) == timestampCalendar.get(Calendar.DAY_OF_YEAR) &&
                    calendar.get(Calendar.YEAR) == timestampCalendar.get(Calendar.YEAR) -> {
                when {
                    diff < 60000 -> "방금 전"
                    diff < 3600000 -> "${diff / 60000}분 전"
                    diff < 86400000 -> "${diff / 3600000}시간 전"
                    else -> "오늘 ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(timestamp.toDate())}"
                }
            }
            // 어제인 경우
            calendar.apply { add(Calendar.DAY_OF_YEAR, -1) }.get(Calendar.DAY_OF_YEAR) ==
                    timestampCalendar.get(Calendar.DAY_OF_YEAR) &&
                    calendar.get(Calendar.YEAR) == timestampCalendar.get(Calendar.YEAR) -> {
                "어제 ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(timestamp.toDate())}"
            }
            // 그 외의 경우
            else -> fullDateFormat.format(timestamp.toDate())
        }
    }

    inner class SpendingViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var categoryText: TextView? = null
        var amountText: TextView? = null
        var assetText: TextView? = null
        var memoText: TextView? = null
        var dateText: TextView? = null
        var ocrBadge: TextView? = null

        init {
            itemView.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(spendingList[position])
                }
            }
        }

        fun bind(item: SpendingItem) {
            if (categoryText == null) {
                categoryText = itemView.findViewWithTag("category")
                amountText = itemView.findViewWithTag("amount")
                assetText = itemView.findViewWithTag("asset")
                memoText = itemView.findViewWithTag("memo")
                dateText = itemView.findViewWithTag("date")
                ocrBadge = itemView.findViewWithTag("ocr_badge")
            }

            categoryText?.text = "📂 ${item.category}"
            amountText?.text = "${numberFormat.format(item.amount)}원"
            assetText?.text = "💳 ${item.asset}"
            memoText?.text = if (item.memo.isNotEmpty()) "📝 ${item.memo}" else "메모 없음"

            // 개선된 시간 표시 - 상세한 상대적 시간 사용
            item.date?.let { timestamp ->
                dateText?.text = "📅 ${getDetailedRelativeTime(timestamp)}"
            }

            if (item.isOcrGenerated) {
                ocrBadge?.visibility = View.VISIBLE
                ocrBadge?.text = "🤖 OCR"
            } else {
                ocrBadge?.visibility = View.GONE
            }

            val backgroundColor = when (item.category) {
                "식비" -> android.graphics.Color.parseColor("#FFE0B2")
                "카페" -> android.graphics.Color.parseColor("#D7CCC8")
                "교통" -> android.graphics.Color.parseColor("#BBDEFB")
                "쇼핑" -> android.graphics.Color.parseColor("#F8BBD9")
                "문화생활" -> android.graphics.Color.parseColor("#E1BEE7")
                "의료" -> android.graphics.Color.parseColor("#FFCDD2")
                else -> android.graphics.Color.parseColor("#F5F5F5")
            }
            itemView.setBackgroundColor(backgroundColor)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SpendingViewHolder {
        val itemView = createItemView(parent)
        return SpendingViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: SpendingViewHolder, position: Int) {
        holder.bind(spendingList[position])
    }

    override fun getItemCount(): Int = spendingList.size

    private fun createItemView(parent: ViewGroup): View {
        val context = parent.context

        val cardView = androidx.cardview.widget.CardView(context).apply {
            layoutParams = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(24, 12, 24, 12)
            }
            radius = 16f
            cardElevation = 4f
        }

        val mainLayout = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(40, 32, 40, 32)
        }

        // 상단 레이아웃 (카테고리, 금액, OCR 뱃지)
        val topLayout = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }

        val categoryText = TextView(context).apply {
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = android.widget.LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
            )
            tag = "category"
        }

        val ocrBadge = TextView(context).apply {
            textSize = 12f
            setPadding(16, 8, 16, 8)
            setBackgroundColor(android.graphics.Color.parseColor("#E3F2FD"))
            setTextColor(android.graphics.Color.parseColor("#1976D2"))
            visibility = View.GONE
            tag = "ocr_badge"
        }

        val amountText = TextView(context).apply {
            textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(android.graphics.Color.parseColor("#D32F2F"))
            gravity = android.view.Gravity.END
            tag = "amount"
        }

        topLayout.addView(categoryText)
        topLayout.addView(ocrBadge)
        topLayout.addView(amountText)

        // 중간 레이아웃 (자산, 개선된 날짜/시간)
        val middleLayout = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            setPadding(0, 16, 0, 0)
        }

        val assetText = TextView(context).apply {
            textSize = 14f
            layoutParams = android.widget.LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
            )
            tag = "asset"
        }

        val dateText = TextView(context).apply {
            textSize = 14f
            gravity = android.view.Gravity.END
            setTextColor(android.graphics.Color.parseColor("#757575"))
            tag = "date"
        }

        middleLayout.addView(assetText)
        middleLayout.addView(dateText)

        // 메모 텍스트
        val memoText = TextView(context).apply {
            textSize = 14f
            setPadding(0, 12, 0, 0)
            setTextColor(android.graphics.Color.parseColor("#424242"))
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            tag = "memo"
        }

        // 레이아웃 조립
        mainLayout.addView(topLayout)
        mainLayout.addView(middleLayout)
        mainLayout.addView(memoText)
        cardView.addView(mainLayout)

        return cardView
    }
}