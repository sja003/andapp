package com.example.myapplication

import android.animation.ValueAnimator
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.databinding.FragmentHomeBinding
import com.google.android.material.tabs.TabLayoutMediator
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var pagerAdapter: HomePagerAdapter
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupViewPager()
        loadSummaryData()
    }

    override fun onResume() {
        super.onResume()
        // 화면이 다시 보일 때마다 데이터 새로고침
        loadSummaryData()
        Log.d("HomeFragment", "onResume - 데이터 새로고침")
    }

    private fun setupViewPager() {
        pagerAdapter = HomePagerAdapter(this)
        binding.viewPager.adapter = pagerAdapter

        val tabTitles = listOf("📅 오늘", "📊 달력", "📈 월별", "📋 전체")
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = tabTitles[position]
        }.attach()

        // 첫 번째 탭 선택
        binding.tabLayout.selectTab(binding.tabLayout.getTabAt(0))
    }

    private fun loadSummaryData() {
        val currentUser = auth.currentUser ?: return

        lifecycleScope.launch {
            try {
                val calendar = Calendar.getInstance()

                // 오늘 날짜 범위
                val todayStart = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }

                val todayEnd = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                    set(Calendar.MILLISECOND, 999)
                }

                // 이번 주 시작 (월요일)
                val weekStart = Calendar.getInstance().apply {
                    set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }

                // 이번 달 시작
                val monthStart = Calendar.getInstance().apply {
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }

                // Firestore에서 데이터 조회
                val spendingSnapshot = db.collection("users")
                    .document(currentUser.uid)
                    .collection("spending")
                    .get()
                    .await()

                var todayTotal = 0
                var weeklyTotal = 0
                var monthlyTotal = 0

                for (document in spendingSnapshot.documents) {
                    val amount = (document.getLong("amount") ?: 0).toInt()
                    val timestamp = document.getTimestamp("date")?.toDate() ?: continue

                    val expenseCalendar = Calendar.getInstance().apply {
                        time = timestamp
                    }

                    // 오늘 지출
                    if (expenseCalendar.after(todayStart) && expenseCalendar.before(todayEnd)) {
                        todayTotal += amount
                    }

                    // 이번 주 지출
                    if (expenseCalendar.after(weekStart)) {
                        weeklyTotal += amount
                    }

                    // 이번 달 지출
                    if (expenseCalendar.after(monthStart)) {
                        monthlyTotal += amount
                    }
                }

                // UI 업데이트 (애니메이션과 함께)
                updateSummaryUI(todayTotal, weeklyTotal, monthlyTotal)

                Log.d("HomeFragment", "요약 데이터 로드 완료: 오늘 ￦$todayTotal, 주간 ￦$weeklyTotal, 월간 ￦$monthlyTotal")

            } catch (e: Exception) {
                Log.e("HomeFragment", "요약 데이터 로드 실패", e)
                // 에러 처리
                binding.tvMonthlyTotal.text = "데이터 로드 실패"
                binding.tvTodayTotal.text = "￦0"
                binding.tvWeeklyTotal.text = "￦0"
            }
        }
    }

    private fun updateSummaryUI(todayTotal: Int, weeklyTotal: Int, monthlyTotal: Int) {
        val numberFormat = NumberFormat.getInstance(Locale.KOREA)

        // 월별 총액 애니메이션
        animateAmount(binding.tvMonthlyTotal, 0, monthlyTotal) { amount ->
            "￦${numberFormat.format(amount)}"
        }

        // 오늘 총액 애니메이션
        animateAmount(binding.tvTodayTotal, 0, todayTotal) { amount ->
            "￦${numberFormat.format(amount)}"
        }

        // 주간 총액 애니메이션
        animateAmount(binding.tvWeeklyTotal, 0, weeklyTotal) { amount ->
            "￦${numberFormat.format(amount)}"
        }
    }

    private fun animateAmount(
        textView: android.widget.TextView,
        startValue: Int,
        endValue: Int,
        formatter: (Int) -> String
    ) {
        val animator = ValueAnimator.ofInt(startValue, endValue)
        animator.duration = 1500 // 1.5초
        animator.interpolator = DecelerateInterpolator()

        animator.addUpdateListener { animation ->
            val animatedValue = animation.animatedValue as Int
            textView.text = formatter(animatedValue)
        }

        animator.start()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}