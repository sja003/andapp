package com.example.myapplication

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.util.Date

object FirestoreHelper {
    fun saveParsedOcrResult(ocrText: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        // 1. 금액 추출: ₩14,400 또는 14400
        val amountRegex = Regex("₩?([0-9]{1,3}(?:,[0-9]{3})+|[0-9]{4,})")
        val price = amountRegex.findAll(ocrText)
            .map { it.groupValues[1].replace(",", "").toIntOrNull() }
            .filterNotNull()
            .maxOrNull() ?: 0

        // 2. 품목 추출: 냉면, 아이스티 등 (간단한 한글+숫자 단어 기준)
        val menuRegex = Regex("([가-힣a-zA-Z\\+]{2,})")
        val allWords = menuRegex.findAll(ocrText).map { it.value }.toList()
        val keywords = allWords.filter {
            it.contains("면") || it.contains("티") || it.contains("만두") || it.contains("김치") || it.contains("밥")
        }

        val summary = keywords.take(5).joinToString(", ")  // 최대 5개만 요약

        // 저장
        val data = mapOf(
            "amount" to price,
            "category" to "OCR",
            "memo" to summary.ifEmpty { "OCR 인식 항목" },
            "date" to Date()
        )

        Firebase.firestore
            .collection("users")
            .document(uid)
            .collection("spending")
            .add(data)
            .addOnSuccessListener {
                Log.d("OCR", "✅ OCR 요약 저장 완료: ${it.id}")
            }
            .addOnFailureListener {
                Log.e("OCR", "❌ OCR 요약 저장 실패: ${it.message}")
            }
    }
}
