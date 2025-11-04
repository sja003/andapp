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
import kotlin.math.abs

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
                val rawDocs = mutableListOf<Map<String, Any?>>()
                var totalAmount = 0

                for (doc in result) {
                    val category = doc.getString("category") ?: "기타"
                    val amount = (doc.getLong("amount") ?: 0).toInt()
                    val date = doc.getTimestamp("date")
                    val memo = doc.getString("memo")
                    val merchant = doc.getString("merchant")

                    Log.d("AnalysisFragment", "문서: ${doc.id}, 카테고리: $category, 금액: $amount")

                    categoryMap[category] = categoryMap.getOrDefault(category, 0) + amount
                    totalAmount += amount

                    // 원본 필드 저장(추세/반복지출 추정용)
                    rawDocs += mapOf(
                        "docId" to doc.id,
                        "category" to category,
                        "amount" to amount,
                        "date" to (date ?: Timestamp(Date())),
                        "memo" to memo,
                        "merchant" to merchant
                    )

                    // OCR 상세 정보 수집
                    if (category == "OCR") {
                        val ocrDetail = doc.get("ocrDetails") as? Map<String, Any>
                        val detailMap = mutableMapOf<String, Any>(
                            "amount" to amount,
                            "memo" to (memo ?: ""),
                            "docId" to doc.id
                        )

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

                    // ✅ 오프라인 AI 소비 분석 계산/표시
                    val insights = computeInsights(categoryMap, totalAmount, rawDocs, ocrDetails)
                    val adviceLines = generateAdviceKorean(insights)
                    showAIAdvice(adviceLines)

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
                                if (itemCount > 3) append(" 외 ${itemCount - 3}개")
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

    // ─────────────────────────────────────────────
    // ▼▼ 여기부터 추가된 "오프라인 AI 소비 분석" 로직 ▼▼
    // ─────────────────────────────────────────────

    // 1) 소비 특징 데이터 모델
    data class SpendingInsights(
        val totalAmount: Int,
        val currentMonthAmount: Int,
        val prevMonthAmount: Int,
        val monthOverMonthRate: Double?,        // 전월 대비 증감률(%)
        val dailyAvgThisMonth: Double?,         // 이번달 일 평균 지출
        val topCategories: List<Pair<String, Int>>,
        val recurringGuesses: List<String>,     // 추정 반복 지출(메모/상호/품목명)
        val ocrAnomalies: List<String>          // OCR 합계 불일치 의심
    )

    // 2) 소비 특징 계산
    private fun computeInsights(
        categoryMap: Map<String, Int>,
        totalAmount: Int,
        rawDocs: List<Map<String, Any?>>,
        ocrDetails: List<Map<String, Any>>
    ): SpendingInsights {

        // 이번달/저번달 범위
        val now = Calendar.getInstance()
        val endThisMonth = now.time
        now.set(Calendar.DAY_OF_MONTH, 1)
        now.set(Calendar.HOUR_OF_DAY, 0)
        now.set(Calendar.MINUTE, 0)
        now.set(Calendar.SECOND, 0)
        now.set(Calendar.MILLISECOND, 0)
        val startThisMonth = now.time

        now.add(Calendar.MONTH, -1)
        val startPrevMonth = now.time
        now.set(Calendar.DAY_OF_MONTH, now.getActualMaximum(Calendar.DAY_OF_MONTH))
        now.set(Calendar.HOUR_OF_DAY, 23)
        now.set(Calendar.MINUTE, 59)
        now.set(Calendar.SECOND, 59)
        now.set(Calendar.MILLISECOND, 999)
        val endPrevMonth = now.time

        var thisMonthSum = 0
        var prevMonthSum = 0

        val repeatCounter = mutableMapOf<String, Int>()

        rawDocs.forEach { doc ->
            val amount = (doc["amount"] as? Number)?.toInt() ?: 0
            val ts = doc["date"] as? com.google.firebase.Timestamp
            val dt = ts?.toDate() ?: return@forEach

            if (dt >= startThisMonth && dt <= endThisMonth) {
                thisMonthSum += amount
            } else if (dt >= startPrevMonth && dt <= endPrevMonth) {
                prevMonthSum += amount
            }

            val memo = (doc["memo"] as? String)?.trim().orEmpty()
            val merchant = (doc["merchant"] as? String)?.trim().orEmpty()
            if (memo.isNotEmpty()) repeatCounter[memo] = repeatCounter.getOrDefault(memo, 0) + 1
            if (merchant.isNotEmpty()) repeatCounter[merchant] = repeatCounter.getOrDefault(merchant, 0) + 1
        }

        // OCR 품목명도 반복 후보에 추가
        ocrDetails.forEach { d ->
            val items = d["items"] as? List<Map<String, Any>>
            items?.forEach { item ->
                val name = (item["name"] as? String)?.trim().orEmpty()
                if (name.isNotEmpty()) {
                    repeatCounter[name] = repeatCounter.getOrDefault(name, 0) + 1
                }
            }
        }

        val recurring = repeatCounter
            .filter { it.value >= 2 }
            .toList()
            .sortedByDescending { it.second }
            .take(8)
            .map { it.first }

        // OCR 합계 불일치 의심(±5% 초과)
        val anomalies = mutableListOf<String>()
        ocrDetails.forEachIndexed { idx, d ->
            val receiptTotal = (d["receiptTotal"] as? Number)?.toDouble() ?: return@forEachIndexed
            val itemsTotal = (d["itemsTotal"] as? Number)?.toDouble() ?: return@forEachIndexed
            if (receiptTotal <= 0) return@forEachIndexed
            val diffRate = abs(receiptTotal - itemsTotal) / receiptTotal
            if (diffRate > 0.05) {
                anomalies += "영수증 #${idx + 1}: 항목합계(${String.format("%,d", itemsTotal.toLong())}) vs 영수증총액(${String.format("%,d", receiptTotal.toLong())}) 불일치(편차 ${(diffRate * 100).toInt()}%)"
            }
        }

        val top = categoryMap.toList().sortedByDescending { it.second }.take(3)

        val mom: Double? = if (prevMonthSum > 0) {
            ((thisMonthSum - prevMonthSum).toDouble() / prevMonthSum.toDouble()) * 100.0
        } else null

        val daysPassedThisMonth = Calendar.getInstance().get(Calendar.DAY_OF_MONTH).coerceAtLeast(1).toDouble()
        val dailyAvg = if (thisMonthSum > 0) thisMonthSum / daysPassedThisMonth else null

        return SpendingInsights(
            totalAmount = totalAmount,
            currentMonthAmount = thisMonthSum,
            prevMonthAmount = prevMonthSum,
            monthOverMonthRate = mom,
            dailyAvgThisMonth = dailyAvg,
            topCategories = top,
            recurringGuesses = recurring,
            ocrAnomalies = anomalies
        )
    }

    // 3) 한국어 조언 생성
    private fun generateAdviceKorean(ins: SpendingInsights, currencySuffix: String = "원"): List<String> {
        val tips = mutableListOf<String>()

        // 전월 대비
        ins.monthOverMonthRate?.let { r ->
            val dir = if (r >= 0) "증가" else "감소"
            tips += "이번달 지출은 전월 대비 ${String.format("%.1f", kotlin.math.abs(r))}% $dir 했어요."
            if (r >= 15) tips += "증가 폭이 커요. 이번달만의 이벤트성 지출(여행/행사/구독 갱신 등)을 점검해보세요."
        } ?: run {
            tips += "전월 데이터가 부족해 추세 비교는 생략했어요. 이번달부터 꾸준히 기록하면 추세 분석이 가능해요."
        }

        // 일 평균
        ins.dailyAvgThisMonth?.let { d ->
            tips += "이번달 일 평균 지출은 약 ${String.format("%,d", d.toInt())}$currencySuffix 입니다."
        }

        // 상위 카테고리
        if (ins.topCategories.isNotEmpty()) {
            val head = ins.topCategories.joinToString(" · ") {
                "${it.first}(${String.format("%,d", it.second)}$currencySuffix)"
            }
            tips += "지출 상위 카테고리 TOP: $head."
            ins.topCategories.firstOrNull()?.let {
                tips += "가장 큰 비중의 '${it.first}' 카테고리는 주 1회 지출 합계 상한을 정해보세요. 자동이체/간편결제 한도도 함께 설정하면 효과가 커요."
            }
        }

        // 반복 지출 추정
        if (ins.recurringGuesses.isNotEmpty()) {
            tips += "반복 지출로 보이는 항목: ${ins.recurringGuesses.take(5).joinToString(", ")} …"
            tips += "반복 지출은 구독/정기결제일 가능성이 있어요. 불필요한 항목은 구독 해지, 대체 서비스 탐색을 권장해요."
        } else {
            tips += "반복 지출로 보이는 항목이 아직 뚜렷하지 않아요. 메모에 매장명/용도를 좀 더 구체적으로 남기면 분석 정확도가 올라가요."
        }

        // OCR 이상 징후
        if (ins.ocrAnomalies.isNotEmpty()) {
            tips += "OCR 인식값과 영수증 합계가 어긋난 내역이 있어요:"
            ins.ocrAnomalies.take(3).forEach { tips += "· $it" }
            tips += "합계 불일치 항목은 수동 수정으로 금액을 한번 더 확인해주세요."
        }

        // 총평
        tips += "한 주에 1번, 상위 카테고리를 중심으로 '지출 리셋데이(무지출 또는 저지출)'를 지정하면 월 지출을 쉽게 줄일 수 있어요."

        return tips
    }

    // 4) “AI 소비 분석” 카드 표시 (기존 시각 요소는 그대로 유지)
    private fun showAIAdvice(adviceLines: List<String>) {
        val parent = binding.categorySummaryLayout

        // 기존 AI 카드 제거(새로 그림)
        for (i in parent.childCount - 1 downTo 0) {
            val v = parent.getChildAt(i)
            if (v.tag == "ai-advice-card") parent.removeViewAt(i)
        }

        val container = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#FFFFFF"))
            setPadding(24, 24, 24, 24)
            val lp = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(16, 8, 16, 24)
            layoutParams = lp
            // 기본 프레임(약간의 테두리 느낌)
            setBackgroundResource(android.R.drawable.dialog_holo_light_frame)
            tag = "ai-advice-card"
        }

        val title = TextView(requireContext()).apply {
            text = "🤖 AI 소비 분석"
            setTextColor(Color.parseColor("#212529"))
            textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        container.addView(title)

        val subtitle = TextView(requireContext()).apply {
            text = "최근 지출을 바탕으로 간단한 인사이트와 행동 제안을 만들어 드렸어요."
            setTextColor(Color.parseColor("#6C757D"))
            textSize = 14f
            setPadding(0, 6, 0, 12)
        }
        container.addView(subtitle)

        adviceLines.forEach { line ->
            val tv = TextView(requireContext()).apply {
                text = "• $line"
                setTextColor(Color.parseColor("#343A40"))
                textSize = 15f
                setLineSpacing(4f, 1.05f)
                setPadding(0, 6, 0, 6)
            }
            container.addView(tv)
        }

        val action = TextView(requireContext()).apply {
            text = "목표 만들기(이번달 한도 설정)"
            setTextColor(Color.WHITE)
            setPadding(16, 12, 16, 12)
            setBackgroundColor(Color.parseColor("#4DB6AC"))
            val lp = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(0, 12, 0, 0)
            layoutParams = lp
            setOnClickListener {
                showSuccess("예: 설정 화면으로 이동하여 월간 한도를 정하도록 유도하세요.")
            }
        }
        container.addView(action)

        parent.addView(container)
    }

    // ─────────────────────────────────────────────
    // ▲▲ 여기까지 추가된 "오프라인 AI 소비 분석" 로직 ▲▲
    // ─────────────────────────────────────────────

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
