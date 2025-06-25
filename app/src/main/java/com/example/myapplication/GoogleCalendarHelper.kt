package com.example.myapplication

import android.content.Context
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.calendar.Calendar
import com.google.api.services.calendar.model.Event
import com.google.api.services.calendar.model.EventDateTime
import com.google.firebase.Timestamp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.*
import com.google.api.client.util.DateTime



object GoogleCalendarHelper {

    fun insertExpenseEvent(
        context: Context,
        account: GoogleSignInAccount,
        title: String,
        description: String,
        timestamp: Timestamp
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val credential = GoogleAccountCredential.usingOAuth2(
                    context,
                    listOf("https://www.googleapis.com/auth/calendar")
                )
                credential.selectedAccount = account.account

                val service = Calendar.Builder(
                    GoogleNetHttpTransport.newTrustedTransport(),
                    GsonFactory.getDefaultInstance(),
                    credential
                ).setApplicationName("MyApp").build()

                // 🔧 날짜 설정
                val calendar = java.util.Calendar.getInstance()
                calendar.time = timestamp.toDate() // Timestamp -> Date

                val start = DateTime(calendar.time)
                val end = DateTime(calendar.time) // 종료 시간 동일

                val event = Event()
                    .setSummary(title)
                    .setDescription(description)
                    .setStart(EventDateTime().setDateTime(start))
                    .setEnd(EventDateTime().setDateTime(end))

                service.events().insert("primary", event).execute()

                Log.d("Calendar", "✅ Google Calendar에 이벤트 등록 성공")
            } catch (e: Exception) {
                Log.e("Calendar", "❌ 이벤트 등록 실패: ${e.message}")
            }
        }
    }
}
