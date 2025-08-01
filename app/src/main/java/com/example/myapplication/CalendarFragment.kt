package com.example.myapplication

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.graphics.Color
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.Timestamp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class CalendarFragment : Fragment() {

    private lateinit var rootView: View
    private lateinit var calendarView: CalendarView
    private lateinit var eventsRecyclerView: RecyclerView
    private lateinit var eventsAdapter: CalendarEventsAdapter
    private lateinit var addEventFab: FloatingActionButton
    private lateinit var monthYearText: TextView
    private lateinit var todayButton: Button
    private lateinit var userTypeIndicator: TextView

    private val db = FirebaseFirestore.getInstance()
    private val events = mutableListOf<CalendarEvent>()
    private val spendingData = mutableMapOf<String, List<SpendingData>>()

    private var selectedDate = Calendar.getInstance()
    private var isGoogleUser = false
    private var googleCalendarHelper: GoogleCalendarHelper? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        rootView = createCalendarLayout()
        return rootView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initializeComponents()
        checkUserType()
        setupCalendar()
        setupRecyclerView()
        setupEventListeners()
        loadInitialData()
    }

    private fun createCalendarLayout(): View {
        val context = requireContext()

        // 메인 스크롤뷰
        val scrollView = ScrollView(context).apply {
            setBackgroundColor(Color.parseColor("#F8F9FA"))
        }

        val mainLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        // 상단 헤더
        val headerLayout = createHeaderLayout()

        // 캘린더 컨테이너
        val calendarContainer = createCalendarContainer()

        // 선택된 날짜 정보
        val selectedDateInfo = createSelectedDateInfo()

        // 이벤트 목록
        val eventsSection = createEventsSection()

        // FAB
        addEventFab = FloatingActionButton(context).apply {
            setImageResource(android.R.drawable.ic_input_add)
            setBackgroundColor(Color.parseColor("#1976D2"))
            layoutParams = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 32, 32, 32)
            }
        }

        mainLayout.addView(headerLayout)
        mainLayout.addView(calendarContainer)
        mainLayout.addView(selectedDateInfo)
        mainLayout.addView(eventsSection)

        scrollView.addView(mainLayout)

        // FAB를 별도로 추가하기 위한 FrameLayout
        val frameLayout = FrameLayout(context)
        frameLayout.addView(scrollView)
        frameLayout.addView(addEventFab, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            android.view.Gravity.BOTTOM or android.view.Gravity.END
        ))

        return frameLayout
    }

    private fun createHeaderLayout(): View {
        val context = requireContext()

        val headerCard = androidx.cardview.widget.CardView(context).apply {
            radius = 0f
            cardElevation = 4f
            setCardBackgroundColor(Color.WHITE)
        }

        val headerLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 32)
        }

        // 제목과 사용자 타입
        val titleLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val titleText = TextView(context).apply {
            text = "📅 스마트 캘린더"
            textSize = 24f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.parseColor("#212121"))
            layoutParams = LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
            )
        }

        userTypeIndicator = TextView(context).apply {
            textSize = 12f
            setPadding(24, 12, 24, 12)
            setBackgroundColor(Color.parseColor("#E3F2FD"))
            setTextColor(Color.parseColor("#1976D2"))
        }

        titleLayout.addView(titleText)
        titleLayout.addView(userTypeIndicator)

        // 월/년 표시와 오늘 버튼
        val controlLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 24, 0, 0)
        }

        monthYearText = TextView(context).apply {
            textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.parseColor("#424242"))
            layoutParams = LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
            )
        }

        todayButton = Button(context).apply {
            text = "오늘"
            textSize = 14f
            setPadding(32, 16, 32, 16)
            setBackgroundColor(Color.parseColor("#1976D2"))
            setTextColor(Color.WHITE)
        }

        controlLayout.addView(monthYearText)
        controlLayout.addView(todayButton)

        headerLayout.addView(titleLayout)
        headerLayout.addView(controlLayout)
        headerCard.addView(headerLayout)

        return headerCard
    }

    private fun createCalendarContainer(): View {
        val context = requireContext()

        val calendarCard = androidx.cardview.widget.CardView(context).apply {
            layoutParams = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(24, 16, 24, 16)
            }
            radius = 16f
            cardElevation = 6f
            setCardBackgroundColor(Color.WHITE)
        }

        calendarView = CalendarView(context).apply {
            setPadding(32, 32, 32, 32)
        }

        calendarCard.addView(calendarView)
        return calendarCard
    }

    private fun createSelectedDateInfo(): View {
        val context = requireContext()

        val infoCard = androidx.cardview.widget.CardView(context).apply {
            layoutParams = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(24, 0, 24, 16)
            }
            radius = 16f
            cardElevation = 4f
            setCardBackgroundColor(Color.WHITE)
        }

        val infoLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 24, 32, 24)
        }

        val selectedDateText = TextView(context).apply {
            text = "📅 선택된 날짜: ${SimpleDateFormat("yyyy년 MM월 dd일", Locale.KOREAN).format(selectedDate.time)}"
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.parseColor("#212121"))
            tag = "selectedDateText"
        }

        val statsLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 16, 0, 0)
        }

        val spendingChip = Chip(context).apply {
            text = "💰 지출: 0원"
            chipBackgroundColor = android.content.res.ColorStateList.valueOf(Color.parseColor("#FFE0B2"))
            setTextColor(Color.parseColor("#E65100"))
            tag = "spendingChip"
        }

        val eventsChip = Chip(context).apply {
            text = "📋 일정: 0개"
            chipBackgroundColor = android.content.res.ColorStateList.valueOf(Color.parseColor("#E3F2FD"))
            setTextColor(Color.parseColor("#1976D2"))
            tag = "eventsChip"
        }

        statsLayout.addView(spendingChip)
        statsLayout.addView(eventsChip)

        infoLayout.addView(selectedDateText)
        infoLayout.addView(statsLayout)
        infoCard.addView(infoLayout)

        return infoCard
    }

    private fun createEventsSection(): View {
        val context = requireContext()

        val eventsCard = androidx.cardview.widget.CardView(context).apply {
            layoutParams = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                800 // 고정 높이
            ).apply {
                setMargins(24, 0, 24, 24)
            }
            radius = 16f
            cardElevation = 4f
            setCardBackgroundColor(Color.WHITE)
        }

        val eventsLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 24, 32, 24)
        }

        val sectionTitle = TextView(context).apply {
            text = "📋 일정 및 지출 내역"
            textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.parseColor("#212121"))
            setPadding(0, 0, 0, 24)
        }

        eventsRecyclerView = RecyclerView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        eventsLayout.addView(sectionTitle)
        eventsLayout.addView(eventsRecyclerView)
        eventsCard.addView(eventsLayout)

        return eventsCard
    }

    private fun initializeComponents() {
        // 태그로 뷰 찾기
        monthYearText = rootView.findViewWithTag("selectedDateText") ?: monthYearText
    }

    private fun checkUserType() {
        val googleAccount = GoogleSignIn.getLastSignedInAccount(requireContext())
        isGoogleUser = googleAccount != null

        userTypeIndicator.text = if (isGoogleUser) {
            "🔗 Google 연동"
        } else {
            "📱 로컬 캘린더"
        }

        if (isGoogleUser) {
            googleCalendarHelper = GoogleCalendarHelper(requireContext(), googleAccount!!)
        }
    }

    private fun setupCalendar() {
        updateMonthYearText()

        calendarView.setOnDateChangeListener { _, year, month, dayOfMonth ->
            selectedDate.set(year, month, dayOfMonth)
            updateSelectedDateInfo()
            loadEventsForSelectedDate()
        }
    }

    private fun setupRecyclerView() {
        eventsAdapter = CalendarEventsAdapter(events) { event ->
            showEventOptionsDialog(event)
        }

        eventsRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = eventsAdapter
        }
    }

    private fun setupEventListeners() {
        todayButton.setOnClickListener {
            selectedDate = Calendar.getInstance()
            calendarView.date = selectedDate.timeInMillis
            updateSelectedDateInfo()
            loadEventsForSelectedDate()
        }

        addEventFab.setOnClickListener {
            showAddEventDialog()
        }
    }

    private fun loadInitialData() {
        loadEventsForSelectedDate()
        loadSpendingData()
    }

    private fun updateMonthYearText() {
        val format = SimpleDateFormat("yyyy년 MM월", Locale.KOREAN)
        monthYearText.text = format.format(selectedDate.time)
    }

    private fun updateSelectedDateInfo() {
        val selectedDateText = rootView.findViewWithTag<TextView>("selectedDateText")
        val spendingChip = rootView.findViewWithTag<Chip>("spendingChip")
        val eventsChip = rootView.findViewWithTag<Chip>("eventsChip")

        val format = SimpleDateFormat("yyyy년 MM월 dd일", Locale.KOREAN)
        selectedDateText?.text = "📅 선택된 날짜: ${format.format(selectedDate.time)}"

        updateMonthYearText()

        // 통계 업데이트
        val dateKey = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(selectedDate.time)
        val spending = spendingData[dateKey]?.sumOf { it.amount } ?: 0
        val eventCount = events.count {
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(it.date.toDate()) == dateKey
        }

        spendingChip?.text = "💰 지출: ${String.format("%,d", spending)}원"
        eventsChip?.text = "📋 일정: ${eventCount}개"
    }

    private fun loadEventsForSelectedDate() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        val calendar = selectedDate.clone() as Calendar
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfDay = Timestamp(calendar.time)

        calendar.add(Calendar.DAY_OF_MONTH, 1)
        val startOfNextDay = Timestamp(calendar.time)

        events.clear()

        // 로컬 이벤트 로드
        db.collection("users").document(uid).collection("events")
            .whereGreaterThanOrEqualTo("date", startOfDay)
            .whereLessThan("date", startOfNextDay)
            .get()
            .addOnSuccessListener { documents ->
                for (doc in documents) {
                    val event = CalendarEvent(
                        id = doc.id,
                        title = doc.getString("title") ?: "",
                        description = doc.getString("description") ?: "",
                        date = doc.getTimestamp("date") ?: Timestamp.now(),
                        type = EventType.LOCAL,
                        category = doc.getString("category") ?: "기본"
                    )
                    events.add(event)
                }
                eventsAdapter.notifyDataSetChanged()
                updateSelectedDateInfo()
            }

        // Google 캘린더 이벤트 로드 (Google 사용자인 경우)
        if (isGoogleUser) {
            googleCalendarHelper?.loadEventsForDate(selectedDate.time) { googleEvents ->
                for (googleEvent in googleEvents) {
                    events.add(googleEvent)
                }
                eventsAdapter.notifyDataSetChanged()
                updateSelectedDateInfo()
            }
        }

        // 지출 데이터도 이벤트로 추가
        loadSpendingAsEvents(startOfDay, startOfNextDay)
    }

    private fun loadSpendingAsEvents(startOfDay: Timestamp, startOfNextDay: Timestamp) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        db.collection("users").document(uid).collection("spending")
            .whereGreaterThanOrEqualTo("date", startOfDay)
            .whereLessThan("date", startOfNextDay)
            .get()
            .addOnSuccessListener { documents ->
                for (doc in documents) {
                    val amount = doc.getLong("amount")?.toInt() ?: 0
                    val category = doc.getString("category") ?: "기타"
                    val memo = doc.getString("memo") ?: ""

                    val spendingEvent = CalendarEvent(
                        id = doc.id,
                        title = "💰 ${category} - ${String.format("%,d", amount)}원",
                        description = memo,
                        date = doc.getTimestamp("date") ?: Timestamp.now(),
                        type = EventType.SPENDING,
                        category = category
                    )
                    events.add(spendingEvent)
                }
                eventsAdapter.notifyDataSetChanged()
                updateSelectedDateInfo()
            }
    }

    private fun loadSpendingData() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        db.collection("users").document(uid).collection("spending")
            .get()
            .addOnSuccessListener { documents ->
                spendingData.clear()
                for (doc in documents) {
                    val date = doc.getTimestamp("date")?.toDate() ?: continue
                    val dateKey = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(date)

                    val spending = SpendingData(
                        amount = doc.getLong("amount")?.toInt() ?: 0,
                        category = doc.getString("category") ?: "",
                        memo = doc.getString("memo") ?: ""
                    )

                    if (spendingData[dateKey] == null) {
                        spendingData[dateKey] = mutableListOf()
                    }
                    (spendingData[dateKey] as MutableList).add(spending)
                }
                updateSelectedDateInfo()
            }
    }

    private fun showAddEventDialog() {
        val context = requireContext()

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 50, 50, 50)
        }

        // 제목
        val titleLabel = TextView(context).apply {
            text = "일정 제목"
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 16)
        }

        val titleInput = EditText(context).apply {
            hint = "일정 제목을 입력하세요"
            setPadding(24, 24, 24, 24)
            setBackgroundColor(Color.parseColor("#F5F5F5"))
        }

        // 설명
        val descLabel = TextView(context).apply {
            text = "설명 (선택사항)"
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 32, 0, 16)
        }

        val descInput = EditText(context).apply {
            hint = "일정 설명을 입력하세요"
            setPadding(24, 24, 24, 24)
            setBackgroundColor(Color.parseColor("#F5F5F5"))
            maxLines = 3
        }

        // 카테고리
        val categoryLabel = TextView(context).apply {
            text = "카테고리"
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 32, 0, 16)
        }

        val categorySpinner = Spinner(context).apply {
            val categories = listOf("업무", "개인", "약속", "기념일", "운동", "학습", "기타")
            val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, categories)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            this.adapter = adapter
        }

        // 시간 설정
        val timeLabel = TextView(context).apply {
            text = "시간 설정"
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 32, 0, 16)
        }

        var selectedTime = selectedDate.clone() as Calendar
        val timeButton = Button(context).apply {
            text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(selectedTime.time)
            setOnClickListener {
                TimePickerDialog(context, { _, hour, minute ->
                    selectedTime.set(Calendar.HOUR_OF_DAY, hour)
                    selectedTime.set(Calendar.MINUTE, minute)
                    text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(selectedTime.time)
                }, selectedTime.get(Calendar.HOUR_OF_DAY), selectedTime.get(Calendar.MINUTE), true).show()
            }
        }

        layout.addView(titleLabel)
        layout.addView(titleInput)
        layout.addView(descLabel)
        layout.addView(descInput)
        layout.addView(categoryLabel)
        layout.addView(categorySpinner)
        layout.addView(timeLabel)
        layout.addView(timeButton)

        AlertDialog.Builder(context)
            .setTitle("📅 새 일정 추가")
            .setView(layout)
            .setPositiveButton("추가") { _, _ ->
                val title = titleInput.text.toString().trim()
                val description = descInput.text.toString().trim()
                val category = categorySpinner.selectedItem.toString()

                if (title.isNotEmpty()) {
                    addNewEvent(title, description, category, Timestamp(selectedTime.time))
                } else {
                    Toast.makeText(context, "제목을 입력해주세요", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun addNewEvent(title: String, description: String, category: String, eventTime: Timestamp) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        val eventData = hashMapOf(
            "title" to title,
            "description" to description,
            "category" to category,
            "date" to eventTime,
            "createdAt" to Timestamp.now()
        )

        if (isGoogleUser) {
            // Google 캘린더에도 추가
            googleCalendarHelper?.addEvent(title, description, eventTime.toDate()) { success ->
                if (success) {
                    Toast.makeText(requireContext(), "✅ Google 캘린더에 일정이 추가되었습니다!", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Firestore에 로컬 이벤트 저장
        db.collection("users").document(uid).collection("events")
            .add(eventData)
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "✅ 일정이 추가되었습니다!", Toast.LENGTH_SHORT).show()
                loadEventsForSelectedDate()
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "❌ 일정 추가 실패: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showEventOptionsDialog(event: CalendarEvent) {
        val options = when (event.type) {
            EventType.SPENDING -> arrayOf("📊 상세보기", "✏️ 수정", "🗑️ 삭제")
            EventType.GOOGLE -> arrayOf("📊 상세보기", "🔗 Google에서 열기")
            EventType.LOCAL -> arrayOf("📊 상세보기", "✏️ 수정", "🗑️ 삭제")
        }

        AlertDialog.Builder(requireContext())
            .setTitle(event.title)
            .setItems(options) { _, which ->
                when (event.type) {
                    EventType.SPENDING -> {
                        when (which) {
                            0 -> showSpendingDetail(event)
                            1 -> editSpending(event)
                            2 -> deleteSpending(event)
                        }
                    }
                    EventType.GOOGLE -> {
                        when (which) {
                            0 -> showEventDetail(event)
                            1 -> openInGoogleCalendar(event)
                        }
                    }
                    EventType.LOCAL -> {
                        when (which) {
                            0 -> showEventDetail(event)
                            1 -> editEvent(event)
                            2 -> deleteEvent(event)
                        }
                    }
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun showEventDetail(event: CalendarEvent) {
        val message = buildString {
            append("📋 제목: ${event.title}\n\n")
            if (event.description.isNotEmpty()) {
                append("📝 설명: ${event.description}\n\n")
            }
            append("📂 카테고리: ${event.category}\n")
            append("🕐 시간: ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(event.date.toDate())}\n")
            append("📱 타입: ${when(event.type) {
                EventType.LOCAL -> "로컬 일정"
                EventType.GOOGLE -> "Google 캘린더"
                EventType.SPENDING -> "지출 내역"
            }}")
        }

        AlertDialog.Builder(requireContext())
            .setTitle("📊 일정 상세정보")
            .setMessage(message)
            .setPositiveButton("확인", null)
            .show()
    }

    private fun showSpendingDetail(event: CalendarEvent) {
        // 지출 상세보기 구현
        showEventDetail(event)
    }

    private fun editSpending(event: CalendarEvent) {
        // 지출 수정 (기존 SpendingFragment 로직 활용)
        Toast.makeText(requireContext(), "지출 수정 기능", Toast.LENGTH_SHORT).show()
    }

    private fun deleteSpending(event: CalendarEvent) {
        AlertDialog.Builder(requireContext())
            .setTitle("🗑️ 지출 삭제")
            .setMessage("이 지출 내역을 삭제하시겠습니까?")
            .setPositiveButton("삭제") { _, _ ->
                val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@setPositiveButton
                db.collection("users").document(uid).collection("spending")
                    .document(event.id)
                    .delete()
                    .addOnSuccessListener {
                        Toast.makeText(requireContext(), "✅ 지출이 삭제되었습니다", Toast.LENGTH_SHORT).show()
                        loadEventsForSelectedDate()
                    }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun editEvent(event: CalendarEvent) {
        // 일정 수정 구현
        Toast.makeText(requireContext(), "일정 수정 기능", Toast.LENGTH_SHORT).show()
    }

    private fun deleteEvent(event: CalendarEvent) {
        AlertDialog.Builder(requireContext())
            .setTitle("🗑️ 일정 삭제")
            .setMessage("이 일정을 삭제하시겠습니까?")
            .setPositiveButton("삭제") { _, _ ->
                val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@setPositiveButton
                db.collection("users").document(uid).collection("events")
                    .document(event.id)
                    .delete()
                    .addOnSuccessListener {
                        Toast.makeText(requireContext(), "✅ 일정이 삭제되었습니다", Toast.LENGTH_SHORT).show()
                        loadEventsForSelectedDate()
                    }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun openInGoogleCalendar(event: CalendarEvent) {
        // Google 캘린더에서 열기
        Toast.makeText(requireContext(), "Google 캘린더에서 열기", Toast.LENGTH_SHORT).show()
    }
}
