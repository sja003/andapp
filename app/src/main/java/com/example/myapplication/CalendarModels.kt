package com.example.myapplication

import com.google.firebase.Timestamp

// 이벤트 타입 열거형
enum class EventType {
    LOCAL,    // 앱 내에서 생성된 일정
    GOOGLE,   // Google 캘린더에서 가져온 일정
    SPENDING  // 지출 내역
}

// 캘린더 이벤트 데이터 클래스
data class CalendarEvent(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val date: Timestamp = Timestamp.now(),
    val type: EventType = EventType.LOCAL,
    val category: String = "",
    val amount: Int = 0,
    val isRepeating: Boolean = false,
    val repeatType: String? = null,
    val repeatInterval: Int = 1,
    val endDate: Timestamp? = null,
    val reminder: Boolean = false,
    val reminderMinutes: Int = 15,
    val location: String = "",
    val attendees: List<String> = emptyList(),
    val color: String = "#2196F3",
    val allDay: Boolean = false,
    val startTime: String = "",
    val endTime: String = "",
    val createdAt: Timestamp = Timestamp.now(),
    val updatedAt: Timestamp = Timestamp.now()
)

// 캘린더 날짜 정보 데이터 클래스
data class CalendarDay(
    val date: String = "",
    val day: Int = 0,
    val month: Int = 0,
    val year: Int = 0,
    val hasEvents: Boolean = false,
    val hasSpending: Boolean = false,
    val eventCount: Int = 0,
    val spendingCount: Int = 0,
    val totalSpending: Int = 0,
    val isToday: Boolean = false,
    val isSelected: Boolean = false,
    val isCurrentMonth: Boolean = true
)

// 월별 통계 데이터 클래스
data class MonthlyStats(
    val totalEvents: Int = 0,
    val totalSpending: Int = 0,
    val activeDays: Int = 0,
    val averageDailySpending: Double = 0.0,
    val topSpendingCategory: String = "",
    val topSpendingAmount: Int = 0,
    val eventsByType: Map<EventType, Int> = emptyMap(),
    val spendingByCategory: Map<String, Int> = emptyMap(),
    val dailyAverages: Map<String, Double> = emptyMap()
)

// 반복 일정 타입
enum class RepeatType(val displayName: String) {
    NONE("반복 안함"),
    DAILY("매일"),
    WEEKLY("매주"),
    MONTHLY("매월"),
    YEARLY("매년"),
    WEEKDAYS("평일"),
    WEEKENDS("주말"),
    CUSTOM("사용자 정의")
}

// 캘린더 뷰 모드
enum class CalendarViewMode {
    MONTH,   // 월 보기
    WEEK,    // 주 보기
    DAY,     // 일 보기
    AGENDA   // 일정 목록 보기
}

// 이벤트 필터 옵션
data class EventFilter(
    val showLocalEvents: Boolean = true,
    val showGoogleEvents: Boolean = true,
    val showSpending: Boolean = true,
    val categories: Set<String> = emptySet(),
    val dateRange: Pair<Timestamp?, Timestamp?> = Pair(null, null),
    val amountRange: Pair<Int?, Int?> = Pair(null, null),
    val searchQuery: String = ""
)