package com.example.myapplication

import android.Manifest
import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.*
import android.widget.*
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class AssetFragment : Fragment() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    // 예산 UI/상태
    private lateinit var budgetAmountText: TextView
    private lateinit var budgetProgressBar: ProgressBar
    private lateinit var budgetProgressLabel: TextView
    private var currentBudget: Int? = null

    // 주차 카드 컨테이너
    private lateinit var weeklyStatsContainer: LinearLayout

    private val monthKey: String
        get() = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())

    // 알림 채널 상수
    private val NOTI_CHANNEL_ID = "budget_alerts"
    private val NOTI_CHANNEL_NAME = "Budget Alerts"
    private val NOTI_ID_BUDGET_OVER = 2001
    private val REQ_NOTI_PERMISSION = 9911

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return createAssetLayout()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        createNotificationChannelIfNeeded()
        requestPostNotificationPermissionIfNeeded()

        loadBudget()
        loadAssetData() // 예산/주차 카드와 연동됨
    }

    // ----------------------- 레이아웃 -----------------------

    private fun createAssetLayout(): View {
        val context = requireContext()
        val scrollView = androidx.core.widget.NestedScrollView(context)

        val mainLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }

        // 헤더
        mainLayout.addView(createHeaderCard())

        // 📌 이번 달 예산 카드 + 진행률바
        mainLayout.addView(createBudgetCard())

        // 📊 주차별 예산 분배 카드
        mainLayout.addView(createWeeklyStatsCard())

        // 💳 자산별 지출 카드
        mainLayout.addView(createAssetStatsCard())

        scrollView.addView(mainLayout)
        return scrollView
    }

    private fun createHeaderCard(): View {
        val context = requireContext()
        val cardView = androidx.cardview.widget.CardView(context).apply {
            layoutParams = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 16) }
            radius = 16f
            cardElevation = 4f
        }

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 20)
            setBackgroundResource(R.drawable.gradient_card)
        }

        val titleText = TextView(context).apply {
            text = "💼 자산 관리"
            textSize = 20f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.WHITE)
        }

        val subtitleText = TextView(context).apply {
            text = "예산/주차별/결제수단별 지출을 한눈에!"
            textSize = 14f
            setTextColor(Color.parseColor("#CCFFFFFF"))
            setPadding(0, 4, 0, 0)
        }

        layout.addView(titleText)
        layout.addView(subtitleText)
        cardView.addView(layout)
        return cardView
    }

    // --------- 📌 예산 카드 (+ 진행률바) ---------
    private fun createBudgetCard(): View {
        val context = requireContext()
        val cardView = androidx.cardview.widget.CardView(context).apply {
            layoutParams = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 16) }
            radius = 16f
            cardElevation = 3f
        }

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 20)
        }

        val titleText = TextView(context).apply {
            text = "📌 이번 달 예산"
            textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
        }

        budgetAmountText = TextView(context).apply {
            text = "설정된 예산 없음"
            textSize = 16f
            setTextColor(Color.parseColor("#757575"))
            setPadding(0, 8, 0, 12)
        }

        // 진행률 바
        budgetProgressBar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = false
            max = 1000 // 100.0%를 위해 1000 스케일 사용
            progress = 0
        }
        budgetProgressLabel = TextView(context).apply {
            text = "0% (0원 / 0원)"
            textSize = 12f
            setTextColor(Color.parseColor("#616161"))
            setPadding(0, 4, 0, 0)
        }

        val setBudgetBtn = Button(context).apply {
            text = "예산 설정/수정"
            setOnClickListener { showBudgetDialog() }
        }

        layout.addView(titleText)
        layout.addView(budgetAmountText)
        layout.addView(budgetProgressBar)
        layout.addView(budgetProgressLabel)
        layout.addView(setBudgetBtn)
        cardView.addView(layout)
        return cardView
    }

    private fun showBudgetDialog() {
        val context = requireContext()
        val editText = EditText(context).apply {
            hint = "예산 금액 입력 (원)"
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(currentBudget?.toString() ?: "")
        }

        AlertDialog.Builder(context)
            .setTitle("${monthKey} 예산 설정")
            .setView(editText)
            .setPositiveButton("저장") { _, _ ->
                val amount = editText.text.toString().filter { it.isDigit() }.toIntOrNull()
                if (amount != null && amount >= 0) saveBudget(amount)
                else Toast.makeText(context, "올바른 금액을 입력하세요", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun saveBudget(amount: Int) {
        val currentUser = auth.currentUser ?: return
        val data = mapOf("month" to monthKey, "amount" to amount)

        db.collection("users")
            .document(currentUser.uid)
            .collection("settings")
            .document("budget_$monthKey")
            .set(data)
            .addOnSuccessListener {
                currentBudget = amount
                val nf = NumberFormat.getInstance(Locale.KOREA)
                budgetAmountText.setTextColor(Color.parseColor("#1976D2"))
                budgetAmountText.text = "설정된 예산: ${nf.format(amount)}원"
                // 집계/진행률/주차 갱신
                loadAssetData()
                Toast.makeText(requireContext(), "예산이 저장되었습니다", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "예산 저장 실패: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadBudget() {
        val currentUser = auth.currentUser ?: return

        lifecycleScope.launch {
            try {
                val snap = db.collection("users")
                    .document(currentUser.uid)
                    .collection("settings")
                    .document("budget_$monthKey")
                    .get()
                    .await()

                val nf = NumberFormat.getInstance(Locale.KOREA)
                if (snap.exists()) {
                    currentBudget = snap.getLong("amount")?.toInt()
                    val amountText = nf.format(currentBudget ?: 0)
                    budgetAmountText.setTextColor(Color.parseColor("#1976D2"))
                    budgetAmountText.text = "설정된 예산: ${amountText}원"
                } else {
                    currentBudget = null
                    budgetAmountText.setTextColor(Color.parseColor("#757575"))
                    budgetAmountText.text = "설정된 예산 없음"
                }
            } catch (_: Exception) {
                // 무시/로그
            }
        }
    }

    // --------- 📊 주차별 예산 분배 카드 ---------
    private fun createWeeklyStatsCard(): View {
        val context = requireContext()
        val cardView = androidx.cardview.widget.CardView(context).apply {
            layoutParams = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 16) }
            radius = 16f
            cardElevation = 3f
        }

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 20)
        }

        val titleText = TextView(context).apply {
            text = "📊 주차별 지출"
            textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 8)
        }

        val tip = TextView(context).apply {
            text = "한 달 예산을 4~5주로 나누어 비교해요"
            textSize = 12f
            setTextColor(Color.parseColor("#757575"))
            setPadding(0, 0, 0, 8)
        }

        weeklyStatsContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            tag = "weekly_stats_container"
        }

        layout.addView(titleText)
        layout.addView(tip)
        layout.addView(weeklyStatsContainer)
        cardView.addView(layout)
        return cardView
    }

    private fun addWeeklyRow(weekLabel: String, spent: Int, quota: Int) {
        val context = requireContext()
        val nf = NumberFormat.getInstance(Locale.KOREA)

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 6, 0, 6)
        }

        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val label = TextView(context).apply {
            text = weekLabel
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val value = TextView(context).apply {
            val quotaText = if (quota > 0) " / ${nf.format(quota)}원" else ""
            text = "${nf.format(spent)}원$quotaText"
            textSize = 14f
        }
        header.addView(label); header.addView(value)

        val barBg = FrameLayout(context).apply {
            setBackgroundColor(Color.parseColor("#E0E0E0"))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 14).apply {
                topMargin = 6
            }
        }
        val ratio = if (quota > 0) (spent.toFloat() / quota).coerceAtMost(1f) else 0f
        val barFg = View(context).apply {
            setBackgroundColor(if (spent > quota && quota > 0) Color.parseColor("#D32F2F") else Color.parseColor("#1976D2"))
        }
        barBg.addView(barFg, FrameLayout.LayoutParams(
            (resources.displayMetrics.widthPixels * 0.6f * ratio).toInt(),
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        row.addView(header)
        row.addView(barBg)
        weeklyStatsContainer.addView(row)
    }

    // --------- 💳 자산별 지출 카드 ---------
    private fun createAssetStatsCard(): View {
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
            id = View.generateViewId()
            tag = "asset_stats_layout"
        }

        val titleText = TextView(context).apply {
            text = "결제 수단별 지출 (이번 달)"
            textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 16)
        }

        layout.addView(titleText)
        cardView.addView(layout)
        return cardView
    }

    // ----------------------- 데이터 로드/업데이트 -----------------------

    private fun loadAssetData() {
        val currentUser = auth.currentUser ?: return

        lifecycleScope.launch {
            try {
                // 이번 달 범위
                val calStart = Calendar.getInstance().apply {
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val calEnd = Calendar.getInstance().apply {
                    set(Calendar.DAY_OF_MONTH, getActualMaxDayOfMonth())
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                    set(Calendar.MILLISECOND, 999)
                }

                val spendingSnapshot = db.collection("users")
                    .document(currentUser.uid)
                    .collection("spending")
                    .whereGreaterThanOrEqualTo("date", com.google.firebase.Timestamp(calStart.time))
                    .whereLessThanOrEqualTo("date", com.google.firebase.Timestamp(calEnd.time))
                    .get()
                    .await()

                // 자산별 합계
                val assetMap = mutableMapOf<String, Int>()
                var totalAmount = 0

                // 주차별 합계 (weekOfMonth: 1..6)
                val weeklyMap = IntArray(6) { 0 }

                for (document in spendingSnapshot.documents) {
                    val amount = (document.getLong("amount") ?: 0).toInt()
                    val asset = document.getString("asset") ?: "기타"
                    val ts = document.getTimestamp("date")?.toDate()
                    totalAmount += amount
                    assetMap[asset] = assetMap.getOrDefault(asset, 0) + amount

                    ts?.let {
                        val c = Calendar.getInstance().apply { time = it }
                        val w = c.get(Calendar.WEEK_OF_MONTH) // 1..6
                        if (w in 1..6) weeklyMap[w - 1] += amount
                    }
                }

                // UI 업데이트
                updateAssetUI(assetMap, totalAmount)
                updateBudgetProgress(totalAmount)
                updateWeeklyUI(weeklyMap, totalAmount)

                // 예산 초과 시 알림
                maybeNotifyBudgetOver(totalAmount)

            } catch (_: Exception) {
                // 필요시 로그/토스트
            }
        }
    }

    private fun updateBudgetProgress(totalAmount: Int) {
        val nf = NumberFormat.getInstance(Locale.KOREA)
        val budget = currentBudget ?: 0
        if (budget <= 0) {
            budgetProgressBar.progress = 0
            budgetProgressLabel.text = "0% (${nf.format(totalAmount)}원 / 0원)"
            return
        }
        val ratio = (totalAmount.toDouble() / budget).coerceAtLeast(0.0)
        val clamped = ratio.coerceAtMost(1.0)
        budgetProgressBar.progress = (clamped * 1000).toInt()
        budgetProgressLabel.text = "${String.format(Locale.getDefault(), "%.1f", ratio * 100)}%  (${nf.format(totalAmount)}원 / ${nf.format(budget)}원)"
    }

    private fun updateWeeklyUI(weeklyMap: IntArray, totalAmount: Int) {
        if (!::weeklyStatsContainer.isInitialized) return
        weeklyStatsContainer.removeAllViews()

        val weeksInMonth = estimateWeeksInCurrentMonth()
        val budget = currentBudget ?: 0
        // 예산 주차 분배(균등): 남는 건 마지막 주에 더해도 되지만, 단순화를 위해 균등 나눔
        val weeklyQuota = if (budget > 0 && weeksInMonth > 0) budget / weeksInMonth else 0

        for (i in 0 until weeksInMonth) {
            val spent = weeklyMap[i]
            val label = "${i + 1}주차"
            addWeeklyRow(label, spent, weeklyQuota)
        }

        // 주차가 5주 이상인데 데이터가 없는 주는 0원으로 표시
        if (weeksInMonth < weeklyMap.size) {
            for (i in weeksInMonth until weeklyMap.size) {
                if (weeklyMap[i] > 0) {
                    // 예외적으로 6주차가 있는 달 처리(드문 케이스)
                    addWeeklyRow("${i + 1}주차", weeklyMap[i], weeklyQuota)
                }
            }
        }
    }

    private fun estimateWeeksInCurrentMonth(): Int {
        val c = Calendar.getInstance()
        c.set(Calendar.DAY_OF_MONTH, 1)
        val firstDayOfWeek = c.get(Calendar.DAY_OF_WEEK) // 1=일..7=토
        val daysInMonth = c.getActualMaximum(Calendar.DAY_OF_MONTH)
        val before = (firstDayOfWeek - Calendar.SUNDAY + 7) % 7
        val totalCells = before + daysInMonth
        return Math.ceil(totalCells / 7.0).toInt() // 4~6주
    }

    private fun updateAssetUI(assetMap: Map<String, Int>, totalAmount: Int) {
        val rootView = view ?: return
        val assetLayout = rootView.findViewWithTag<LinearLayout>("asset_stats_layout") ?: return

        // 기존 아이템들 제거 (타이틀 제외)
        if (assetLayout.childCount > 1) {
            assetLayout.removeViews(1, assetLayout.childCount - 1)
        }

        val numberFormat = NumberFormat.getInstance(Locale.KOREA)
        val sortedAssets = assetMap.toList().sortedByDescending { it.second }

        for ((asset, amount) in sortedAssets) {
            val percentage = if (totalAmount > 0) (amount.toFloat() / totalAmount * 100) else 0f
            val item = createAssetItem(asset, amount, percentage, numberFormat)
            assetLayout.addView(item)
        }

        // 총합 표시
        val totalItemView = createTotalItem(totalAmount, numberFormat)
        assetLayout.addView(totalItemView)

        // 예산 문자열도 최신화(초과/여유 문구)
        currentBudget?.let { budget ->
            if (budget > 0) {
                when {
                    totalAmount > budget -> {
                        budgetAmountText.setTextColor(Color.parseColor("#D32F2F"))
                        budgetAmountText.text =
                            "설정된 예산: ${numberFormat.format(budget)}원 (⚠ 초과: ${numberFormat.format(totalAmount - budget)}원)"
                    }
                    totalAmount == budget -> {
                        budgetAmountText.setTextColor(Color.parseColor("#FB8C00"))
                        budgetAmountText.text =
                            "설정된 예산: ${numberFormat.format(budget)}원 (딱 맞음! 🎯)"
                    }
                    else -> {
                        val remain = budget - totalAmount
                        budgetAmountText.setTextColor(Color.parseColor("#1976D2"))
                        budgetAmountText.text =
                            "설정된 예산: ${numberFormat.format(budget)}원 (남음: ${numberFormat.format(remain)}원)"
                    }
                }
            }
        }
    }

    private fun createAssetItem(asset: String, amount: Int, percentage: Float, nf: NumberFormat): View {
        val context = requireContext()
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 8, 0, 8)
        }

        val assetIcon = getAssetIcon(asset)

        val assetText = TextView(context).apply {
            text = "$assetIcon $asset"
            textSize = 16f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val amountText = TextView(context).apply {
            text = "${nf.format(amount)}원 (${String.format(Locale.getDefault(), "%.1f", percentage)}%)"
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.parseColor("#D32F2F"))
        }

        layout.addView(assetText)
        layout.addView(amountText)
        return layout
    }

    private fun createTotalItem(totalAmount: Int, nf: NumberFormat): View {
        val context = requireContext()
        val divider = View(context).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 2)
            setBackgroundColor(Color.parseColor("#E0E0E0"))
        }

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 16, 0, 0)
        }

        val totalLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 8, 0, 0)
        }

        val totalText = TextView(context).apply {
            text = "💰 총 지출(이번 달)"
            textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val totalAmountText = TextView(context).apply {
            text = "${nf.format(totalAmount)}원"
            textSize = 20f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.parseColor("#1976D2"))
        }

        totalLayout.addView(totalText)
        totalLayout.addView(totalAmountText)

        layout.addView(divider)
        layout.addView(totalLayout)
        return layout
    }

    // ----------------------- 알림 -----------------------

    private fun createNotificationChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = requireContext().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                NOTI_CHANNEL_ID,
                NOTI_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Budget exceed notifications"
            }
            nm.createNotificationChannel(channel)
        }
    }

    private fun requestPostNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                ActivityCompat.requestPermissions(requireActivity(), arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTI_PERMISSION)
            }
        }
    }

    private fun maybeNotifyBudgetOver(totalAmount: Int) {
        val budget = currentBudget ?: return
        if (budget <= 0) return
        if (totalAmount <= budget) return

        // 알림 권한 체크(13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val over = totalAmount - budget
        val nf = NumberFormat.getInstance(Locale.KOREA)

        val nm = requireContext().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(requireContext(), NOTI_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_budget) // ← 작은 아이콘 준비 필요(아래 참고)
            .setContentTitle("예산 초과 알림")
            .setContentText("이번 달 지출이 예산을 초과했어요: ${nf.format(over)}원 초과")
            .setStyle(NotificationCompat.BigTextStyle().bigText("총 지출: ${nf.format(totalAmount)}원 / 예산: ${nf.format(budget)}원\n지출을 점검해보세요."))
            .setAutoCancel(true)
            .build()

        nm.notify(NOTI_ID_BUDGET_OVER, notification)
    }

    // ----------------------- 유틸 -----------------------

    private fun getAssetIcon(asset: String): String {
        return when (asset) {
            "현금" -> "💵"
            "체크카드" -> "💳"
            "신용카드" -> "💎"
            "카카오페이" -> "💛"
            "토스" -> "💙"
            "OCR 인식" -> "🤖"
            else -> "💰"
        }
    }

    private fun getActualMaxDayOfMonth(): Int {
        val c = Calendar.getInstance()
        return c.getActualMaximum(Calendar.DAY_OF_MONTH)
    }
}
