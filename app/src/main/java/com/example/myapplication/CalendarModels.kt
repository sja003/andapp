package com.example.myapplication

import com.google.firebase.Timestamp

data class CalendarEvent(
    val id: String,
    val title: String,
    val description: String,
    val date: Timestamp,
    val type: EventType,
    val category: String
)

enum class EventType {
    LOCAL,    // 로컬 일정
    GOOGLE,   // Google 캘린더 일정
    SPENDING  // 지출 내역
}

data class SpendingData(
    val amount: Int,
    val category: String,
    val memo: String
)

data class LocalEvent(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val date: Timestamp = Timestamp.now(),
    val createdAt: Timestamp = Timestamp.now()
)

data class CalendarIndicator(
    val date: String,
    val hasSpending: Boolean,
    val hasEvent: Boolean,
    val spendingAmount: Int = 0,
    val eventCount: Int = 0
)