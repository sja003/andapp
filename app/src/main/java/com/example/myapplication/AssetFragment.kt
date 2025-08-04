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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.NumberFormat
import java.util.*

class AssetFragment : Fragment() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return createAssetLayout()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadAssetData()
    }

    private fun createAssetLayout(): View {
        val context = requireContext()
        val scrollView = androidx.core.widget.NestedScrollView(context)

        val mainLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }

        // 헤더
        val headerCard = createHeaderCard()
        mainLayout.addView(headerCard)

        // 자산별 지출 카드
        val assetStatsCard = createAssetStatsCard()
        mainLayout.addView(assetStatsCard)

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
            setBackgroundResource(R.drawable.gradient_card)
        }

        val titleText = TextView(context).apply {
            text = "💼 자산 관리"
            textSize = 20f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.WHITE)
        }

        val subtitleText = TextView(context).apply {
            text = "결제 수단별 지출 현황을 확인하세요"
            textSize = 14f
            setTextColor(Color.parseColor("#CCFFFFFF"))
            setPadding(0, 4, 0, 0)
        }

        layout.addView(titleText)
        layout.addView(subtitleText)
        cardView.addView(layout)
        return cardView
    }

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
            text = "결제 수단별 지출"
            textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 16)
        }

        layout.addView(titleText)
        cardView.addView(layout)
        return cardView
    }

    private fun loadAssetData() {
        val currentUser = auth.currentUser ?: return

        lifecycleScope.launch {
            try {
                val spendingSnapshot = db.collection("users")
                    .document(currentUser.uid)
                    .collection("spending")
                    .get()
                    .await()

                val assetMap = mutableMapOf<String, Int>()
                var totalAmount = 0

                for (document in spendingSnapshot.documents) {
                    val amount = (document.getLong("amount") ?: 0).toInt()
                    val asset = document.getString("asset") ?: "기타"

                    assetMap[asset] = assetMap.getOrDefault(asset, 0) + amount
                    totalAmount += amount
                }

                updateAssetUI(assetMap, totalAmount)

            } catch (e: Exception) {
                // 에러 처리
            }
        }
    }

    private fun updateAssetUI(assetMap: Map<String, Int>, totalAmount: Int) {
        val rootView = view ?: return
        val assetLayout = rootView.findViewWithTag<LinearLayout>("asset_stats_layout")

        // 기존 아이템들 제거 (타이틀 제외)
        if (assetLayout.childCount > 1) {
            assetLayout.removeViews(1, assetLayout.childCount - 1)
        }

        val numberFormat = NumberFormat.getInstance(Locale.KOREA)
        val sortedAssets = assetMap.toList().sortedByDescending { it.second }

        for ((asset, amount) in sortedAssets) {
            val percentage = if (totalAmount > 0) (amount.toFloat() / totalAmount * 100) else 0f
            val assetItemView = createAssetItem(asset, amount, percentage, numberFormat)
            assetLayout.addView(assetItemView)
        }

        // 총합 표시
        val totalItemView = createTotalItem(totalAmount, numberFormat)
        assetLayout.addView(totalItemView)
    }

    private fun createAssetItem(asset: String, amount: Int, percentage: Float, numberFormat: NumberFormat): View {
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
            text = "${numberFormat.format(amount)}원 (${String.format("%.1f", percentage)}%)"
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.parseColor("#D32F2F"))
        }

        layout.addView(assetText)
        layout.addView(amountText)
        return layout
    }

    private fun createTotalItem(totalAmount: Int, numberFormat: NumberFormat): View {
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
            text = "💰 총 지출"
            textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val totalAmountText = TextView(context).apply {
            text = "${numberFormat.format(totalAmount)}원"
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
}