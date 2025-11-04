package com.example.myapplication

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.myapplication.databinding.FragmentAddExpenseBinding
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class SpendingFragment : Fragment() {

    private var _binding: FragmentAddExpenseBinding? = null
    private val binding get() = _binding!!

    private val db = FirebaseFirestore.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddExpenseBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupSpinners()
        setupSaveButton()
    }

    private fun setupSpinners() {
        // 카테고리 Spinner 설정
        val categoryList = listOf("식비", "카페", "교통", "쇼핑", "문화생활", "의료", "기타")
        val categoryAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, categoryList)
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.inputCategory.adapter = categoryAdapter

        // 자산 Spinner 설정
        val assetList = listOf("현금", "체크카드", "신용카드", "카카오페이", "토스")
        val assetAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, assetList)
        assetAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.inputAsset.adapter = assetAdapter
    }

    private fun setupSaveButton() {
        binding.saveButton.setOnClickListener {
            val amountText = binding.inputAmount.text?.toString() ?: ""
            val category = binding.inputCategory.selectedItem?.toString() ?: ""
            val asset = binding.inputAsset.selectedItem?.toString() ?: ""
            val memo = binding.inputMemo.text?.toString() ?: ""

            if (amountText.isEmpty()) {
                Toast.makeText(requireContext(), "금액은 필수입니다", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val amount = try {
                amountText.toInt()
            } catch (e: NumberFormatException) {
                Toast.makeText(requireContext(), "금액은 숫자만 입력해주세요", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val currentUser = FirebaseAuth.getInstance().currentUser
            if (currentUser == null) {
                Toast.makeText(requireContext(), "로그인이 필요합니다", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            saveSpending(currentUser.uid, amount, category, asset, memo)
        }
    }

    private fun saveSpending(uid: String, amount: Int, category: String, asset: String, memo: String) {
        val timestamp = Timestamp.now()

        val spending = hashMapOf(
            "amount" to amount,
            "category" to category,
            "asset" to (asset ?: "현금"),  // null이면 기본값
            "memo" to (memo ?: ""),        // null이면 빈 문자열
            "date" to timestamp
        )

        // Firestore 저장
        db.collection("users").document(uid).collection("spending")
            .add(spending)
            .addOnSuccessListener { documentReference ->
                Log.d("SpendingFragment", "지출 저장 성공: ${documentReference.id}")
                Toast.makeText(requireContext(), "지출 저장 성공!", Toast.LENGTH_SHORT).show()

                // 입력 필드 초기화
                clearInputs()

                // 캘린더 이벤트 추가 (앱 내 캘린더 - 모든 사용자용)
                addToCalendarEvent(uid, category, amount, memo, timestamp)

                // Google 캘린더 연동 (안전한 방식으로 처리)
                safeAddToGoogleCalendar(category, amount, memo, timestamp)

                // 안전한 방식으로 홈화면으로 이동
                navigateToHome()
            }
            .addOnFailureListener { e ->
                Log.e("SpendingFragment", "지출 저장 실패", e)
                Toast.makeText(requireContext(), "저장 실패: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun addToCalendarEvent(uid: String, category: String, amount: Int, memo: String, timestamp: Timestamp) {
        // 앱 내 캘린더 이벤트 생성 (모든 사용자용)
        val title = "💰 $category"
        val description = "금액: ${String.format("%,d", amount)}원\n메모: ${memo.ifEmpty { "없음" }}"

        val calendarEvent = hashMapOf(
            "title" to title,
            "description" to description,
            "date" to timestamp,
            "type" to "SPENDING",
            "category" to category,
            "amount" to amount,
            "createdAt" to Timestamp.now()
        )

        db.collection("users").document(uid).collection("events")
            .add(calendarEvent)
            .addOnSuccessListener { documentReference ->
                Log.d("SpendingFragment", "캘린더 이벤트 추가 성공: ${documentReference.id}")
            }
            .addOnFailureListener { e ->
                Log.e("SpendingFragment", "캘린더 이벤트 추가 실패", e)
                // 실패해도 앱은 계속 동작
            }
    }

    private fun safeAddToGoogleCalendar(category: String, amount: Int, memo: String, timestamp: Timestamp) {
        try {
            val googleAccount = GoogleSignIn.getLastSignedInAccount(requireContext())
            if (googleAccount != null) {
                Log.d("SpendingFragment", "Google 계정 확인됨, 캘린더 연동 시도")

                // Google Calendar Helper를 안전하게 사용
                try {
                    val googleCalendarHelper = GoogleCalendarHelper(requireContext(), googleAccount)
                    val title = "[지출] $category - ${String.format("%,d", amount)}원"
                    val description = if (memo.isNotEmpty()) memo else "직접 입력한 지출"

                    googleCalendarHelper.insertExpenseEvent(title, description, timestamp)
                    Log.d("SpendingFragment", "Google 캘린더 연동 성공")
                } catch (e: Exception) {
                    Log.w("SpendingFragment", "Google 캘린더 연동 실패, 앱 내 캘린더만 사용: ${e.message}")
                    // Google 캘린더 연동 실패해도 앱은 정상 동작
                }
            } else {
                Log.d("SpendingFragment", "Google 계정 없음, 앱 내 캘린더만 사용")
            }
        } catch (e: Exception) {
            Log.w("SpendingFragment", "Google 서비스 확인 실패: ${e.message}")
            // 모든 Google 관련 오류를 안전하게 처리
        }
    }

    private fun clearInputs() {
        try {
            binding.inputAmount.setText("")
            binding.inputMemo.setText("")
            // 스피너도 초기화
            binding.inputCategory.setSelection(0)
            binding.inputAsset.setSelection(0)
        } catch (e: Exception) {
            Log.e("SpendingFragment", "입력 필드 초기화 실패", e)
        }
    }

    private fun navigateToHome() {
        try {
            val activity = requireActivity() as? MainActivity
            if (activity != null) {
                // 백스택에서 현재 Fragment 제거
                activity.supportFragmentManager.popBackStack()

                // 하단 네비게이션을 홈으로 설정
                activity.findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottom_navigation_view)
                    ?.selectedItemId = R.id.menu_home

                Log.d("SpendingFragment", "홈으로 이동 완료")
            }
        } catch (e: Exception) {
            Log.e("SpendingFragment", "홈 이동 실패", e)
            // 네비게이션 실패해도 앱이 크래시되지 않도록 처리
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}