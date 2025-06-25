package com.example.myapplication

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.myapplication.databinding.FragmentAddExpenseBinding
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore


data class Spending(
    val amount: Int = 0,
    val category: String = "",
    val asset: String = "",
    val memo: String = "",
    val date: Timestamp? = null
)

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

        // ✅ 카테고리 Spinner 설정
        val categoryList = listOf("식비", "교통", "쇼핑", "문화생활", "의료", "기타")
        val categoryAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, categoryList)
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.inputCategory.adapter = categoryAdapter

        // ✅ 자산 Spinner 설정
        val assetList = listOf("현금", "체크카드", "신용카드", "카카오페이", "토스")
        val assetAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, assetList)
        assetAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.inputAsset.adapter = assetAdapter

        // 저장 버튼 클릭
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

            val uid = currentUser.uid
            val timestamp = Timestamp.now()

            val spending = hashMapOf(
                "amount" to amount,
                "category" to category,
                "asset" to asset,
                "memo" to memo,
                "date" to timestamp
            )

            // 🔥 Firestore 저장
            db.collection("users").document(uid).collection("spending")
                .add(spending)
                .addOnSuccessListener {
                    Toast.makeText(requireContext(), "지출 저장 성공!", Toast.LENGTH_SHORT).show()
                    binding.inputAmount.text.clear()
                    binding.inputMemo.text.clear()

                    // 🔄 Fragment 전환
                    requireActivity().supportFragmentManager.beginTransaction()
                        .replace(R.id.fragment_container, DailyFragment())
                        .commit()
                }
                .addOnFailureListener {
                    Toast.makeText(requireContext(), "저장 실패: ${it.message}", Toast.LENGTH_SHORT).show()
                }

            // ✅ Google Calendar 이벤트 등록
            val account = com.google.android.gms.auth.api.signin.GoogleSignIn.getLastSignedInAccount(requireContext())
            if (account != null) {
                com.example.myapplication.GoogleCalendarHelper.insertExpenseEvent(
                    context = requireContext(),
                    account = account,
                    title = "[지출] ${category} - ${amount}원",
                    description = memo,
                    timestamp = timestamp
                )
            }
        }

    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
