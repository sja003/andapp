package com.example.myapplication

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter

class HomePagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {

    override fun getItemCount(): Int = 4 // 4개 탭

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> DailyFragment()      // 일간 (수정/삭제 기능 포함)
            1 -> CalendarFragment()   // 달력
            2 -> MonthlyFragment()    // 월별
            3 -> SpendingListFragment() // 전체 목록 (수정/삭제 기능 포함)
            else -> DailyFragment()
        }
    }
}
