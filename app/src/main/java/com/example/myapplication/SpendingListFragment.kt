package com.example.myapplication

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class SpendingListFragment : Fragment() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private lateinit var recyclerView: RecyclerView
    private lateinit var spendingAdapter: SpendingListAdapter
    private val spendingList = mutableListOf<SpendingItem>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return createSpendingListLayout()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        loadAllSpending()
    }

    private fun createSpendingListLayout(): View {
        val context = requireContext()
        val scrollView = androidx.core.widget.NestedScrollView(context)

        val mainLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }

        // 헤더 카드
        val headerCard = createHeaderCard()
        mainLayout.addView(headerCard)

        // 리스트 카드
        val listCard = createListCard()
        mainLayout.addView(listCard)

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
            text = "📋 전체 내역"
            textSize = 20f
            setTypeface(null, android.graphics.Typeface.BOLD)
        }

        val subtitleText = TextView(context).apply {
            text = "모든 지출 내역을 확인하고 관리하세요"
            textSize = 14f
            setPadding(0, 4, 0, 0)
        }

        layout.addView(titleText)
        layout.addView(subtitleText)
        cardView.addView(layout)
        return cardView
    }

    private fun createListCard(): View {
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
            text = "지출 목록"
            textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 16)
        }

        recyclerView = RecyclerView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                600.dpToPx()
            )
        }

        layout.addView(titleText)
        layout.addView(recyclerView)
        cardView.addView(layout)
        return cardView
    }

    private fun setupRecyclerView() {
        spendingAdapter = SpendingListAdapter(spendingList) { spendingItem ->
            showEditDeleteDialog(spendingItem)
        }

        recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = spendingAdapter
        }
    }

    private fun loadAllSpending() {
        val currentUser = auth.currentUser ?: return

        db.collection("users")
            .document(currentUser.uid)
            .collection("spending")
            .orderBy("date", Query.Direction.DESCENDING)
            .limit(50) // 최신 50개만 로드
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Toast.makeText(requireContext(), "데이터 로드 실패: ${error.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                spendingList.clear()
                snapshot?.documents?.forEach { document ->
                    val item = SpendingItem(
                        id = document.id,
                        amount = document.getLong("amount")?.toInt() ?: 0,
                        category = document.getString("category") ?: "",
                        asset = document.getString("asset") ?: "",
                        memo = document.getString("memo") ?: "",
                        date = document.getTimestamp("date"),
                        isOcrGenerated = document.getString("asset") == "OCR 인식",
                        ocrDetails = document.get("ocrDetails") as? Map<String, Any>
                    )
                    spendingList.add(item)
                }
                spendingAdapter.notifyDataSetChanged()
            }
    }

    private fun showEditDeleteDialog(spendingItem: SpendingItem) {
        // DailyFragment와 동일한 구현 사용
        val options = arrayOf("✏️ 수정하기", "🗑️ 삭제하기")

        android.app.AlertDialog.Builder(requireContext())
            .setTitle("${spendingItem.category} • ${String.format("%,d", spendingItem.amount)}원")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showEditDialog(spendingItem)
                    1 -> showDeleteConfirmDialog(spendingItem)
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    // 편집/삭제 메서드들은 DailyFragment와 동일하므로 생략...
    private fun showEditDialog(spendingItem: SpendingItem) {
        // DailyFragment의 구현과 동일
    }

    private fun showDeleteConfirmDialog(spendingItem: SpendingItem) {
        // DailyFragment의 구현과 동일
    }

    private fun Int.dpToPx(): Int {
        val density = resources.displayMetrics.density
        return (this * density).toInt()
    }
}