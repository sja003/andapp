package com.example.myapplication

import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

class CalendarEventsAdapter(
    private val events: List<CalendarEvent>,
    private val onItemClick: (CalendarEvent) -> Unit
) : RecyclerView.Adapter<CalendarEventsAdapter.EventViewHolder>() {

    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    inner class EventViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var typeIndicator: View? = null
        var titleText: TextView? = null
        var timeText: TextView? = null
        var categoryText: TextView? = null
        var descriptionText: TextView? = null

        init {
            itemView.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(events[position])
                }
            }
        }

        fun bind(event: CalendarEvent) {
            if (titleText == null) {
                typeIndicator = itemView.findViewWithTag("type_indicator")
                titleText = itemView.findViewWithTag("title")
                timeText = itemView.findViewWithTag("time")
                categoryText = itemView.findViewWithTag("category")
                descriptionText = itemView.findViewWithTag("description")
            }

            titleText?.text = event.title
            timeText?.text = timeFormat.format(event.date.toDate())
            categoryText?.text = "📂 ${event.category}"

            if (event.description.isNotEmpty()) {
                descriptionText?.text = event.description
                descriptionText?.visibility = View.VISIBLE
            } else {
                descriptionText?.visibility = View.GONE
            }

            // 타입별 색상 및 아이콘 설정
            when (event.type) {
                EventType.LOCAL -> {
                    typeIndicator?.setBackgroundColor(Color.parseColor("#4CAF50"))
                    itemView.setBackgroundColor(Color.parseColor("#F1F8E9"))
                }
                EventType.GOOGLE -> {
                    typeIndicator?.setBackgroundColor(Color.parseColor("#2196F3"))
                    itemView.setBackgroundColor(Color.parseColor("#E3F2FD"))
                }
                EventType.SPENDING -> {
                    typeIndicator?.setBackgroundColor(Color.parseColor("#FF9800"))
                    itemView.setBackgroundColor(Color.parseColor("#FFF3E0"))
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventViewHolder {
        val itemView = createEventItemView(parent)
        return EventViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: EventViewHolder, position: Int) {
        holder.bind(events[position])
    }

    override fun getItemCount(): Int = events.size

    private fun createEventItemView(parent: ViewGroup): View {
        val context = parent.context

        val cardView = androidx.cardview.widget.CardView(context).apply {
            layoutParams = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 8, 0, 8)
            }
            radius = 12f
            cardElevation = 2f
        }

        val mainLayout = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            setPadding(20, 20, 20, 20)
            gravity = android.view.Gravity.CENTER_VERTICAL
        }

        // 타입 인디케이터
        val typeIndicator = View(context).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(12, 60)
            tag = "type_indicator"
        }

        // 컨텐츠 레이아웃
        val contentLayout = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            layoutParams = android.widget.LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
            )
            setPadding(24, 0, 0, 0)
        }

        // 상단 (제목, 시간)
        val topLayout = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }

        val titleText = TextView(context).apply {
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.parseColor("#212121"))
            layoutParams = android.widget.LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
            )
            tag = "title"
        }

        val timeText = TextView(context).apply {
            textSize = 14f
            setTextColor(Color.parseColor("#757575"))
            tag = "time"
        }

        topLayout.addView(titleText)
        topLayout.addView(timeText)

        // 카테고리
        val categoryText = TextView(context).apply {
            textSize = 13f
            setTextColor(Color.parseColor("#424242"))
            setPadding(0, 8, 0, 0)
            tag = "category"
        }

        // 설명
        val descriptionText = TextView(context).apply {
            textSize = 13f
            setTextColor(Color.parseColor("#616161"))
            setPadding(0, 4, 0, 0)
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            tag = "description"
        }

        contentLayout.addView(topLayout)
        contentLayout.addView(categoryText)
        contentLayout.addView(descriptionText)

        mainLayout.addView(typeIndicator)
        mainLayout.addView(contentLayout)
        cardView.addView(mainLayout)

        return cardView
    }
}