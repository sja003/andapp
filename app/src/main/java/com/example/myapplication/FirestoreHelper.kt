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

        Log.d("OCR_PARSE", "원본 텍스트:\n$ocrText")

        // 1. 텍스트를 줄 단위로 분리
        val lines = ocrText.split("\n").map { it.trim() }.filter { it.isNotEmpty() }

        // 2. 개별 품목과 가격 추출
        val menuItems = mutableListOf<Pair<String, Int>>()
        var totalAmount = 0

        for (line in lines) {
            // 품목명과 가격이 함께 있는 패턴 찾기
            val itemWithPricePattern = Regex("(.+?)\\s*([0-9]{1,3}(?:,[0-9]{3})*|[0-9]{3,})\\s*원?\\s*$")
            val match = itemWithPricePattern.find(line)

            if (match != null) {
                val itemName = match.groupValues[1].trim()
                val priceStr = match.groupValues[2].replace(",", "")

                try {
                    val price = priceStr.toInt()

                    // 유효한 품목명 필터링 (2글자 이상, 특수문자 제외)
                    if (itemName.length >= 2 &&
                        !itemName.contains(Regex("[0-9]{4,}")) && // 긴 숫자 제외
                        !itemName.matches(Regex(".*[합계|총계|소계|부가세|VAT|카드|현금].*")) &&
                        price >= 500 && price <= 100000) { // 합리적인 가격 범위

                        menuItems.add(Pair(itemName, price))
                        Log.d("OCR_PARSE", "품목 추출: $itemName - ${price}원")
                    }
                } catch (e: NumberFormatException) {
                    Log.d("OCR_PARSE", "가격 변환 실패: $priceStr")
                }
            }
        }

        // 3. 총합 계산 (추출된 품목들의 합)
        totalAmount = menuItems.sumOf { it.second }

        // 4. 영수증에서 명시된 총합도 찾아보기
        var receiptTotal = 0
        val totalPatterns = listOf(
            Regex("합\\s*계[:\\s]*([0-9]{1,3}(?:,[0-9]{3})*|[0-9]{3,})"),
            Regex("총\\s*계[:\\s]*([0-9]{1,3}(?:,[0-9]{3})*|[0-9]{3,})"),
            Regex("총\\s*액[:\\s]*([0-9]{1,3}(?:,[0-9]{3})*|[0-9]{3,})"),
            Regex("결제금액[:\\s]*([0-9]{1,3}(?:,[0-9]{3})*|[0-9]{3,})")
        )

        for (pattern in totalPatterns) {
            val totalMatch = pattern.find(ocrText)
            if (totalMatch != null) {
                try {
                    receiptTotal = totalMatch.groupValues[1].replace(",", "").toInt()
                    Log.d("OCR_PARSE", "영수증 총합 발견: ${receiptTotal}원")
                    break
                } catch (e: NumberFormatException) {
                    continue
                }
            }
        }

        // 5. 최종 금액 결정 (영수증 총합을 우선, 없으면 품목 합계)
        val finalAmount = if (receiptTotal > 0) receiptTotal else totalAmount

        // 6. 품목 요약 생성
        val itemsSummary = if (menuItems.isNotEmpty()) {
            menuItems.take(5).joinToString(", ") { "${it.first}(${it.second}원)" }
        } else {
            "OCR 인식 항목"
        }

        Log.d("OCR_PARSE", "=== 최종 결과 ===")
        Log.d("OCR_PARSE", "품목들: $itemsSummary")
        Log.d("OCR_PARSE", "품목 합계: ${totalAmount}원")
        Log.d("OCR_PARSE", "영수증 총합: ${receiptTotal}원")
        Log.d("OCR_PARSE", "최종 저장 금액: ${finalAmount}원")

        // 7. Firestore에 저장
        val data = mapOf(
            "amount" to finalAmount,
            "category" to "OCR",
            "asset" to "OCR 인식",
            "memo" to itemsSummary,
            "date" to Date(),
            "ocrDetails" to mapOf(
                "items" to menuItems.map { mapOf("name" to it.first, "price" to it.second) },
                "itemsTotal" to totalAmount,
                "receiptTotal" to receiptTotal,
                "rawText" to ocrText.take(500) // 원본 텍스트 일부 저장
            )
        )

        Firebase.firestore
            .collection("users")
            .document(uid)
            .collection("spending")
            .add(data)
            .addOnSuccessListener {
                Log.d("OCR", "✅ OCR 상세 정보 저장 완료: ${it.id}")
                Log.d("OCR", "저장된 내용: 금액=${finalAmount}원, 품목=${menuItems.size}개")
            }
            .addOnFailureListener {
                Log.e("OCR", "❌ OCR 저장 실패: ${it.message}")
            }
    }
}