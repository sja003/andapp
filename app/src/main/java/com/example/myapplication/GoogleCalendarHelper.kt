package com.example.myapplication

import android.content.Context
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.DateTime
import com.google.api.services.calendar.Calendar
import com.google.api.services.calendar.CalendarScopes
import com.google.api.services.calendar.model.Event
import com.google.api.services.calendar.model.EventDateTime
import com.google.firebase.Timestamp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.google.api.client.extensions.android.http.AndroidHttp
import java.util.*

class GoogleCalendarHelper(
    private val context: Context,
    private val account: GoogleSignInAccount
) {
    private val credential: GoogleAccountCredential
    private val calendarService: Calendar

    init {
        credential = GoogleAccountCredential.usingOAuth2(
            context,
            listOf(CalendarScopes.CALENDAR)
        )
        credential.selectedAccount = account.account

        calendarService = Calendar.Builder(
            AndroidHttp.newCompatibleTransport(),  // ✅ Android 환경용으로 변경
            GsonFactory.getDefaultInstance(),
            credential
        ).setApplicationName("MyApplication").build()
    }

    fun loadEventsForDate(date: Date, callback: (List<CalendarEvent>) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val calendar = java.util.Calendar.getInstance().apply {
                    time = date
                    set(java.util.Calendar.HOUR_OF_DAY, 0)
                    set(java.util.Calendar.MINUTE, 0)
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }

                val startTime = DateTime(calendar.time)
                calendar.add(java.util.Calendar.DAY_OF_MONTH, 1)
                val endTime = DateTime(calendar.time)

                val events = calendarService.events().list("primary")
                    .setTimeMin(startTime)
                    .setTimeMax(endTime)
                    .setOrderBy("startTime")
                    .setSingleEvents(true)
                    .execute()

                val calendarEvents = events.items.map { event ->
                    val eventTime = event.start.dateTime ?: event.start.date
                    CalendarEvent(
                        id = event.id ?: "",
                        title = event.summary ?: "제목 없음",
                        description = event.description ?: "",
                        date = Timestamp(Date(eventTime.value)),
                        type = EventType.GOOGLE,
                        category = "Google 캘린더"
                    )
                }

                withContext(Dispatchers.Main) {
                    callback(calendarEvents)
                }

                Log.d("GoogleCalendar", "✅ Google 캘린더 이벤트 로드 성공: ${calendarEvents.size}개")

            } catch (e: Exception) {
                Log.e("GoogleCalendar", "❌ Google 캘린더 이벤트 로드 실패", e)
                withContext(Dispatchers.Main) {
                    callback(emptyList())
                }
            }
        }
    }

    fun addEvent(title: String, description: String, date: Date, callback: (Boolean) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val event = Event().apply {
                    summary = title
                    this.description = description

                    val eventDateTime = DateTime(date)
                    start = EventDateTime().setDateTime(eventDateTime)
                    end = EventDateTime().setDateTime(eventDateTime)
                }

                calendarService.events().insert("primary", event).execute()

                withContext(Dispatchers.Main) {
                    callback(true)
                }

                Log.d("GoogleCalendar", "✅ Google 캘린더 이벤트 추가 성공")

            } catch (e: Exception) {
                Log.e("GoogleCalendar", "❌ Google 캘린더 이벤트 추가 실패", e)
                withContext(Dispatchers.Main) {
                    callback(false)
                }
            }
        }
    }

    fun insertExpenseEvent(title: String, description: String, timestamp: Timestamp) {
        addEvent(title, description, timestamp.toDate()) { success ->
            if (success) {
                Log.d("GoogleCalendar", "✅ 지출 이벤트 Google 캘린더 추가 성공")
            } else {
                Log.e("GoogleCalendar", "❌ 지출 이벤트 Google 캘린더 추가 실패")
            }
        }
    }
}