package com.example.myapplication

import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.myapplication.databinding.FragmentMonthlyBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class MonthlyFragment : Fragment() {

    private var _binding: FragmentMonthlyBinding? = null
    private val binding get() = _binding!!

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMonthlyBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.monthlyRecyclerView.layoutManager = LinearLayoutManager(requireContext())

        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(requireContext(), "로그인이 필요합니다.", Toast.LENGTH_SHORT).show()
            return
        }

        // ✅ Firestore의 경로를 사용자 하위 컬렉션으로 수정
        db.collection("users")
            .document(currentUser.uid)
            .collection("spending")
            .get()
            .addOnSuccessListener { result ->
                val monthlyMap = mutableMapOf<String, Int>()

                for (document in result) {
                    val timestamp = document.getDate("date")
                    val amount = (document.getLong("amount") ?: 0).toInt()

                    if (timestamp != null) {
                        val calendar = Calendar.getInstance().apply { time = timestamp }
                        val key = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(calendar.time)

                        monthlyMap[key] = monthlyMap.getOrDefault(key, 0) + amount
                    }
                }

                val monthlySummaries = monthlyMap
                    .toSortedMap()
                    .map { MonthlySummary(it.key, it.value) }

                val adapter = MonthlyAdapter(monthlySummaries)
                binding.monthlyRecyclerView.adapter = adapter
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "데이터를 불러오지 못했습니다.", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
