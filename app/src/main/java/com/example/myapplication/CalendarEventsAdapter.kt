package com.example.myapplication

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

/**
 * 캘린더 이벤트 어댑터 - 일정과 지출을 통합 표시
 */
class CalendarEventsAdapter(
    private val events: MutableList<CalendarEvent>,
    private val onDataChanged: () -> Unit
) : RecyclerView.Adapter<CalendarEventsAdapter.EventViewHolder>() {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    class EventViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val typeIcon: TextView
        val title: TextView
        val description: TextView
        val time: TextView
        val category: TextView

        init {
            // Tag로 뷰 찾기
            typeIcon = view.findViewWithTag("tv_event_type_icon") ?: TextView(view.context)
            title = view.findViewWithTag("tv_event_title") ?: TextView(view.context)
            description = view.findViewWithTag("tv_event_description") ?: TextView(view.context)
            time = view.findViewWithTag("tv_event_time") ?: TextView(view.context)
            category = view.findViewWithTag("tv_event_category") ?: TextView(view.context)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventViewHolder {
        val itemView = createEventItemView(parent)
        return EventViewHolder(itemView)
    }

    private fun createEventItemView(parent: ViewGroup): View {
        val context = parent.context
        val cardView = androidx.cardview.widget.CardView(context).apply {
            layoutParams = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 4, 0, 4)
            }
            radius = 8f
            cardElevation = 2f
            setCardBackgroundColor(Color.WHITE)
        }

        val mainLayout = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            setPadding(16, 12, 16, 12)
            gravity = android.view.Gravity.CENTER_VERTICAL
        }

        // 타입 아이콘
        val typeIcon = TextView(context).apply {
            tag = "tv_event_type_icon"
            textSize = 20f
            layoutParams = android.widget.LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 12, 0)
            }
        }

        // 내용 컨테이너
        val contentLayout = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            layoutParams = android.widget.LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
            )
        }

        // 제목
        val title = TextView(context).apply {
            tag = "tv_event_title"
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.parseColor("#1A1D29"))
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }

        // 설명
        val description = TextView(context).apply {
            tag = "tv_event_description"
            textSize = 14f
            setTextColor(Color.parseColor("#757575"))
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(0, 2, 0, 0)
        }

        // 시간과 카테고리 컨테이너
        val metaLayout = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            setPadding(0, 4, 0, 0)
        }

        val time = TextView(context).apply {
            tag = "tv_event_time"
            textSize = 12f
            setTextColor(Color.parseColor("#9E9E9E"))
        }

        val category = TextView(context).apply {
            tag = "tv_event_category"
            textSize = 12f
            setTextColor(Color.parseColor("#2196F3"))
            setPadding(8, 0, 0, 0)
        }

        // 뷰 조립
        metaLayout.addView(time)
        metaLayout.addView(category)

        contentLayout.addView(title)
        contentLayout.addView(description)
        contentLayout.addView(metaLayout)

        mainLayout.addView(typeIcon)
        mainLayout.addView(contentLayout)

        cardView.addView(mainLayout)
        return cardView
    }

    override fun onBindViewHolder(holder: EventViewHolder, position: Int) {
        val event = events[position]

        // 타입별 아이콘 설정
        val (icon, iconColor) = when (event.type) {
            EventType.LOCAL -> "📅" to Color.parseColor("#4CAF50")
            EventType.GOOGLE -> "🔗" to Color.parseColor("#FF9800")
            EventType.SPENDING -> "💰" to Color.parseColor("#F44336")
        }

        holder.typeIcon.text = icon
        holder.typeIcon.setTextColor(iconColor)

        // 내용 설정
        holder.title.text = event.title

        if (event.description.isNotEmpty()) {
            holder.description.text = event.description
            holder.description.visibility = View.VISIBLE
        } else {
            holder.description.visibility = View.GONE
        }

        // 시간 포맷팅
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        holder.time.text = timeFormat.format(event.date.toDate())

        // 카테고리 표시
        if (event.category.isNotEmpty()) {
            holder.category.text = "• ${event.category}"
            holder.category.visibility = View.VISIBLE
        } else {
            holder.category.visibility = View.GONE
        }

        // 클릭 이벤트 - 편집/상세보기
        holder.itemView.setOnClickListener {
            showEventDetail(event, holder.itemView.context)
        }

        // 길게 누르기 - 삭제
        holder.itemView.setOnLongClickListener {
            showDeleteConfirmation(event, position, holder.itemView.context)
            true
        }
    }

    private fun showEventDetail(event: CalendarEvent, context: Context) {
        val message = buildString {
            append("📝 제목: ${event.title}\n\n")
            if (event.description.isNotEmpty()) {
                append("📄 설명: ${event.description}\n\n")
            }
            append("📅 날짜: ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(event.date.toDate())}\n\n")
            append("🏷️ 카테고리: ${event.category}\n\n")
            append("🔗 타입: ${getTypeDisplayName(event.type)}")
        }

        AlertDialog.Builder(context)
            .setTitle("일정 상세")
            .setMessage(message)
            .setPositiveButton("확인", null)
            .show()
    }

    private fun getTypeDisplayName(type: EventType): String {
        return when (type) {
            EventType.LOCAL -> "로컬 일정"
            EventType.GOOGLE -> "구글 캘린더"
            EventType.SPENDING -> "지출 내역"
        }
    }

    private fun showDeleteConfirmation(event: CalendarEvent, position: Int, context: Context) {
        // 구글 캘린더 이벤트는 삭제 불가
        if (event.type == EventType.GOOGLE) {
            Toast.makeText(context, "구글 캘린더 이벤트는 구글 캘린더 앱에서 수정하세요", Toast.LENGTH_SHORT).show()
            return
        }

        val title = if (event.type == EventType.SPENDING) "지출 내역 삭제" else "일정 삭제"
        val message = "'${event.title}'을(를) 삭제하시겠습니까?"

        AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("삭제") { _, _ ->
                deleteEvent(event, position, context)
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun deleteEvent(event: CalendarEvent, position: Int, context: Context) {
        val currentUser = auth.currentUser ?: return

        val collection = when (event.type) {
            EventType.LOCAL -> "events"
            EventType.SPENDING -> "spending"
            EventType.GOOGLE -> return // 구글 이벤트는 삭제 불가
        }

        db.collection("users")
            .document(currentUser.uid)
            .collection(collection)
            .document(event.id)
            .delete()
            .addOnSuccessListener {
                events.removeAt(position)
                notifyItemRemoved(position)
                notifyItemRangeChanged(position, events.size)
                onDataChanged()

                val deleteMessage = when (event.type) {
                    EventType.LOCAL -> "✅ 일정이 삭제되었습니다"
                    EventType.SPENDING -> "✅ 지출 내역이 삭제되었습니다"
                    EventType.GOOGLE -> ""
                }
                Toast.makeText(context, deleteMessage, Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { exception ->
                Toast.makeText(context, "❌ 삭제 실패: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
    }

    override fun getItemCount() = events.size

    // 데이터 업데이트 메서드들
    fun addEvent(event: CalendarEvent) {
        events.add(event)
        events.sortBy { it.date.toDate() }
        notifyDataSetChanged()
    }

    fun updateEvent(eventId: String, updatedEvent: CalendarEvent) {
        val index = events.indexOfFirst { it.id == eventId }
        if (index >= 0) {
            events[index] = updatedEvent
            notifyItemChanged(index)
        }
    }

    fun clearEvents() {
        events.clear()
        notifyDataSetChanged()
    }

    fun getEventsForType(type: EventType): List<CalendarEvent> {
        return events.filter { it.type == type }
    }

    fun getEventCount(): Int = events.size
}