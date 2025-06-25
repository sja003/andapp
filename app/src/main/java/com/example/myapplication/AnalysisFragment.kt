package com.example.myapplication

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.myapplication.databinding.FragmentAnalysisBinding
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class AnalysisFragment : Fragment() {

    private var _binding: FragmentAnalysisBinding? = null
    private val binding get() = _binding!!
    private lateinit var pieChart: PieChart

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    // 아이콘과 색상 매핑
    private val categoryIcons = mapOf(
        "식비" to "🍽️",
        "교통" to "🚗",
        "패션" to "👗",
        "문화" to "🎬",
        "의료" to "💊",
        "기타" to "📦"
    )

    private val categoryColors = mapOf(
        "식비" to "#FF8A65",
        "교통" to "#4DB6AC",
        "패션" to "#9575CD",
        "문화" to "#F06292",
        "의료" to "#BA68C8",
        "기타" to "#4FC3F7"
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAnalysisBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        pieChart = binding.pieChart
        loadCategoryData()
    }

    private fun loadCategoryData() {
        val currentUser = auth.currentUser ?: return

        db.collection("users").document(currentUser.uid).collection("spending")
            .get()
            .addOnSuccessListener { result ->
                val categoryMap = mutableMapOf<String, Int>()
                var totalAmount = 0

                for (doc in result) {
                    val category = doc.getString("category") ?: "기타"
                    val amount = (doc.getLong("amount") ?: 0).toInt()
                    categoryMap[category] = categoryMap.getOrDefault(category, 0) + amount
                    totalAmount += amount
                }

                // 원형차트 데이터 구성
                val entries = categoryMap.map { (category, amount) ->
                    PieEntry(amount.toFloat(), "$category (${(amount * 100 / totalAmount)}%)")
                }

                val colors = categoryMap.map { (category, _) ->
                    Color.parseColor(categoryColors[category] ?: "#9E9E9E")
                }

                val dataSet = PieDataSet(entries, "카테고리별 지출")
                dataSet.colors = colors
                dataSet.valueTextSize = 14f
                dataSet.valueTextColor = Color.WHITE

                val pieData = PieData(dataSet)
                pieChart.data = pieData
                pieChart.setUsePercentValues(false)
                pieChart.setEntryLabelColor(Color.BLACK)
                pieChart.description.isEnabled = false
                pieChart.invalidate()

                // 🔽 카테고리 요약 출력
                showCategoryLegend(categoryMap)
            }
    }

    private fun showCategoryLegend(categoryMap: Map<String, Int>) {
        binding.categorySummaryLayout.removeAllViews()

        for ((category, amount) in categoryMap) {
            val icon = categoryIcons[category] ?: "❔"
            val colorHex = categoryColors[category] ?: "#9E9E9E"

            val item = TextView(requireContext()).apply {
                text = "$icon $category: ${amount}원"
                setTextColor(Color.parseColor(colorHex))
                textSize = 16f
                setPadding(10, 10, 10, 10)
            }

            binding.categorySummaryLayout.addView(item)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
