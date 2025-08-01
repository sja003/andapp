package com.example.myapplication

import android.os.Bundle
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
            val amountText = binding.inputAmount.text.toString()
            val category = binding.inputCategory.selectedItem.toString()
            val asset = binding.inputAsset.selectedItem.toString()
            val memo = binding.inputMemo.text.toString()

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
            "asset" to asset,
            "memo" to memo,
            "date" to timestamp
        )

        // Firestore 저장
        db.collection("users").document(uid).collection("spending")
            .add(spending)
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "지출 저장 성공!", Toast.LENGTH_SHORT).show()
                clearInputs()

                // Google 캘린더 연동 (Google 사용자인 경우)
                addToGoogleCalendarIfNeeded(category, amount, memo, timestamp)

                // 홈화면으로 이동
                navigateToHome()
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "저장 실패: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun addToGoogleCalendarIfNeeded(category: String, amount: Int, memo: String, timestamp: Timestamp) {
        val googleAccount = GoogleSignIn.getLastSignedInAccount(requireContext())
        if (googleAccount != null) {
            val googleCalendarHelper = GoogleCalendarHelper(requireContext(), googleAccount)
            val title = "[지출] $category - ${String.format("%,d", amount)}원"
            val description = if (memo.isNotEmpty()) memo else "직접 입력한 지출"

            googleCalendarHelper.insertExpenseEvent(title, description, timestamp)
        }
    }

    private fun clearInputs() {
        binding.inputAmount.text.clear()
        binding.inputMemo.text.clear()
    }

    private fun navigateToHome() {
        requireActivity().supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, HomeFragment())
            .commit()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}