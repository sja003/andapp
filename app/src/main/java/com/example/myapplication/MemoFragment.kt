package com.example.myapplication

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.myapplication.databinding.FragmentMemoBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

data class Memo(
    val memo: String = "",
    val date: com.google.firebase.Timestamp? = null,
    val uid: String = ""
)

class MemoFragment : Fragment() {

    private var _binding: FragmentMemoBinding? = null
    private val binding get() = _binding!!
    private val memoList = mutableListOf<Memo>()
    private lateinit var adapter: MemoAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMemoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = MemoAdapter(memoList)
        binding.memoRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.memoRecyclerView.adapter = adapter

        loadMemosFromFirestore()
    }

    private fun loadMemosFromFirestore() {
        val db = FirebaseFirestore.getInstance()
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return

        db.collection("spending")
            .whereEqualTo("uid", currentUser.uid)
            .whereNotEqualTo("memo", "")
            .orderBy("memo", Query.Direction.ASCENDING) // or orderBy("date", DESCENDING) if you want recent first
            .get()
            .addOnSuccessListener { result ->
                memoList.clear()
                for (doc in result) {
                    val memo = doc.toObject(Memo::class.java)
                    memoList.add(memo)
                }
                adapter.notifyDataSetChanged()
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
