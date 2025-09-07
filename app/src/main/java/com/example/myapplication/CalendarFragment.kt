package com.example.myapplication

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.cardview.widget.CardView
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class CalendarFragment : Fragment() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    // ---- UI ----
    private lateinit var monthTitle: TextView
    private lateinit var monthGrid: RecyclerView

    private lateinit var selectedDateText: TextView
    private lateinit var dateTotalText: TextView
    private lateinit var dateIndicator: LinearLayout

    // 월별 통계 참조
    private lateinit var eventsStatsView: View
    private lateinit var spendingStatsView: View
    private lateinit var activeDaysStatsView: View

    // ---- 상태 ----
    private var selectedDate: Calendar = Calendar.getInstance()
    private val monthCursor: Calendar = Calendar.getInstance() // 현재 표시 월

    // 데코 데이터(yyyy-MM-dd)
    private val spendDayKeys = mutableSetOf<String>()
    private val eventDayKeys = mutableSetOf<String>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return createCalendarLayout()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupInitialState()
        renderMonth()
        loadTodayData()
        loadMonthDecorDataAndRefresh() // 진입 시 데코 로드
    }

    override fun onResume() {
        super.onResume()
        loadDateData(selectedDate)
        loadMonthDecorDataAndRefresh()
    }

    // ---------- 레이아웃 빌드 ----------

    private fun createCalendarLayout(): View {
        val context = requireContext()
        val scroll = androidx.core.widget.NestedScrollView(context)
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }

        root.addView(createHeaderCard())
        root.addView(createMonthlyStatsCard())
        root.addView(createLegendCard())
        root.addView(createCalendarCard()) // 커스텀 달력
        root.addView(createDateInfoCard())

        scroll.addView(root)
        return scroll
    }

    private fun createHeaderCard(): View {
        val context = requireContext()
        val card = CardView(context).apply {
            layoutParams = ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, 0, 16)
            }
            radius = 16f
            cardElevation = 4f
        }
        val box = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 20)
        }
        val title = TextView(context).apply {
            text = "📅 스마트 캘린더"
            textSize = 20f
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        val sub = TextView(context).apply {
            text = "일정과 지출을 한 번에 관리하세요"
            textSize = 14f
            setPadding(0, 4, 0, 0)
        }
        box.addView(title); box.addView(sub)
        card.addView(box)
        return card
    }

    private fun createMonthlyStatsCard(): View {
        val context = requireContext()
        val card = CardView(context).apply {
            layoutParams = ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, 0, 16)
            }
            radius = 12f
            cardElevation = 2f
            setCardBackgroundColor(Color.parseColor("#F8F9FA"))
        }
        val box = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 16, 20, 16)
        }
        val title = TextView(context).apply {
            text = "📈 이번 달 요약"
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.parseColor("#1A1D29"))
        }
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 12, 0, 0)
        }

        val eventsStats = createStatsItem(context, "📅", "0", "일정")
        val divider1 = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(1, 30.dpToPx()).apply { setMargins(16, 0, 16, 0) }
            setBackgroundColor(Color.parseColor("#E0E0E0"))
        }
        val spendingStats = createStatsItem(context, "💰", "￦0", "지출")
        val divider2 = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(1, 30.dpToPx()).apply { setMargins(16, 0, 16, 0) }
            setBackgroundColor(Color.parseColor("#E0E0E0"))
        }
        val activeDaysStats = createStatsItem(context, "🎆", "0", "활동일")

        eventsStatsView = eventsStats
        spendingStatsView = spendingStats
        activeDaysStatsView = activeDaysStats

        row.addView(eventsStats); row.addView(divider1)
        row.addView(spendingStats); row.addView(divider2)
        row.addView(activeDaysStats)

        box.addView(title); box.addView(row)
        card.addView(box)

        loadMonthlyStats(eventsStatsView, spendingStatsView, activeDaysStatsView)
        return card
    }

    private fun createStatsItem(context: Context, icon: String, value: String, label: String): View {
        val cont = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val iconText = TextView(context).apply { text = icon; textSize = 20f; gravity = Gravity.CENTER }
        val valueText = TextView(context).apply {
            text = value; textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.parseColor("#2196F3"))
            gravity = Gravity.CENTER
            tag = "value_$label"
        }
        val labelText = TextView(context).apply { text = label; textSize = 12f; setTextColor(Color.parseColor("#757575")); gravity = Gravity.CENTER }
        cont.addView(iconText); cont.addView(valueText); cont.addView(labelText)
        return cont
    }

    private fun createLegendCard(): View {
        val context = requireContext()
        val card = CardView(context).apply {
            layoutParams = ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, 0, 16)
            }
            radius = 12f
            cardElevation = 2f
        }
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(16, 12, 16, 12)
        }
        val legend = TextView(context).apply {
            text = "📅 일정    💰 지출    🎆 둘 다"
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val addBtn = Button(context).apply {
            text = "📅 일정 추가"; textSize = 12f
            setOnClickListener { showAddEventDialog() }
            setBackgroundColor(Color.parseColor("#4CAF50"))
            setTextColor(Color.WHITE)
        }
        row.addView(legend); row.addView(addBtn)
        card.addView(row)
        return card
    }

    private fun createCalendarCard(): View {
        val context = requireContext()
        val card = CardView(context).apply {
            layoutParams = ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, 0, 16)
            }
            radius = 16f
            cardElevation = 3f
        }
        val box = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 20)
        }

        // 상단: 이전/월 타이틀/다음
        val topRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val prev = ImageButton(context).apply {
            setImageResource(android.R.drawable.ic_media_previous)
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener { shiftMonth(-1) }
        }
        monthTitle = TextView(context).apply {
            textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val next = ImageButton(context).apply {
            setImageResource(android.R.drawable.ic_media_next)
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener { shiftMonth(1) }
        }
        topRow.addView(prev); topRow.addView(monthTitle); topRow.addView(next)

        // 요일 헤더
        val headerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 12, 0, 8)
        }
        val weekdays = listOf("일","월","화","수","목","금","토")
        weekdays.forEach {
            headerRow.addView(TextView(context).apply {
                text = it; gravity = Gravity.CENTER; setTextColor(Color.parseColor("#616161"))
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }

        // 7열 그리드
        monthGrid = RecyclerView(context).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            layoutManager = GridLayoutManager(context, 7)
            adapter = DayCellAdapter(
                onClick = { day -> onDayClicked(day) }
            )
        }

        box.addView(topRow)
        box.addView(headerRow)
        box.addView(monthGrid)
        card.addView(box)
        return card
    }

    private fun createDateInfoCard(): View {
        val context = requireContext()
        val card = CardView(context).apply {
            layoutParams = ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            radius = 16f
            cardElevation = 3f
        }
        val box = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 20)
        }
        selectedDateText = TextView(context).apply {
            text = "오늘"
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        dateTotalText = TextView(context).apply {
            text = "￦0"
            textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.parseColor("#4CAF50"))
            setPadding(0, 8, 0, 8)
        }
        dateIndicator = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 8, 0, 8)
        }
        val detailBtn = Button(context).apply {
            text = "📊 자세히 보기"
            setOnClickListener { showDateDetailBottomSheet() }
            setBackgroundColor(Color.parseColor("#4CAF50"))
            setTextColor(Color.WHITE)
        }
        box.addView(selectedDateText); box.addView(dateTotalText); box.addView(dateIndicator); box.addView(detailBtn)
        card.addView(box)
        return card
    }

    // ---------- 동작 ----------

    private fun setupInitialState() {
        val df = SimpleDateFormat("MM월 dd일 (E)", Locale.KOREAN)
        selectedDateText.text = df.format(selectedDate.time)
        monthCursor.timeInMillis = System.currentTimeMillis()
    }

    private fun shiftMonth(diff: Int) {
        monthCursor.add(Calendar.MONTH, diff)
        renderMonth()
        loadMonthDecorDataAndRefresh()
    }

    private fun renderMonth() {
        val ymFormat = SimpleDateFormat("yyyy년 M월", Locale.KOREAN)
        monthTitle.text = ymFormat.format(monthCursor.time)

        val days = buildMonthCells(monthCursor)
        (monthGrid.adapter as DayCellAdapter).submit(days)
    }

    private fun buildMonthCells(monthCal: Calendar): List<DayCell> {
        val cal = Calendar.getInstance().apply {
            timeInMillis = monthCal.timeInMillis
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }

        val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK) // 1=일 ... 7=토
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)

        val list = mutableListOf<DayCell>()
        val before = (firstDayOfWeek - Calendar.SUNDAY + 7) % 7 // 일=0 기준
        cal.add(Calendar.DAY_OF_MONTH, -before)
        repeat(before) {
            list.add(DayCell(date = cal.time, inMonth = false))
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }

        repeat(daysInMonth) {
            list.add(DayCell(date = cal.time, inMonth = true))
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }

        while (list.size % 7 != 0 || list.size < 42) {
            list.add(DayCell(date = cal.time, inMonth = false))
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }

        return list
    }

    private fun onDayClicked(cell: DayCell) {
        if (!cell.inMonth) {
            monthCursor.time = cell.date
            renderMonth()
            loadMonthDecorDataAndRefresh()
            return
        }
        selectedDate = Calendar.getInstance().apply { time = cell.date }
        val df = SimpleDateFormat("MM월 dd일 (E)", Locale.KOREAN)
        selectedDateText.text = df.format(selectedDate.time)
        loadDateData(selectedDate)
    }

    private fun loadTodayData() {
        val today = Calendar.getInstance()
        selectedDate = today
        val df = SimpleDateFormat("MM월 dd일 (E)", Locale.KOREAN)
        selectedDateText.text = df.format(selectedDate.time)
        loadDateData(selectedDate)
    }

    // ---------- 데이터 로드 ----------

    private fun loadMonthlyStats(eventsStats: View, spendingStats: View, activeDaysStats: View) {
        val user = auth.currentUser ?: return
        lifecycleScope.launch {
            try {
                val cal = Calendar.getInstance().apply {
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }
                val monthStart = cal.time
                cal.add(Calendar.MONTH, 1); cal.add(Calendar.DAY_OF_MONTH, -1)
                cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59)
                cal.set(Calendar.SECOND, 59); cal.set(Calendar.MILLISECOND, 999)
                val monthEnd = cal.time

                val events = db.collection("users").document(user.uid).collection("events")
                    .whereGreaterThanOrEqualTo("date", Timestamp(monthStart))
                    .whereLessThanOrEqualTo("date", Timestamp(monthEnd))
                    .get().await()

                val spend = db.collection("users").document(user.uid).collection("spending")
                    .whereGreaterThanOrEqualTo("date", Timestamp(monthStart))
                    .whereLessThanOrEqualTo("date", Timestamp(monthEnd))
                    .get().await()

                val totalEvents = events.size()
                var totalSpending = 0
                val active = mutableSetOf<String>()

                for (d in spend.documents) {
                    totalSpending += (d.getLong("amount") ?: 0).toInt()
                    d.getTimestamp("date")?.toDate()?.let {
                        active.add(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(it))
                    }
                }
                for (d in events.documents) {
                    d.getTimestamp("date")?.toDate()?.let {
                        active.add(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(it))
                    }
                }

                val nf = NumberFormat.getInstance(Locale.KOREA)
                eventsStats.findViewWithTag<TextView>("value_일정")?.text = "$totalEvents"
                spendingStats.findViewWithTag<TextView>("value_지출")?.text = "￦${nf.format(totalSpending)}"
                activeDaysStats.findViewWithTag<TextView>("value_활동일")?.text = "${active.size}"

            } catch (e: Exception) {
                Log.e("Calendar", "월 통계 로드 실패", e)
            }
        }
    }

    private fun loadDateData(sel: Calendar) {
        val user = auth.currentUser ?: return
        lifecycleScope.launch {
            try {
                val start = Calendar.getInstance().apply {
                    timeInMillis = sel.timeInMillis
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }
                val end = Calendar.getInstance().apply {
                    timeInMillis = sel.timeInMillis
                    set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999)
                }

                val spend = db.collection("users").document(user.uid).collection("spending")
                    .whereGreaterThanOrEqualTo("date", Timestamp(start.time))
                    .whereLessThanOrEqualTo("date", Timestamp(end.time))
                    .get().await()

                var total = 0; var spendCount = 0
                for (d in spend.documents) {
                    total += (d.getLong("amount") ?: 0).toInt()
                    spendCount++
                }

                val events = db.collection("users").document(user.uid).collection("events")
                    .whereGreaterThanOrEqualTo("date", Timestamp(start.time))
                    .whereLessThanOrEqualTo("date", Timestamp(end.time))
                    .get().await()
                val eventCount = events.size()

                val nf = NumberFormat.getInstance(Locale.KOREA)
                dateTotalText.text = "￦${nf.format(total)}"
                updateDateIndicator(spendCount, eventCount)

            } catch (e: Exception) {
                dateTotalText.text = "데이터 로드 실패"
                Log.e("Calendar", "날짜별 데이터 로드 실패", e)
            }
        }
    }

    /** 현재 표시 월의 지출/일정 날짜 집합을 갱신하고 캘린더를 리프레시 */
    private fun loadMonthDecorDataAndRefresh() {
        val user = auth.currentUser ?: return
        lifecycleScope.launch {
            try {
                val calStart = Calendar.getInstance().apply {
                    timeInMillis = monthCursor.timeInMillis
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }
                val calEnd = Calendar.getInstance().apply {
                    timeInMillis = monthCursor.timeInMillis
                    set(Calendar.DAY_OF_MONTH, monthCursor.getActualMaximum(Calendar.DAY_OF_MONTH))
                    set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999)
                }

                val spend = db.collection("users").document(user.uid).collection("spending")
                    .whereGreaterThanOrEqualTo("date", Timestamp(calStart.time))
                    .whereLessThanOrEqualTo("date", Timestamp(calEnd.time))
                    .get().await()
                val events = db.collection("users").document(user.uid).collection("events")
                    .whereGreaterThanOrEqualTo("date", Timestamp(calStart.time))
                    .whereLessThanOrEqualTo("date", Timestamp(calEnd.time))
                    .get().await()

                spendDayKeys.clear()
                for (d in spend.documents) {
                    d.getTimestamp("date")?.toDate()?.let {
                        spendDayKeys.add(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(it))
                    }
                }
                eventDayKeys.clear()
                for (d in events.documents) {
                    d.getTimestamp("date")?.toDate()?.let {
                        eventDayKeys.add(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(it))
                    }
                }

                // 그리드 갱신(데코 반영)
                (monthGrid.adapter as DayCellAdapter).refreshDots(spendDayKeys, eventDayKeys)

                // 월간 카드도 갱신
                runCatching { loadMonthlyStats(eventsStatsView, spendingStatsView, activeDaysStatsView) }

            } catch (e: Exception) {
                Log.e("Calendar", "월 데코 로드 실패", e)
            }
        }
    }

    // ---------- 기타 UI ----------

    private fun showAddEventDialog() {
        val context = requireContext()
        val layout = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(20, 20, 20, 20) }
        val titleEdit = EditText(context).apply { hint = "일정 제목"; textSize = 16f }
        val descEdit = EditText(context).apply { hint = "설명 (선택사항)"; textSize = 14f; setPadding(0, 16, 0, 0) }
        layout.addView(titleEdit); layout.addView(descEdit)

        AlertDialog.Builder(context)
            .setTitle("📅 일정 추가")
            .setView(layout)
            .setPositiveButton("추가") { _, _ ->
                val title = titleEdit.text.toString()
                val description = descEdit.text.toString()
                if (title.isNotEmpty()) addLocalEvent(title, description)
                else Toast.makeText(context, "제목을 입력해주세요", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun addLocalEvent(title: String, description: String) {
        val user = auth.currentUser ?: return
        val event = hashMapOf(
            "title" to title,
            "description" to description,
            "date" to Timestamp(selectedDate.time),
            "type" to "LOCAL",
            "isRepeating" to false,
            "createdAt" to Timestamp.now()
        )
        db.collection("users").document(user.uid).collection("events")
            .add(event)
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "일정이 추가되었습니다!", Toast.LENGTH_SHORT).show()
                loadDateData(selectedDate)
                loadMonthlyStats(eventsStatsView, spendingStatsView, activeDaysStatsView)
                loadMonthDecorDataAndRefresh()
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "일정 추가 실패: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showDateDetailBottomSheet() {
        val context = requireContext()
        val dialog = BottomSheetDialog(context)
        val layout = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(20, 20, 20, 20) }
        val title = TextView(context).apply {
            textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 16)
        }
        val rv = RecyclerView(context).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 400.dpToPx())
        }
        val empty = TextView(context).apply {
            text = "이 날에는 일정이나 지출이 없습니다."
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#757575"))
            visibility = View.GONE
        }
        val df = SimpleDateFormat("MM월 dd일 (E)", Locale.KOREAN)
        title.text = df.format(selectedDate.time)

        layout.addView(title); layout.addView(rv); layout.addView(empty)
        dialog.setContentView(layout)
        dialog.show()

        // 데이터 로드는 다이얼로그 보여준 뒤 호출 (context 안전)
        loadDateEventsAndSpending(rv, empty)
    }

    private fun loadDateEventsAndSpending(rv: RecyclerView, empty: TextView) {
        val user = auth.currentUser
        if (user == null) {
            Toast.makeText(requireContext(), "로그인이 필요합니다", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            val all = mutableListOf<CalendarEvent>()

            // 1) 날짜 범위 계산
            val start = Calendar.getInstance().apply {
                timeInMillis = selectedDate.timeInMillis
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            val end = Calendar.getInstance().apply {
                timeInMillis = selectedDate.timeInMillis
                set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999)
            }

            // 2) 로컬/앱 일정
            try {
                val events = db.collection("users").document(user.uid).collection("events")
                    .whereGreaterThanOrEqualTo("date", Timestamp(start.time))
                    .whereLessThanOrEqualTo("date", Timestamp(end.time))
                    .get().await()

                for (d in events.documents) {
                    val t = d.getString("type") ?: "LOCAL"
                    all.add(
                        CalendarEvent(
                            id = d.id,
                            title = d.getString("title") ?: "",
                            description = d.getString("description") ?: "",
                            date = d.getTimestamp("date") ?: Timestamp.now(),
                            type = if (t == "SPENDING") EventType.SPENDING else EventType.LOCAL,
                            category = if (t == "SPENDING") d.getString("category") ?: "지출" else "로컬 일정"
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e("Calendar", "앱 일정 로드 실패", e)
                Toast.makeText(requireContext(), "앱 일정 로드 실패: ${e.message}", Toast.LENGTH_SHORT).show()
            }

            // 3) 지출
            try {
                val spend = db.collection("users").document(user.uid).collection("spending")
                    .whereGreaterThanOrEqualTo("date", Timestamp(start.time))
                    .whereLessThanOrEqualTo("date", Timestamp(end.time))
                    .get().await()

                val nf = NumberFormat.getInstance(Locale.KOREA)
                for (d in spend.documents) {
                    val amount = (d.getLong("amount") ?: 0).toInt()
                    val category = d.getString("category") ?: "기타"
                    val memo = d.getString("memo") ?: ""
                    all.add(
                        CalendarEvent(
                            id = d.id,
                            title = "$category - ￦${nf.format(amount)}",
                            description = memo.ifEmpty { "메모 없음" },
                            date = d.getTimestamp("date") ?: Timestamp.now(),
                            type = EventType.SPENDING,
                            category = category
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e("Calendar", "지출 로드 실패", e)
                Toast.makeText(requireContext(), "지출 로드 실패: ${e.message}", Toast.LENGTH_SHORT).show()
            }

            // 4) Google 캘린더 (있으면)
            val account = GoogleSignIn.getLastSignedInAccount(requireContext())
            if (account != null) {
                try {
                    val helper = GoogleCalendarHelper(requireContext(), account)
                    helper.loadEventsForDate(selectedDate.time) { googleEvents ->
                        // 콜백이 어디서 오든 UI 스레드로 보장
                        if (isAdded) {
                            requireActivity().runOnUiThread {
                                all.addAll(googleEvents)
                                setupRecycler(all, rv, empty)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w("Calendar", "Google Calendar Helper 사용 불가", e)
                    Toast.makeText(requireContext(), "구글 캘린더 로드 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                    setupRecycler(all, rv, empty)
                }
            } else {
                setupRecycler(all, rv, empty)
            }
        }
    }

    private fun setupRecycler(allItems: MutableList<CalendarEvent>, rv: RecyclerView, empty: TextView) {
        allItems.sortBy { it.date.toDate() }
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = CalendarEventsAdapter(allItems) {
            loadDateData(selectedDate)
            loadMonthlyStats(eventsStatsView, spendingStatsView, activeDaysStatsView)
            loadMonthDecorDataAndRefresh()
        }
        if (allItems.isEmpty()) { empty.visibility = View.VISIBLE; rv.visibility = View.GONE }
        else { empty.visibility = View.GONE; rv.visibility = View.VISIBLE }
    }

    private fun updateDateIndicator(spendingCount: Int, eventCount: Int) {
        dateIndicator.removeAllViews()
        val context = requireContext()
        if (spendingCount > 0) {
            val t = TextView(context).apply {
                text = "💰 지출 ${spendingCount}건"; textSize = 14f
                setPadding(8, 4, 8, 4); setBackgroundColor(Color.parseColor("#E8F5E8"))
                updateLayoutParams<ViewGroup.MarginLayoutParams> { rightMargin = 8.dpToPx() }
            }
            dateIndicator.addView(t)
        }
        if (eventCount > 0) {
            val t = TextView(context).apply {
                text = "📅 일정 ${eventCount}건"; textSize = 14f
                setPadding(8, 4, 8, 4); setBackgroundColor(Color.parseColor("#E3F2FD"))
            }
            dateIndicator.addView(t)
        }
        if (spendingCount == 0 && eventCount == 0) {
            val t = TextView(context).apply {
                text = "🌱 오늘은 깨끗한 하루!"; textSize = 14f
                setTextColor(Color.parseColor("#757575"))
            }
            dateIndicator.addView(t)
        }
    }

    private fun Int.dpToPx(): Int {
        val d = resources.displayMetrics.density
        return (this * d).toInt()
    }
}

// ---------- 데이터 모델 & Day 셀 어댑터 ----------

private data class DayCell(
    val date: Date,
    val inMonth: Boolean
)

private class DayCellAdapter(
    private val onClick: (DayCell) -> Unit
) : RecyclerView.Adapter<DayCellAdapter.VH>() {

    private val items = mutableListOf<DayCell>()
    private val spendKeys = mutableSetOf<String>()
    private val eventKeys = mutableSetOf<String>()
    private val dayFormat = SimpleDateFormat("d", Locale.getDefault())
    private val keyFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    fun submit(newItems: List<DayCell>) {
        items.clear(); items.addAll(newItems); notifyDataSetChanged()
    }

    fun refreshDots(spend: Set<String>, event: Set<String>) {
        spendKeys.clear(); spendKeys.addAll(spend)
        eventKeys.clear(); eventKeys.addAll(event)
        notifyDataSetChanged()
    }

    inner class VH(val card: CardView, val dayText: TextView, val dotRow: LinearLayout, val dotSpend: View, val dotEvent: View) :
        RecyclerView.ViewHolder(card)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val context = parent.context
        val card = CardView(context).apply {
            layoutParams = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (parent.measuredWidth / 7.0 * 0.9).toInt()
            ).apply { setMargins(2, 2, 2, 2) }
            radius = 12f
            cardElevation = 0f
        }
        val box = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 8)
        }
        val dayText = TextView(context).apply { textSize = 16f; gravity = Gravity.CENTER }
        val dotRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; setPadding(0, 4, 0, 0) }
        val dotSpend = View(context).apply { setBackgroundColor(Color.parseColor("#4CAF50")) }
        val dotEvent = View(context).apply { setBackgroundColor(Color.parseColor("#2196F3")) }
        val size = (6 * context.resources.displayMetrics.density).toInt()
        dotRow.addView(dotSpend, LinearLayout.LayoutParams(size, size).apply { rightMargin = (4 * context.resources.displayMetrics.density).toInt() })
        dotRow.addView(dotEvent, LinearLayout.LayoutParams(size, size))
        box.addView(dayText); box.addView(dotRow)
        card.addView(box)
        return VH(card, dayText, dotRow, dotSpend, dotEvent)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val cell = items[position]
        holder.dayText.text = dayFormat.format(cell.date)

        holder.card.alpha = if (cell.inMonth) 1f else 0.35f

        val key = keyFormat.format(cell.date)
        val hasSpend = spendKeys.contains(key)
        val hasEvent = eventKeys.contains(key)

        holder.dotRow.visibility = if (hasSpend || hasEvent) View.VISIBLE else View.INVISIBLE
        holder.dotSpend.visibility = if (hasSpend) View.VISIBLE else View.GONE
        holder.dotEvent.visibility = if (hasEvent) View.VISIBLE else View.GONE

        holder.card.setOnClickListener { onClick(cell) }
    }

    override fun getItemCount() = items.size
}
