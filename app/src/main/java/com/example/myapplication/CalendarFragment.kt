package com.example.myapplication

import android.app.AlertDialog
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.example.myapplication.databinding.FragmentCalendarBinding
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.DateTime
import com.google.api.services.calendar.CalendarScopes
import com.google.api.services.calendar.model.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.kizitonwose.calendar.core.CalendarDay
import com.kizitonwose.calendar.view.ViewContainer
import java.time.*
import java.util.*

class CalendarFragment : Fragment() {

    private var _binding: FragmentCalendarBinding? = null
    private val binding get() = _binding!!

    private var selectedDate: LocalDate? = null
    private lateinit var credential: GoogleAccountCredential
    private lateinit var calendarService: com.google.api.services.calendar.Calendar

    private val spendingDates = mutableSetOf<LocalDate>()
    private val googleEventDates = mutableSetOf<LocalDate>()
    private val localEventDates = mutableSetOf<LocalDate>()

    private var isGoogleUser = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCalendarBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val account = com.google.android.gms.auth.api.signin.GoogleSignIn.getLastSignedInAccount(requireContext())
        isGoogleUser = account != null

        if (isGoogleUser) {
            credential = GoogleAccountCredential.usingOAuth2(requireContext(), listOf(CalendarScopes.CALENDAR))
            credential.selectedAccount = account?.account
            calendarService = com.google.api.services.calendar.Calendar.Builder(
                AndroidHttp.newCompatibleTransport(), GsonFactory.getDefaultInstance(), credential
            ).setApplicationName("MyApplication").build()
            loadGoogleCalendarEvents()
        } else {
            loadLocalEventsFromFirestore()
        }

        loadSpendingDatesFromFirestore()

        val currentMonth = YearMonth.now()
        val startMonth = currentMonth.minusMonths(12)
        val endMonth = currentMonth.plusMonths(12)

        val calendarView = binding.calendarView
        calendarView.setup(startMonth, endMonth, DayOfWeek.SUNDAY)
        calendarView.scrollToMonth(currentMonth)

