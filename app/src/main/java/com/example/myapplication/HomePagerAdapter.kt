package com.example.myapplication

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.myapplication.DailyFragment
import com.example.myapplication.CalendarFragment
import com.example.myapplication.MonthlyFragment

class HomePagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {

    private val tabFragments = listOf(
        DailyFragment(),
        CalendarFragment(),
        MonthlyFragment()
    )

    override fun getItemCount() = tabFragments.size

    override fun createFragment(position: Int) = tabFragments[position]

    override fun getItemId(position: Int): Long {
        // 각 position을 고유한 ID로 반환
        return position.toLong()
    }

    override fun containsItem(itemId: Long): Boolean {
        // 항상 유효한 position만 반환
        return itemId in 0 until itemCount
    }
}

