package com.example.myapplication

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
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
import java.util.*

/**
 * 향상된 달력 프래그먼트 - 일정 관리와 지출 추적 통합
 */
class CalendarFragment : Fragment() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private lateinit var calendarView: CalendarView
    private lateinit var selectedDateText: TextView
    private lateinit var dateTotalText: TextView
    private lateinit var dateIndicator: LinearLayout

    private var selectedDate: Calendar = Calendar.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return createCalendarLayout()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupCalendar()
        loadMonthData()
    }

    private fun createCalendarLayout(): View {
        val context = requireContext()
        val scrollView = androidx.core.widget.NestedScrollView(context)

        val mainLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }

        // 헤더 카드
        mainLayout.addView(createHeaderCard())

        // 월별 통계 카드
        mainLayout.addView(createMonthlyStatsCard())

        // 범례 및 일정 추가 버튼
        mainLayout.addView(createLegendCard())

        // 캘린더 카드
        mainLayout.addView(createCalendarCard())

        // 선택된 날짜 정보 카드
        mainLayout.addView(createDateInfoCard())

        scrollView.addView(mainLayout)
        return scrollView
    }

    private fun createHeaderCard(): View {
        val context = requireContext()
        val cardView = androidx.cardview.widget.CardView(context).apply {
            layoutParams = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 16)
            }
            radius = 16f
            cardElevation = 4f
        }

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 20)
        }

        val titleText = TextView(context).apply {
            text = "📅 스마트 캘린더"
            textSize = 20f
            setTypeface(null, android.graphics.Typeface.BOLD)
        }

        val subtitleText = TextView(context).apply {
            text = "일정과 지출을 한 번에 관리하세요"
            textSize = 14f
            setPadding(0, 4, 0, 0)
        }

        layout.addView(titleText)
        layout.addView(subtitleText)
        cardView.addView(layout)
        return cardView
    }

    private fun createMonthlyStatsCard(): View {
        val context = requireContext()
        val cardView = androidx.cardview.widget.CardView(context).apply {
            layoutParams = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 16)
            }
            radius = 12f
            cardElevation = 2f
            setCardBackgroundColor(Color.parseColor("#F8F9FA"))
        }

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 16, 20, 16)
        }

        val titleText = TextView(context).apply {
            text = "📈 이번 달 요약"
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.parseColor("#1A1D29"))
        }

        val statsContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 12, 0, 0)
        }

        // 일정 통계
        val eventsStats = createStatsItem(context, "📅", "0", "일정")
        statsContainer.addView(eventsStats)

        // 구분선
        val divider1 = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(1, 30.dpToPx()).apply {
                setMargins(16, 0, 16, 0)
            }
            setBackgroundColor(Color.parseColor("#E0E0E0"))
        }
        statsContainer.addView(divider1)

        // 지출 통계
        val spendingStats = createStatsItem(context, "💰", "￦0", "지출")
        statsContainer.addView(spendingStats)

        // 구분선
        val divider2 = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(1, 30.dpToPx()).apply {
                setMargins(16, 0, 16, 0)
            }
            setBackgroundColor(Color.parseColor("#E0E0E0"))
        }
        statsContainer.addView(divider2)

        // 활동 일자 통계
        val activeDaysStats = createStatsItem(context, "🎆", "0", "활동일")
        statsContainer.addView(activeDaysStats)

        layout.addView(titleText)
        layout.addView(statsContainer)
        cardView.addView(layout)

        // 데이터 로드
        loadMonthlyStats(eventsStats, spendingStats, activeDaysStats)

        return cardView
    }

    private fun createStatsItem(context: Context, icon: String, value: String, label: String): View {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
            )
        }

        val iconText = TextView(context).apply {
            text = icon
            textSize = 20f
            gravity = Gravity.CENTER
        }

        val valueText = TextView(context).apply {
            text = value
            textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.parseColor("#2196F3"))
            gravity = Gravity.CENTER
            tag = "value_$label" // 데이터 업데이트를 위한 태그
        }

        val labelText = TextView(context).apply {
            text = label
            textSize = 12f
            setTextColor(Color.parseColor("#757575"))
            gravity = Gravity.CENTER
        }

        container.addView(iconText)
        container.addView(valueText)
        container.addView(labelText)

        return container
    }

    private fun createLegendCard(): View {
        val context = requireContext()
        val cardView = androidx.cardview.widget.CardView(context).apply {
            layoutParams = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 16)
            }
            radius = 12f
            cardElevation = 2f
        }

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(16, 12, 16, 12)
        }

        val legendText = TextView(context).apply {
            text = "📅 일정    💰 지출    🎆 둘 다"
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
            )
        }

        val addEventButton = Button(context).apply {
            text = "📅 일정 추가"
            textSize = 12f
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener { showAddEventDialog() }
            // Material 스타일 적용
            setBackgroundColor(Color.parseColor("#4CAF50"))
            setTextColor(Color.WHITE)
        }

        layout.addView(legendText)
        layout.addView(addEventButton)
        cardView.addView(layout)
        return cardView
    }

    private fun createCalendarCard(): View {
        val context = requireContext()
        val cardView = androidx.cardview.widget.CardView(context).apply {
            layoutParams = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 16)
            }
            radius = 16f
            cardElevation = 3f
        }

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 20)
        }

        calendarView = CalendarView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 350.dpToPx()
            )
        }

        layout.addView(calendarView)
        cardView.addView(layout)
        return cardView
    }

    private fun createDateInfoCard(): View {
        val context = requireContext()
        val cardView = androidx.cardview.widget.CardView(context).apply {
            layoutParams = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            radius = 16f
            cardElevation = 3f
        }

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 20)
        }

        selectedDateText = TextView(context).apply {
            text = "날짜를 선택해주세요"
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

        val detailButton = Button(context).apply {
            text = "📊 자세히 보기"
            setOnClickListener { showDateDetailBottomSheet() }
            // Material 스타일 적용
            setBackgroundColor(Color.parseColor("#4CAF50"))
            setTextColor(Color.WHITE)
        }

        layout.addView(selectedDateText)
        layout.addView(dateTotalText)
        layout.addView(dateIndicator)
        layout.addView(detailButton)
        cardView.addView(layout)
        return cardView
    }

    private fun setupCalendar() {
        calendarView.setOnDateChangeListener { _, year, month, dayOfMonth ->
            selectedDate = Calendar.getInstance().apply {
                set(year, month, dayOfMonth)
            }

            val dateFormat = SimpleDateFormat("MM월 dd일 (E)", Locale.KOREAN)
            selectedDateText.text = dateFormat.format(selectedDate.time)

            loadDateData(selectedDate)
        }
    }

    private fun loadMonthlyStats(eventsStats: View, spendingStats: View, activeDaysStats: View) {
        val currentUser = auth.currentUser ?: return

        lifecycleScope.launch {
            try {
                val calendar = Calendar.getInstance()
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                val monthStart = calendar.time

                calendar.add(Calendar.MONTH, 1)
                calendar.add(Calendar.DAY_OF_MONTH, -1)
                val monthEnd = calendar.time

                // 일정 통계
                val eventsSnapshot = db.collection("users")
                    .document(currentUser.uid)
                    .collection("events")
                    .whereGreaterThanOrEqualTo("date", Timestamp(monthStart))
                    .whereLessThanOrEqualTo("date", Timestamp(monthEnd))
                    .get()
                    .await()

                val totalEvents = eventsSnapshot.size()

                // 지출 통계
                val spendingSnapshot = db.collection("users")
                    .document(currentUser.uid)
                    .collection("spending")
                    .whereGreaterThanOrEqualTo("date", Timestamp(monthStart))
                    .whereLessThanOrEqualTo("date", Timestamp(monthEnd))
                    .get()
                    .await()

                var totalSpending = 0
                val activeDates = mutableSetOf<String>()

                for (doc in spendingSnapshot.documents) {
                    totalSpending += (doc.getLong("amount") ?: 0).toInt()
                    val timestamp = doc.getTimestamp("date")
                    if (timestamp != null) {
                        val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                            .format(timestamp.toDate())
                        activeDates.add(dateStr)
                    }
                }

                // 일정이 있는 날짜도 추가
                for (doc in eventsSnapshot.documents) {
                    val timestamp = doc.getTimestamp("date")
                    if (timestamp != null) {
                        val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                            .format(timestamp.toDate())
                        activeDates.add(dateStr)
                    }
                }

                // UI 업데이트
                val numberFormat = NumberFormat.getInstance(Locale.KOREA)

                eventsStats.findViewWithTag<TextView>("value_일정")?.text = "${totalEvents}"
                spendingStats.findViewWithTag<TextView>("value_지출")?.text = "￦${numberFormat.format(totalSpending)}"
                activeDaysStats.findViewWithTag<TextView>("value_활동일")?.text = "${activeDates.size}"

                Log.d("Calendar", "이번 달 통계: 일정 ${totalEvents}건, 지출 ￦${numberFormat.format(totalSpending)}, 활동일 ${activeDates.size}일")

            } catch (e: Exception) {
                Log.e("Calendar", "월별 통계 로드 실패", e)
            }
        }
    }

    private fun loadMonthData() {
        // 월별 지시자 데이터 로드 (캘린더 지시자용)
        Log.d("Calendar", "월별 데이터 로드 완료")
    }

    private fun loadDateData(selectedDate: Calendar) {
        val currentUser = auth.currentUser ?: return

        lifecycleScope.launch {
            try {
                val startOfDay = Calendar.getInstance().apply {
                    timeInMillis = selectedDate.timeInMillis
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }

                val endOfDay = Calendar.getInstance().apply {
                    timeInMillis = selectedDate.timeInMillis
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                    set(Calendar.MILLISECOND, 999)
                }

                // 지출 데이터 로드
                val spendingSnapshot = db.collection("users")
                    .document(currentUser.uid)
                    .collection("spending")
                    .whereGreaterThanOrEqualTo("date", Timestamp(startOfDay.time))
                    .whereLessThanOrEqualTo("date", Timestamp(endOfDay.time))
                    .get()
                    .await()

                var totalAmount = 0
                var spendingCount = 0
                for (document in spendingSnapshot.documents) {
                    totalAmount += (document.getLong("amount") ?: 0).toInt()
                    spendingCount++
                }

                // 로컬 일정 데이터 로드
                val eventsSnapshot = db.collection("users")
                    .document(currentUser.uid)
                    .collection("events")
                    .whereGreaterThanOrEqualTo("date", Timestamp(startOfDay.time))
                    .whereLessThanOrEqualTo("date", Timestamp(endOfDay.time))
                    .get()
                    .await()

                val eventCount = eventsSnapshot.size()

                // UI 업데이트
                val numberFormat = NumberFormat.getInstance(Locale.KOREA)
                dateTotalText.text = "￦${numberFormat.format(totalAmount)}"

                updateDateIndicator(spendingCount, eventCount)

            } catch (e: Exception) {
                dateTotalText.text = "데이터 로드 실패"
                Log.e("Calendar", "날짜별 데이터 로드 실패", e)
            }
        }
    }

    private fun updateDateIndicator(spendingCount: Int, eventCount: Int) {
        dateIndicator.removeAllViews()

        val context = requireContext()

        if (spendingCount > 0) {
            val spendingIndicator = TextView(context).apply {
                text = "💰 지출 ${spendingCount}건"
                textSize = 14f
                setPadding(8, 4, 8, 4)
                setBackgroundColor(Color.parseColor("#E8F5E8"))
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, 8, 0)
                }
            }
            dateIndicator.addView(spendingIndicator)
        }

        if (eventCount > 0) {
            val eventIndicator = TextView(context).apply {
                text = "📅 일정 ${eventCount}건"
                textSize = 14f
                setPadding(8, 4, 8, 4)
                setBackgroundColor(Color.parseColor("#E3F2FD"))
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            dateIndicator.addView(eventIndicator)
        }

        if (spendingCount == 0 && eventCount == 0) {
            val emptyIndicator = TextView(context).apply {
                text = "🌱 오늘은 깨끗한 하루!"
                textSize = 14f
                setTextColor(Color.parseColor("#757575"))
            }
            dateIndicator.addView(emptyIndicator)
        }
    }

    private fun showAddEventDialog() {
        val context = requireContext()

        // 기본적인 다이얼로그 생성
        val dialogLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 20)
        }

        val titleEdit = EditText(context).apply {
            hint = "일정 제목"
            textSize = 16f
        }

        val descEdit = EditText(context).apply {
            hint = "설명 (선택사항)"
            textSize = 14f
            setPadding(0, 16, 0, 0)
        }

        val repeatSpinner = Spinner(context).apply {
            setPadding(0, 16, 0, 0)
        }

        val repeatCountEdit = EditText(context).apply {
            hint = "반복 횟수"
            inputType = InputType.TYPE_CLASS_NUMBER
            setPadding(0, 8, 0, 0)
            visibility = View.GONE
        }

        // 반복 옵션 설정
        val repeatOptions = listOf("반복 없음", "매일", "매주", "매월")
        val repeatAdapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, repeatOptions)
        repeatAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        repeatSpinner.adapter = repeatAdapter

        // 반복 선택 시 횟수 입력 필드 표시/숨김
        repeatSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position == 0) { // "반복 없음"
                    repeatCountEdit.visibility = View.GONE
                } else {
                    repeatCountEdit.visibility = View.VISIBLE
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        dialogLayout.addView(titleEdit)
        dialogLayout.addView(descEdit)
        dialogLayout.addView(repeatSpinner)
        dialogLayout.addView(repeatCountEdit)

        AlertDialog.Builder(context)
            .setTitle("📅 일정 추가")
            .setView(dialogLayout)
            .setPositiveButton("추가") { _, _ ->
                val title = titleEdit.text.toString()
                val description = descEdit.text.toString()
                val repeatType = repeatSpinner.selectedItemPosition
                val repeatCount = repeatCountEdit.text.toString().toIntOrNull() ?: 0

                if (title.isNotEmpty()) {
                    if (repeatType == 0) {
                        addLocalEvent(title, description)
                    } else {
                        addRepeatingEvent(title, description, repeatType, repeatCount)
                    }
                } else {
                    Toast.makeText(context, "제목을 입력해주세요", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun addLocalEvent(title: String, description: String) {
        val currentUser = auth.currentUser ?: return

        val event = hashMapOf(
            "title" to title,
            "description" to description,
            "date" to Timestamp(selectedDate.time),
            "type" to "LOCAL",
            "isRepeating" to false,
            "createdAt" to Timestamp.now()
        )

        db.collection("users")
            .document(currentUser.uid)
            .collection("events")
            .add(event)
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "일정이 추가되었습니다!", Toast.LENGTH_SHORT).show()
                loadDateData(selectedDate)
                loadMonthData()
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "일정 추가 실패: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun addRepeatingEvent(title: String, description: String, repeatType: Int, repeatCount: Int) {
        val currentUser = auth.currentUser ?: return

        val repeatTypes = listOf("", "DAILY", "WEEKLY", "MONTHLY")
        val repeatTypeStr = if (repeatType < repeatTypes.size) repeatTypes[repeatType] else ""

        val maxRepeats = if (repeatCount > 0) repeatCount else getDefaultRepeatCount(repeatType)

        val baseCalendar = Calendar.getInstance().apply {
            time = selectedDate.time
        }

        val events = mutableListOf<Map<String, Any>>()

        for (i in 0 until maxRepeats) {
            val eventDate = Calendar.getInstance().apply {
                time = baseCalendar.time
                when (repeatType) {
                    1 -> add(Calendar.DAY_OF_MONTH, i) // 매일
                    2 -> add(Calendar.WEEK_OF_YEAR, i) // 매주
                    3 -> add(Calendar.MONTH, i) // 매월
                }
            }

            val event = hashMapOf<String, Any>(
                "title" to title,
                "description" to description,
                "date" to Timestamp(eventDate.time),
                "type" to "LOCAL",
                "isRepeating" to true,
                "repeatType" to repeatTypeStr,
                "repeatIndex" to i,
                "totalRepeats" to maxRepeats,
                "createdAt" to Timestamp.now()
            )

            events.add(event)
        }

        // 배치로 모든 반복 일정 추가
        val batch = db.batch()
        events.forEach { event ->
            val docRef = db.collection("users")
                .document(currentUser.uid)
                .collection("events")
                .document()
            batch.set(docRef, event)
        }

        batch.commit()
            .addOnSuccessListener {
                val repeatTypeNames = listOf("", "매일", "매주", "매월")
                val typeName = if (repeatType < repeatTypeNames.size) repeatTypeNames[repeatType] else ""
                Toast.makeText(requireContext(), "🔄 ${typeName} ${maxRepeats}회 반복 일정이 추가되었습니다!", Toast.LENGTH_LONG).show()
                loadDateData(selectedDate)
                loadMonthData()
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "❌ 반복 일정 추가 실패: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun getDefaultRepeatCount(repeatType: Int): Int {
        return when (repeatType) {
            1 -> 30 // 매일 - 30일
            2 -> 12 // 매주 - 12주
            3 -> 12 // 매월 - 12개월
            else -> 1
        }
    }

    private fun showDateDetailBottomSheet() {
        val context = requireContext()
        val bottomSheetDialog = BottomSheetDialog(context)

        // 기본 레이아웃 생성
        val bottomSheetLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 20)
        }

        val dateTitle = TextView(context).apply {
            textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 16)
        }

        val recyclerView = RecyclerView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                400.dpToPx()
            )
        }

        val emptyMessage = TextView(context).apply {
            text = "이 날에는 일정이나 지출이 없습니다."
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#757575"))
            visibility = View.GONE
        }

        val dateFormat = SimpleDateFormat("MM월 dd일 (E)", Locale.KOREAN)
        dateTitle.text = dateFormat.format(selectedDate.time)

        bottomSheetLayout.addView(dateTitle)
        bottomSheetLayout.addView(recyclerView)
        bottomSheetLayout.addView(emptyMessage)

        // 데이터 로드 및 리사이클러뷰 설정
        loadDateEventsAndSpending(recyclerView, emptyMessage)

        bottomSheetDialog.setContentView(bottomSheetLayout)
        bottomSheetDialog.show()
    }

    private fun loadDateEventsAndSpending(recyclerView: RecyclerView, emptyMessage: TextView) {
        val currentUser = auth.currentUser ?: return

        lifecycleScope.launch {
            try {
                val startOfDay = Calendar.getInstance().apply {
                    timeInMillis = selectedDate.timeInMillis
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }

                val endOfDay = Calendar.getInstance().apply {
                    timeInMillis = selectedDate.timeInMillis
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                    set(Calendar.MILLISECOND, 999)
                }

                val allItems = mutableListOf<CalendarEvent>()

                // 로컬 일정 로드
                val eventsSnapshot = db.collection("users")
                    .document(currentUser.uid)
                    .collection("events")
                    .whereGreaterThanOrEqualTo("date", Timestamp(startOfDay.time))
                    .whereLessThanOrEqualTo("date", Timestamp(endOfDay.time))
                    .get()
                    .await()

                for (doc in eventsSnapshot.documents) {
                    val event = CalendarEvent(
                        id = doc.id,
                        title = doc.getString("title") ?: "",
                        description = doc.getString("description") ?: "",
                        date = doc.getTimestamp("date") ?: Timestamp.now(),
                        type = EventType.LOCAL,
                        category = "로컬 일정"
                    )
                    allItems.add(event)
                }

                // 지출 내역 로드
                val spendingSnapshot = db.collection("users")
                    .document(currentUser.uid)
                    .collection("spending")
                    .whereGreaterThanOrEqualTo("date", Timestamp(startOfDay.time))
                    .whereLessThanOrEqualTo("date", Timestamp(endOfDay.time))
                    .get()
                    .await()

                for (doc in spendingSnapshot.documents) {
                    val amount = (doc.getLong("amount") ?: 0).toInt()
                    val category = doc.getString("category") ?: "기타"
                    val memo = doc.getString("memo") ?: ""

                    val numberFormat = NumberFormat.getInstance(Locale.KOREA)
                    val event = CalendarEvent(
                        id = doc.id,
                        title = "$category - ￦${numberFormat.format(amount)}",
                        description = memo,
                        date = doc.getTimestamp("date") ?: Timestamp.now(),
                        type = EventType.SPENDING,
                        category = category
                    )
                    allItems.add(event)
                }

                // Google 캘린더 이벤트 로드 (구글 로그인 사용자인 경우)
                val googleAccount = GoogleSignIn.getLastSignedInAccount(requireContext())
                if (googleAccount != null) {
                    try {
                        val googleCalendarHelper = GoogleCalendarHelper(requireContext(), googleAccount)
                        googleCalendarHelper.loadEventsForDate(selectedDate.time) { googleEvents ->
                            allItems.addAll(googleEvents)
                            setupRecyclerView(allItems, recyclerView, emptyMessage)
                        }
                    } catch (e: Exception) {
                        Log.w("Calendar", "Google Calendar Helper 사용 불가", e)
                        setupRecyclerView(allItems, recyclerView, emptyMessage)
                    }
                } else {
                    setupRecyclerView(allItems, recyclerView, emptyMessage)
                }

            } catch (e: Exception) {
                Log.e("Calendar", "이벤트 로드 실패", e)
                Toast.makeText(requireContext(), "데이터 로드 실패", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupRecyclerView(allItems: MutableList<CalendarEvent>, recyclerView: RecyclerView, emptyMessage: TextView) {
        // 시간순으로 정렬
        allItems.sortBy { it.date.toDate() }

        // 어댑터 설정
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        // CalendarEventsAdapter 사용
        try {
            recyclerView.adapter = CalendarEventsAdapter(allItems) {
                // 데이터 변경 시 콜백
                loadDateData(selectedDate)
                loadMonthData()
            }
        } catch (e: Exception) {
            // 기본 SimpleAdapter 사용
            recyclerView.adapter = SimpleCalendarAdapter(allItems) {
                loadDateData(selectedDate)
                loadMonthData()
            }
        }

        // 빈 메시지 처리
        if (allItems.isEmpty()) {
            emptyMessage.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            emptyMessage.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
        }
    }

    private fun Int.dpToPx(): Int {
        val density = resources.displayMetrics.density
        return (this * density).toInt()
    }
}

// 기본 어댑터 클래스 (CalendarEventsAdapter가 없는 경우 대비)
class SimpleCalendarAdapter(
    private val items: MutableList<CalendarEvent>,
    private val onDataChanged: () -> Unit
) : RecyclerView.Adapter<SimpleCalendarAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val titleText: TextView = view.findViewWithTag("title") ?: TextView(view.context)
        val descText: TextView = view.findViewWithTag("desc") ?: TextView(view.context)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        // 프로그래매틱하게 레이아웃 생성
        val context = parent.context
        val cardView = androidx.cardview.widget.CardView(context).apply {
            layoutParams = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(8, 4, 8, 4)
            }
            radius = 8f
            cardElevation = 2f
        }

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 12, 16, 12)
        }

        val titleText = TextView(context).apply {
            tag = "title"
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.parseColor("#1A1D29"))
        }

        val descText = TextView(context).apply {
            tag = "desc"
            textSize = 14f
            setTextColor(Color.parseColor("#757575"))
            setPadding(0, 4, 0, 0)
        }

        layout.addView(titleText)
        layout.addView(descText)
        cardView.addView(layout)

        return ViewHolder(cardView)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]

        val typeEmoji = when (item.type) {
            EventType.LOCAL -> "📅"
            EventType.GOOGLE -> "🔗"
            EventType.SPENDING -> "💰"
        }

        holder.titleText.text = "$typeEmoji ${item.title}"
        holder.descText.text = if (item.description.isNotEmpty()) item.description else "설명 없음"

        // 길게 누르기 이벤트 (삭제 기능)
        holder.itemView.setOnLongClickListener {
            showDeleteDialog(item, position)
            true
        }
    }

    private fun showDeleteDialog(item: CalendarEvent, position: Int) {
        val context = items.firstOrNull()?.let {
            return@let null
        }

        // 삭제 기능은 LOCAL 이벤트와 SPENDING에서만 지원
        if (item.type == EventType.LOCAL || item.type == EventType.SPENDING) {
            try {
                val title = if (item.type == EventType.SPENDING) "지출 삭제" else "일정 삭제"
                val message = "'${item.title}'을(를) 삭제하시겠습니까?"

                // Context가 없으므로 삭제 기능을 직접 호출
                deleteEvent(item, position)
            } catch (e: Exception) {
                Log.w("Calendar", "삭제 다이얼로그 표시 실패", e)
            }
        }
    }

    private fun deleteEvent(item: CalendarEvent, position: Int) {
        val auth = FirebaseAuth.getInstance()
        val db = FirebaseFirestore.getInstance()
        val currentUser = auth.currentUser ?: return

        val collection = when (item.type) {
            EventType.LOCAL -> "events"
            EventType.SPENDING -> "spending"
            EventType.GOOGLE -> return // 구글 이벤트는 삭제 불가
        }

        db.collection("users")
            .document(currentUser.uid)
            .collection(collection)
            .document(item.id)
            .delete()
            .addOnSuccessListener {
                items.removeAt(position)
                notifyItemRemoved(position)
                onDataChanged()
            }
            .addOnFailureListener {
                Log.e("Calendar", "삭제 실패: ${it.message}")
            }
    }

    override fun getItemCount() = items.size
}