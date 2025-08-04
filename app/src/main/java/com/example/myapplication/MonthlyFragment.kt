package com.example.myapplication

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class MonthlyFragment : Fragment() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private lateinit var recyclerView: RecyclerView
    private lateinit var monthlyAdapter: MonthlyAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return createMonthlyLayout()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        loadMonthlyData()
    }

    private fun createMonthlyLayout(): View {
        val context = requireContext()
        val scrollView = androidx.core.widget.NestedScrollView(context)

        val mainLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }

        // 헤더 카드
        val headerCard = createHeaderCard()
        mainLayout.addView(headerCard)

        // 월별 요약 카드
        val summaryCard = createSummaryCard()
        mainLayout.addView(summaryCard)

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
            text = "📈 월별 분석"
            textSize = 20f
            setTypeface(null, android.graphics.Typeface.BOLD)
        }

        val subtitleText = TextView(context).apply {
            text = "월별 지출 추이를 분석하세요"
            textSize = 14f
            setPadding(0, 4, 0, 0)
        }

        layout.addView(titleText)
        layout.addView(subtitleText)
        cardView.addView(layout)
        return cardView
    }

    private fun createSummaryCard(): View {
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

        val titleText = TextView(context).apply {
            text = "월별 지출 현황"
            textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 16)
        }

        recyclerView = RecyclerView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            isNestedScrollingEnabled = false
        }

        layout.addView(titleText)
        layout.addView(recyclerView)
        cardView.addView(layout)
        return cardView
    }

    private fun setupRecyclerView() {
        monthlyAdapter = MonthlyAdapter()
        recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = monthlyAdapter
        }
    }

    private fun loadMonthlyData() {
        val currentUser = auth.currentUser ?: return

        lifecycleScope.launch {
            try {
                val snapshot = db.collection("users")
                    .document(currentUser.uid)
                    .collection("spending")
                    .get()
                    .await()

                val monthlyMap = mutableMapOf<String, Int>()
                val dateFormat = SimpleDateFormat("yyyy년 MM월", Locale.KOREAN)

                for (document in snapshot.documents) {
                    val amount = (document.getLong("amount") ?: 0).toInt()
                    val timestamp = document.getTimestamp("date")?.toDate() ?: continue

                    val monthKey = dateFormat.format(timestamp)
                    monthlyMap[monthKey] = monthlyMap.getOrDefault(monthKey, 0) + amount
                }

                val monthlySummaries = monthlyMap.map { (month, amount) ->
                    MonthlySummary(month, amount)
                }.sortedByDescending { it.month }

                monthlyAdapter.updateData(monthlySummaries)

            } catch (e: Exception) {
                // 에러 처리
            }
        }
    }
}