        calendarView.dayBinder = object : com.kizitonwose.calendar.view.MonthDayBinder<DayViewContainer> {
            override fun create(view: View): DayViewContainer = DayViewContainer(view)
            override fun bind(container: DayViewContainer, day: CalendarDay) {
                container.textView.text = day.date.dayOfMonth.toString()

                val hasSpending = spendingDates.contains(day.date)
                val hasEvent = if (isGoogleUser) googleEventDates.contains(day.date) else localEventDates.contains(day.date)

                container.moneyIcon.visibility = if (hasSpending) View.VISIBLE else View.GONE
                container.eventIcon.visibility = if (hasEvent) View.VISIBLE else View.GONE

                container.textView.setOnClickListener {
                    selectedDate = day.date
                    showEventsBottomSheetInline(day.date)
                }
            }
        }
    }

    private fun loadSpendingDatesFromFirestore() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()

        db.collection("users").document(uid).collection("spending")
            .get()
            .addOnSuccessListener { result ->
                spendingDates.clear()
                for (doc in result) {
                    val timestamp = doc.getTimestamp("date") ?: continue
                    val date = timestamp.toDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
                    spendingDates.add(date)
                }
                binding.calendarView.notifyCalendarChanged()
            }
    }

    private fun loadLocalEventsFromFirestore() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()

        db.collection("users").document(uid).collection("events")
            .get()
            .addOnSuccessListener { result ->
                localEventDates.clear()
                for (doc in result) {
                    val timestamp = doc.getTimestamp("date") ?: continue
                    val date = timestamp.toDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
                    localEventDates.add(date)
                }
                binding.calendarView.notifyCalendarChanged()
            }
    }

    private fun loadGoogleCalendarEvents() {
        Thread {
            try {
                val calendarList = calendarService.calendarList().list().execute()
                val now = DateTime(System.currentTimeMillis() - 1000L * 60 * 60 * 24 * 90)
                val future = DateTime(System.currentTimeMillis() + 1000L * 60 * 60 * 24 * 180)

                for (calendarListEntry in calendarList.items) {
                    val calendarId = calendarListEntry.id
                    val events = calendarService.events().list(calendarId)
                        .setTimeMin(now)
                        .setTimeMax(future)
                        .setOrderBy("startTime")
                        .setSingleEvents(true)
                        .execute()

                    for (event in events.items) {
                        val start = event.start.dateTime ?: event.start.date
                        val localDate = Date(start.value).toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
                        googleEventDates.add(localDate)
                    }
                }
                requireActivity().runOnUiThread { binding.calendarView.notifyCalendarChanged() }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    private fun showEventsBottomSheetInline(date: LocalDate) {
        val container = binding.bottomSheetContainer
        container.removeAllViews()
        container.visibility = View.VISIBLE
        container.minimumHeight = 800

        val title = TextView(requireContext()).apply {
            text = "\uD83D\uDCC5 선택된 날짜: $date"
            textSize = 18f
        }
        container.addView(title)

        val db = FirebaseFirestore.getInstance()
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val start = Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant())
        val end = Date.from(date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant())

        db.collection("users").document(uid).collection("spending")
            .whereGreaterThanOrEqualTo("date", start)
            .whereLessThan("date", end)
            .get()
            .addOnSuccessListener { result ->
                if (!result.isEmpty) {
                    val spendingText = TextView(requireContext())
                    spendingText.text = "\uD83D\uDCB8 지출 내역:\n" + result.joinToString("\n") { doc ->
                        val category = doc.getString("category") ?: "카테고리 없음"
                        val amount = doc.getLong("amount") ?: 0
                        val memo = doc.getString("memo") ?: ""
                        "- [$category] ${amount}원 (${memo})"
                    }
                    container.addView(spendingText)
                }
            }

        val eventList = TextView(requireContext())
        container.addView(eventList)

        if (isGoogleUser) {
            Thread {
                try {
                    val startDate = GregorianCalendar.from(date.atStartOfDay(ZoneId.systemDefault())).time
                    val endDate = GregorianCalendar.from(date.plusDays(1).atStartOfDay(ZoneId.systemDefault())).time
                    val events = calendarService.events().list("primary")
                        .setTimeMin(DateTime(startDate))
                        .setTimeMax(DateTime(endDate))
                        .setOrderBy("startTime")
                        .setSingleEvents(true)
                        .execute()

                    val items = events.items.map { it.summary ?: "(제목 없음)" }
                    requireActivity().runOnUiThread {
                        if (items.isNotEmpty()) {
                            eventList.text = "\uD83D\uDCCC 일정 목록:\n" + items.joinToString("\n")
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }.start()
        } else {
            db.collection("users").document(uid).collection("events")
                .whereGreaterThanOrEqualTo("date", start)
                .whereLessThan("date", end)
                .get()
                .addOnSuccessListener { result ->
                    val items = result.map { it.getString("summary") ?: "(제목 없음)" }
                    if (items.isNotEmpty()) {
                        eventList.text = "\uD83D\uDCCC 일정 목록:\n" + items.joinToString("\n")
                    }
                }
        }

        val addButton = Button(requireContext()).apply {
            text = "지출 및 일정 추가"
            setOnClickListener { showAddDialog(date) }
        }
        container.addView(addButton)
    }

    private fun showAddDialog(date: LocalDate) {
        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 10)
        }
        val titleInput = EditText(requireContext()).apply { hint = "이벤트 제목" }

        layout.addView(titleInput)

        AlertDialog.Builder(requireContext())
            .setTitle("지출 및 일정 추가")
            .setView(layout)
            .setPositiveButton("추가") { _, _ ->
                val summary = titleInput.text.toString()
                val timestamp = com.google.firebase.Timestamp(Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant()))

                val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@setPositiveButton
                val db = FirebaseFirestore.getInstance()


                if (isGoogleUser) {
                    // ✅ Google Calendar 이벤트 추가
                    val event = Event().setSummary(summary)
                    val eventTime = DateTime(Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant()))
                    event.start = EventDateTime().setDateTime(eventTime)
                    event.end = EventDateTime().setDateTime(eventTime)

                    Thread {
                        try {
                            calendarService.events().insert("primary", event).execute()
                            googleEventDates.add(date)
                            requireActivity().runOnUiThread {
                                binding.calendarView.notifyCalendarChanged()
                                showEventsBottomSheetInline(date)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }.start()
                } else {
                    // ✅ Firestore 로컬 이벤트 추가
                    val localEvent = hashMapOf("summary" to summary, "date" to timestamp)
                    db.collection("users").document(uid).collection("events").add(localEvent)
                        .addOnSuccessListener {
                            localEventDates.add(date)
                            requireActivity().runOnUiThread {
                                binding.calendarView.notifyCalendarChanged()
                                showEventsBottomSheetInline(date)
                            }
                        }
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }




    inner class DayViewContainer(view: View) : ViewContainer(view) {
        val textView: TextView = view.findViewById(R.id.calendarDayText)
        val moneyIcon: ImageView = view.findViewById(R.id.calendarMoneyIcon)
        val eventIcon: ImageView = view.findViewById(R.id.calendarEventIcon)
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
