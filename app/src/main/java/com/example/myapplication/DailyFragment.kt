package com.example.myapplication

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.myapplication.databinding.FragmentDailyBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.*

data class Expense(
    val amount: Int = 0,
    val category: String = "",
    val memo: String = "",
    val date: Date = Date()
)

class DailyFragment : Fragment() {

    private var _binding: FragmentDailyBinding? = null
    private val binding get() = _binding!!

    private val groupedExpenses = mutableMapOf<String, MutableList<Expense>>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDailyBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadMonthlyExpenses()
    }

    private fun loadMonthlyExpenses() {
        val db = FirebaseFirestore.getInstance()
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return

        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        val startOfMonth = calendar.time

        calendar.add(Calendar.MONTH, 1)
        val startOfNextMonth = calendar.time

        db.collection("users")
            .document(currentUser.uid)
            .collection("spending")
            .whereGreaterThanOrEqualTo("date", startOfMonth)
            .whereLessThan("date", startOfNextMonth)
            .orderBy("date", Query.Direction.ASCENDING)
            .get()
            .addOnSuccessListener { result ->
                groupedExpenses.clear()
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                for (doc in result) {
                    val expense = doc.toObject(Expense::class.java)
                    val dateKey = sdf.format(expense.date)
                    groupedExpenses.getOrPut(dateKey) { mutableListOf() }.add(expense)
                }
                displayGroupedExpenses()
            }
    }

    private fun displayGroupedExpenses() {
        val container = binding.expenseContainer
        container.removeAllViews()
        val inflater = layoutInflater
        var totalMonth = 0

        for ((date, expenses) in groupedExpenses) {
            val dateText = TextView(requireContext()).apply {
                text = "📅 $date"
                textSize = 18f
            }
            container.addView(dateText)

            var dayTotal = 0
            for (exp in expenses) {
                dayTotal += exp.amount
                val itemText = TextView(requireContext()).apply {
                    text = "- ${exp.category} / ${exp.amount}원 / ${exp.memo}"
                    textSize = 15f
                    if (exp.category == "OCR") {
                        setTextColor(Color.parseColor("#3366CC")) // OCR 데이터는 파란색 표시
                    }
                }
                container.addView(itemText)
            }

            val sumText = TextView(requireContext()).apply {
                text = "총합: ${dayTotal}원\n"
                textSize = 16f
            }
            container.addView(sumText)

            totalMonth += dayTotal
        }

        binding.textTotalAmount.text = "이번 달 총 지출: ${totalMonth}원"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
