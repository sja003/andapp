package com.example.myapplication

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.myapplication.databinding.FragmentAnalysisBinding
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.PercentFormatter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.*

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
        "쇼핑" to "🛍️",
        "문화생활" to "🎬",
        "의료" to "💊",
        "OCR" to "🧾",
        "기타" to "📦"
    )

    private val categoryColors = mapOf(
        "식비" to "#FF8A65",      // 주황색
        "교통" to "#4DB6AC",      // 청록색
        "쇼핑" to "#9575CD",      // 보라색
        "문화생활" to "#F06292",  // 핑크색
        "의료" to "#BA68C8",      // 연보라색
        "OCR" to "#FFA726",       // 오렌지색
        "기타" to "#4FC3F7"       // 하늘색
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
        setupPieChart()
        loadCategoryData()
    }

    private fun setupPieChart() {
        pieChart.apply {
            setUsePercentValues(true)
            description.isEnabled = false
            setExtraOffsets(5f, 10f, 5f, 5f)

            dragDecelerationFrictionCoef = 0.95f
            isDrawHoleEnabled = true
            setHoleColor(Color.WHITE)

            setTransparentCircleColor(Color.WHITE)
            setTransparentCircleAlpha(110)

            holeRadius = 35f
            transparentCircleRadius = 40f

            setDrawCenterText(true)
            centerText = "지출 분석"
            setCenterTextSize(16f)
            setCenterTextColor(Color.GRAY)

            setRotationAngle(0f)
            isRotationEnabled = true
            isHighlightPerTapEnabled = true

            legend.isEnabled = false  // 범례 비활성화 (커스텀으로 구현)
        }
    }

    private fun loadCategoryData() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Log.w("AnalysisFragment", "사용자가 로그인되어 있지 않습니다")
            showError("로그인이 필요합니다.")
            return
        }

        Log.d("AnalysisFragment", "지출 데이터 로드 시작: ${currentUser.uid}")

        db.collection("users").document(currentUser.uid).collection("spending")
            .get()
            .addOnSuccessListener { result ->
                Log.d("AnalysisFragment", "Firestore 조회 성공: ${result.size()}개 문서")

                val categoryMap = mutableMapOf<String, Int>()
                val ocrDetails = mutableListOf<Map<String, Any>>()
                var totalAmount = 0

                for (doc in result) {
                    val category = doc.getString("category") ?: "기타"
                    val amount = (doc.getLong("amount") ?: 0).toInt()
                    val date = doc.getTimestamp("date")

                    Log.d("AnalysisFragment", "문서: ${doc.id}, 카테고리: $category, 금액: $amount")

                    categoryMap[category] = categoryMap.getOrDefault(category, 0) + amount
                    totalAmount += amount

                    // OCR 상세 정보 수집
                    if (category == "OCR") {
                        val ocrDetail = doc.get("ocrDetails") as? Map<String, Any>
                        val detailMap = mutableMapOf<String, Any>(
                            "amount" to amount,
                            "memo" to (doc.getString("memo") ?: ""),
                            "docId" to doc.id
                        )

                        // date 처리 - Firebase Timestamp 생성자 사용
                        val currentTimestamp = date ?: Timestamp(Date())
                        detailMap["date"] = currentTimestamp

                        if (ocrDetail != null) {
                            detailMap.putAll(ocrDetail)
                        }

                        ocrDetails.add(detailMap)
                        Log.d("AnalysisFragment", "OCR 상세 정보 추가: $detailMap")
                    }
                }

                Log.d("AnalysisFragment", "카테고리별 집계: $categoryMap")
                Log.d("AnalysisFragment", "총 지출: ${totalAmount}원")
                Log.d("AnalysisFragment", "OCR 상세 정보: ${ocrDetails.size}개")

                if (totalAmount > 0) {
                    updatePieChart(categoryMap, totalAmount)
                    showCategoryLegend(categoryMap, ocrDetails, totalAmount)
                    showSuccess("분석 완료! 총 ${categoryMap.size}개 카테고리의 지출을 분석했습니다.")
                } else {
                    showError("지출 데이터가 없습니다. 먼저 지출을 입력해주세요.")
                    clearChart()
                }
            }
            .addOnFailureListener { exception ->
                Log.e("AnalysisFragment", "Firestore 조회 실패", exception)
                showError("데이터 로드 실패: ${exception.message}")
                clearChart()
            }
    }

    private fun updatePieChart(categoryMap: Map<String, Int>, totalAmount: Int) {
        val entries = mutableListOf<PieEntry>()
        val colors = mutableListOf<Int>()

        // 카테고리별 데이터를 리스트로 변환 후 정렬
        val categoryList = categoryMap.map { entry ->
            Pair(entry.key, entry.value)
        }.sortedByDescending { pair ->
            pair.second
        }

        for (categoryPair in categoryList) {
            val category = categoryPair.first
            val amount = categoryPair.second
            val percentage = (amount.toFloat() / totalAmount.toFloat()) * 100f
            entries.add(PieEntry(percentage, category))

            val colorHex = categoryColors[category] ?: "#9E9E9E"
            colors.add(Color.parseColor(colorHex))
        }

        val dataSet = PieDataSet(entries, "").apply {
            this.colors = colors
            valueTextSize = 12f
            valueTextColor = Color.WHITE
            valueFormatter = PercentFormatter(pieChart)

            // 슬라이스 간격
            sliceSpace = 2f
            selectionShift = 5f
        }

        val pieData = PieData(dataSet)
        pieChart.data = pieData
        pieChart.invalidate()

        // 중앙 텍스트 업데이트
        pieChart.centerText = "총 지출\n${String.format("%,d", totalAmount)}원"
    }

    private fun showCategoryLegend(
        categoryMap: Map<String, Int>,
        ocrDetails: List<Map<String, Any>>,
        totalAmount: Int
    ) {
        binding.categorySummaryLayout.removeAllViews()

        // 카테고리별 요약을 리스트로 변환 후 정렬
        val categoryList = categoryMap.map { entry ->
            Pair(entry.key, entry.value)
        }.sortedByDescending { pair ->
            pair.second
        }

        for (categoryPair in categoryList) {
            val category = categoryPair.first
            val amount = categoryPair.second
            val icon = categoryIcons[category] ?: "❔"
            val colorHex = categoryColors[category] ?: "#9E9E9E"
            val percentage = (amount.toFloat() / totalAmount.toFloat()) * 100f

            val categoryHeader = TextView(requireContext()).apply {
                text = "$icon $category: ${String.format("%,d", amount)}원 (${String.format("%.1f", percentage)}%)"
                setTextColor(Color.parseColor(colorHex))
                textSize = 18f
                setPadding(16, 16, 16, 8)
                setTypeface(null, android.graphics.Typeface.BOLD)
            }
            binding.categorySummaryLayout.addView(categoryHeader)

            // OCR 카테고리인 경우 상세 정보 표시
            if (category == "OCR" && ocrDetails.isNotEmpty()) {
                for ((index, detail) in ocrDetails.withIndex()) {
                    val items = detail["items"] as? List<Map<String, Any>>
                    val receiptTotal = (detail["receiptTotal"] as? Number)?.toLong() ?: 0L
                    val itemsTotal = (detail["itemsTotal"] as? Number)?.toLong() ?: 0L
                    val detailAmount = (detail["amount"] as? Number)?.toLong() ?: 0L
                    val date = detail["date"] as? com.google.firebase.Timestamp
                    val memo = detail["memo"] as? String ?: ""

                    val dateStr = date?.let {
                        SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()).format(it.toDate())
                    } ?: ""

                    val detailText = TextView(requireContext()).apply {
                        val itemsText = items?.take(3)?.joinToString(", ") { item ->
                            val name = item["name"] as? String ?: ""
                            val price = (item["price"] as? Number)?.toLong() ?: 0L
                            "$name(${String.format("%,d", price)}원)"
                        } ?: ""

                        text = buildString {
                            append("📋 영수증 #${index + 1}")
                            if (dateStr.isNotEmpty()) append(" - $dateStr")
                            append("\n")

                            if (itemsText.isNotEmpty()) {
                                append("🍽️ 품목: $itemsText")
                                val itemCount = items?.size ?: 0
                                if (itemCount > 3) {
                                    append(" 외 ${itemCount - 3}개")
                                }
                                append("\n")
                            }

                            if (memo.isNotEmpty() && memo != "OCR 인식 항목") {
                                append("📝 메모: $memo\n")
                            }

                            append("💰 품목합계: ${String.format("%,d", itemsTotal)}원\n")
                            append("🧾 영수증총액: ${String.format("%,d", receiptTotal)}원\n")
                            append("💾 저장금액: ${String.format("%,d", detailAmount)}원")
                        }

                        setTextColor(Color.parseColor("#666666"))
                        textSize = 14f
                        setPadding(32, 8, 16, 16)
                        setLineSpacing(4f, 1.0f)

                        // 배경색 추가
                        setBackgroundColor(Color.parseColor("#F8F9FA"))

                        val layoutParams = ViewGroup.MarginLayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        ).apply {
                            setMargins(16, 8, 16, 8)
                        }
                        this.layoutParams = layoutParams
                    }
                    binding.categorySummaryLayout.addView(detailText)
                }
            }
        }
    }

    private fun clearChart() {
        pieChart.clear()
        binding.categorySummaryLayout.removeAllViews()
    }

    private fun showError(message: String) {
        // 에러 메시지를 표시하는 TextView 추가
        val errorText = TextView(requireContext()).apply {
            text = "⚠️ $message"
            setTextColor(Color.parseColor("#DC3545"))
            textSize = 16f
            setPadding(16, 16, 16, 16)
            setBackgroundColor(Color.parseColor("#F8D7DA"))
        }
        binding.categorySummaryLayout.removeAllViews()
        binding.categorySummaryLayout.addView(errorText)
    }

    private fun showSuccess(message: String) {
        Log.d("AnalysisFragment", "성공: $message")
        // 필요하면 성공 메시지 UI 추가
    }

    override fun onResume() {
        super.onResume()
        // Fragment가 다시 보일 때마다 데이터 새로고침
        loadCategoryData()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}