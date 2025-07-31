package com.example.myapplication

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class SpendingListFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var spendingAdapter: SpendingListAdapter
    private val spendingList = mutableListOf<SpendingItem>()
    private val db = FirebaseFirestore.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return createListLayout()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        loadSpendingData()
    }

    private fun createListLayout(): View {
        val context = requireContext()

        val rootLayout = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setBackgroundColor(android.graphics.Color.parseColor("#F8F9FA"))
        }

        // 헤더
        val headerLayout = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            setPadding(48, 48, 48, 48)
            setBackgroundColor(android.graphics.Color.WHITE)
            gravity = android.view.Gravity.CENTER_VERTICAL
        }

        val titleText = android.widget.TextView(context).apply {
            text = "💰 전체 지출 내역"
            textSize = 20f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(android.graphics.Color.parseColor("#212121"))
            layoutParams = android.widget.LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
            )
        }

        val hintText = android.widget.TextView(context).apply {
            text = "✏️ 터치하여 수정/삭제"
            textSize = 12f
            setTextColor(android.graphics.Color.parseColor("#757575"))
        }

        headerLayout.addView(titleText)
        headerLayout.addView(hintText)

        // 구분선
        val divider = View(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 3
            )
            setBackgroundColor(android.graphics.Color.parseColor("#E0E0E0"))
        }

        // RecyclerView
        recyclerView = RecyclerView(context).apply {
            clipToPadding = false
            setPadding(0, 24, 0, 240)
        }

        rootLayout.addView(headerLayout)
        rootLayout.addView(divider)
        rootLayout.addView(recyclerView)

        return rootLayout
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

    private fun loadSpendingData() {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            Toast.makeText(requireContext(), "로그인이 필요합니다", Toast.LENGTH_SHORT).show()
            return
        }

        val uid = currentUser.uid

        db.collection("users").document(uid).collection("spending")
            .orderBy("date", Query.Direction.DESCENDING)
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
        val options = arrayOf("✏️ 수정하기", "🗑️ 삭제하기", "📊 상세정보")

        android.app.AlertDialog.Builder(requireContext())
            .setTitle("${spendingItem.category} • ${String.format("%,d", spendingItem.amount)}원")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showEditDialog(spendingItem)
                    1 -> showDeleteConfirmDialog(spendingItem)
                    2 -> showDetailDialog(spendingItem)
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun showDetailDialog(spendingItem: SpendingItem) {
        val message = buildString {
            append("📊 지출 상세 정보\n\n")
            append("💰 금액: ${String.format("%,d", spendingItem.amount)}원\n")
            append("📂 카테고리: ${spendingItem.category}\n")
            append("💳 결제수단: ${spendingItem.asset}\n")
            spendingItem.date?.let {
                append("📅 날짜: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(it.toDate())}\n")
            }
            if (spendingItem.memo.isNotEmpty()) {
                append("📝 메모: ${spendingItem.memo}\n")
            }

            if (spendingItem.isOcrGenerated) {
                append("\n🤖 OCR 생성 정보:\n")
                append("• 영수증 OCR로 자동 생성됨\n")
                spendingItem.ocrDetails?.let { details ->
                    val items = details["items"] as? List<Map<String, Any>>
                    if (!items.isNullOrEmpty()) {
                        append("• 인식된 메뉴: ${items.size}개\n")
                        items.take(3).forEach { item ->
                            val name = item["name"] as? String ?: ""
                            val price = item["price"] as? Number ?: 0
                            append("  - $name: ${String.format("%,d", price.toInt())}원\n")
                        }
                        if (items.size > 3) {
                            append("  - 외 ${items.size - 3}개 항목\n")
                        }
                    }
                }
            }
        }

        android.app.AlertDialog.Builder(requireContext())
            .setTitle("📊 상세 정보")
            .setMessage(message)
            .setPositiveButton("확인", null)
            .show()
    }

    // 수정/삭제 다이얼로그는 DailyFragment와 동일한 로직 사용
    private fun showEditDialog(spendingItem: SpendingItem) {
        val layout = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(50, 50, 50, 50)
        }

        // 카테고리 설정
        val categoryLabel = android.widget.TextView(requireContext()).apply {
            text = "카테고리"
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 20)
        }

        val categorySpinner = android.widget.Spinner(requireContext()).apply {
            val categories = listOf("식비", "카페", "교통", "쇼핑", "문화생활", "의료", "기타")
            val adapter = android.widget.ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, categories)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            this.adapter = adapter

            val currentIndex = categories.indexOf(spendingItem.category)
            if (currentIndex >= 0) {
                setSelection(currentIndex)
            }
            setPadding(0, 0, 0, 30)
        }

        // 금액 설정
        val amountLabel = android.widget.TextView(requireContext()).apply {
            text = "금액"
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 20)
        }

        val amountEditText = android.widget.EditText(requireContext()).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            hint = "금액을 입력하세요"
            setText(spendingItem.amount.toString())
            setPadding(0, 0, 0, 30)
        }

        // 자산 설정
        val assetLabel = android.widget.TextView(requireContext()).apply {
            text = "결제 수단"
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 20)
        }

        val assetSpinner = android.widget.Spinner(requireContext()).apply {
            val assets = if (spendingItem.isOcrGenerated) {
                listOf("OCR 인식", "현금", "체크카드", "신용카드", "카카오페이", "토스")
            } else {
                listOf("현금", "체크카드", "신용카드", "카카오페이", "토스", "OCR 인식")
            }
            val adapter = android.widget.ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, assets)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            this.adapter = adapter

            val currentIndex = assets.indexOf(spendingItem.asset)
            if (currentIndex >= 0) {
                setSelection(currentIndex)
            }
            setPadding(0, 0, 0, 30)
        }

        // 메모 설정
        val memoLabel = android.widget.TextView(requireContext()).apply {
            text = "메모"
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 20)
        }

        val memoEditText = android.widget.EditText(requireContext()).apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            hint = "메모를 입력하세요"
            maxLines = 3
            setText(spendingItem.memo)
        }

        // OCR 정보 표시
        if (spendingItem.isOcrGenerated) {
            val ocrInfoLabel = android.widget.TextView(requireContext()).apply {
                text = "🤖 OCR 인식 정보"
                textSize = 14f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(android.graphics.Color.parseColor("#1976D2"))
                setPadding(0, 30, 0, 10)
            }

            val ocrInfoText = android.widget.TextView(requireContext()).apply {
                text = "이 항목은 영수증 OCR로 자동 생성되었습니다."
                textSize = 12f
                setTextColor(android.graphics.Color.parseColor("#757575"))
                setPadding(0, 0, 0, 20)
            }

            layout.addView(ocrInfoLabel)
            layout.addView(ocrInfoText)
        }

        // 레이아웃에 뷰 추가
        layout.addView(categoryLabel)
        layout.addView(categorySpinner)
        layout.addView(amountLabel)
        layout.addView(amountEditText)
        layout.addView(assetLabel)
        layout.addView(assetSpinner)
        layout.addView(memoLabel)
        layout.addView(memoEditText)

        android.app.AlertDialog.Builder(requireContext())
            .setTitle("✏️ 지출 내역 수정")
            .setView(layout)
            .setPositiveButton("저장") { _, _ ->
                val newCategory = categorySpinner.selectedItem.toString()
                val newAmount = amountEditText.text.toString().toIntOrNull() ?: spendingItem.amount
                val newAsset = assetSpinner.selectedItem.toString()
                val newMemo = memoEditText.text.toString()

                updateSpendingItem(spendingItem.id, newCategory, newAmount, newAsset, newMemo)
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun showDeleteConfirmDialog(spendingItem: SpendingItem) {
        val message = buildString {
            append("정말로 이 지출 내역을 삭제하시겠습니까?\n\n")
            append("💰 금액: ${String.format("%,d", spendingItem.amount)}원\n")
            append("📂 카테고리: ${spendingItem.category}\n")
            append("💳 결제수단: ${spendingItem.asset}\n")
            if (spendingItem.memo.isNotEmpty()) {
                append("📝 메모: ${spendingItem.memo}\n")
            }
            if (spendingItem.isOcrGenerated) {
                append("\n🤖 OCR로 생성된 항목입니다.")
            }
        }

        android.app.AlertDialog.Builder(requireContext())
            .setTitle("🗑️ 지출 내역 삭제")
            .setMessage(message)
            .setPositiveButton("삭제") { _, _ ->
                deleteSpendingItem(spendingItem.id)
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun updateSpendingItem(
        documentId: String,
        category: String,
        amount: Int,
        asset: String,
        memo: String
    ) {
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return
        val uid = currentUser.uid

        val updates = mapOf(
            "category" to category,
            "amount" to amount,
            "asset" to asset,
            "memo" to memo
        )

        db.collection("users").document(uid).collection("spending")
            .document(documentId)
            .update(updates)
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "✅ 지출 내역이 수정되었습니다!", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { error ->
                Toast.makeText(requireContext(), "❌ 수정 실패: ${error.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun deleteSpendingItem(documentId: String) {
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return
        val uid = currentUser.uid

        db.collection("users").document(uid).collection("spending")
            .document(documentId)
            .delete()
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "✅ 지출 내역이 삭제되었습니다!", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { error ->
                Toast.makeText(requireContext(), "❌ 삭제 실패: ${error.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
