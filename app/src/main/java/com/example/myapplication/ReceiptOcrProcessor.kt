package com.example.myapplication

import android.app.AlertDialog
import android.content.Context
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import android.widget.Toast
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import android.graphics.Bitmap

object ReceiptOcrProcessor {

    // 네이버 클로바 OCR Secret Key
    private const val CLIENT_SECRET = "Q1h2UVhkdFNBYkxIQ1dXVEVtS0d6eHBlWVJTWHhraVQ="

    fun processImage(context: Context, imageFile: File) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d("OCR_DEBUG", "=== OCR 처리 시작 ===")
                Log.d("OCR_DEBUG", "파일 존재: ${imageFile.exists()}")
                Log.d("OCR_DEBUG", "파일 크기: ${imageFile.length()} bytes")
                Log.d("OCR_DEBUG", "파일 경로: ${imageFile.absolutePath}")

                // UI에 처리 시작 알림
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "🔍 영수증 분석 중...", Toast.LENGTH_SHORT).show()
                }

                if (!imageFile.exists() || imageFile.length() == 0L) {
                    Log.e("OCR_ERROR", "❌ 파일이 존재하지 않거나 비어있습니다")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "❌ 이미지 파일 오류", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                // 이미지 로드 및 압축
                val options = BitmapFactory.Options().apply {
                    inSampleSize = 2
                }

                val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath, options)
                if (bitmap == null) {
                    Log.e("OCR_ERROR", "❌ 이미지 디코딩 실패")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "❌ 이미지 읽기 실패", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                Log.d("OCR_DEBUG", "✅ 이미지 로드 성공: ${bitmap.width}x${bitmap.height}")

                // JPEG로 압축
                val outputStream = ByteArrayOutputStream()
                val compressed = bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)

                if (!compressed) {
                    Log.e("OCR_ERROR", "❌ 이미지 압축 실패")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "❌ 이미지 압축 실패", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                val imageBytes = outputStream.toByteArray()
                val base64Image = Base64.encodeToString(imageBytes, Base64.NO_WRAP)

                Log.d("OCR_DEBUG", "✅ Base64 인코딩 완료: ${base64Image.length} chars")

                // OCR API 요청 JSON
                val requestId = UUID.randomUUID().toString()
                val timestamp = System.currentTimeMillis()

                val jsonObject = mapOf(
                    "version" to "V2",
                    "requestId" to requestId,
                    "timestamp" to timestamp,
                    "images" to listOf(
                        mapOf(
                            "name" to "receipt_${timestamp}",
                            "format" to "jpg",
                            "data" to base64Image
                        )
                    )
                )

                val json = Gson().toJson(jsonObject)
                Log.d("OCR_DEBUG", "✅ JSON 생성 완료")

                // Retrofit 요청
                val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
                val requestBody: RequestBody = json.toRequestBody(mediaType)

                val retrofit = RetrofitInstance.getInstance()
                val service = retrofit.create(NaverOcrService::class.java)

                Log.d("OCR_DEBUG", "🚀 API 호출 시작...")

                val call = service.requestOcr(
                    body = requestBody,
                    secret = CLIENT_SECRET
                )

                call.enqueue(object : Callback<OcrResponse> {
                    override fun onResponse(call: Call<OcrResponse>, response: Response<OcrResponse>) {
                        Log.d("OCR_DEBUG", "=== API 응답 수신 ===")
                        Log.d("OCR_DEBUG", "상태 코드: ${response.code()}")

                        if (response.isSuccessful) {
                            val result = response.body()

                            if (result?.images?.isNotEmpty() == true) {
                                val extractedTexts = mutableListOf<String>()

                                result.images.forEach { image ->
                                    image.fields.forEach { field ->
                                        val text = field.inferText.trim()
                                        if (text.isNotEmpty()) {
                                            extractedTexts.add(text)
                                        }
                                    }
                                }

                                val fullOcrText = extractedTexts.joinToString("\n")

                                Log.d("OCR_SUCCESS", "✅ OCR 성공!")
                                Log.d("OCR_SUCCESS", "추출된 텍스트 수: ${extractedTexts.size}")
                                Log.d("OCR_SUCCESS", "전체 OCR 결과:\n$fullOcrText")

                                if (fullOcrText.isNotEmpty()) {
                                    // Firestore에 저장
                                    FirestoreHelper.saveParsedOcrResult(fullOcrText)

                                    // UI에 결과 표시
                                    CoroutineScope(Dispatchers.Main).launch {
                                        showOcrResultDialog(context, fullOcrText, extractedTexts.size)
                                    }
                                } else {
                                    CoroutineScope(Dispatchers.Main).launch {
                                        Toast.makeText(context, "⚠️ 텍스트를 인식하지 못했습니다", Toast.LENGTH_LONG).show()
                                    }
                                }

                            } else {
                                Log.w("OCR_WARNING", "⚠️ OCR 결과 이미지가 없습니다")
                                CoroutineScope(Dispatchers.Main).launch {
                                    Toast.makeText(context, "⚠️ 영수증에서 텍스트를 찾을 수 없습니다", Toast.LENGTH_LONG).show()
                                }
                            }

                        } else {
                            val errorBody = response.errorBody()?.string()
                            Log.e("OCR_ERROR", "❌ API 호출 실패")
                            Log.e("OCR_ERROR", "상태 코드: ${response.code()}")
                            Log.e("OCR_ERROR", "에러 바디: $errorBody")

                            CoroutineScope(Dispatchers.Main).launch {
                                Toast.makeText(context, "❌ OCR API 오류: ${response.code()}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }

                    override fun onFailure(call: Call<OcrResponse>, t: Throwable) {
                        Log.e("OCR_ERROR", "❌ 네트워크 오류")
                        Log.e("OCR_ERROR", "오류 메시지: ${t.message}")
                        t.printStackTrace()

                        CoroutineScope(Dispatchers.Main).launch {
                            Toast.makeText(context, "❌ 네트워크 오류: ${t.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                })

            } catch (e: Exception) {
                Log.e("OCR_ERROR", "❌ 처리 중 예외 발생")
                Log.e("OCR_ERROR", "예외 메시지: ${e.message}")
                e.printStackTrace()

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "❌ 처리 오류: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showOcrResultDialog(context: Context, fullText: String, textCount: Int) {
        // OCR 결과를 파싱
        val parsedData = parseOcrText(fullText)

        val dialogMessage = buildString {
            append("🎉 OCR 분석 완료!\n\n")
            append("📊 인식된 텍스트: ${textCount}개\n\n")

            if (parsedData.menuItems.isNotEmpty()) {
                append("🍽️ 인식된 품목:\n")
                parsedData.menuItems.take(5).forEach { item ->
                    append("• ${item.name}: ${String.format("%,d", item.price)}원\n")
                }
                if (parsedData.menuItems.size > 5) {
                    append("• 외 ${parsedData.menuItems.size - 5}개 품목\n")
                }
                append("\n")
            }

            append("💰 금액 정보:\n")
            append("• 품목 합계: ${String.format("%,d", parsedData.itemsTotal)}원\n")
            append("• 영수증 총액: ${String.format("%,d", parsedData.receiptTotal)}원\n")
            append("• 저장 금액: ${String.format("%,d", parsedData.finalAmount)}원\n\n")

            append("✅ 자동으로 가계부에 저장되었습니다!")
        }

        AlertDialog.Builder(context)
            .setTitle("📄 영수증 분석 결과")
            .setMessage(dialogMessage)
            .setPositiveButton("확인") { dialog, _ ->
                dialog.dismiss()
                Toast.makeText(context, "💾 ${String.format("%,d", parsedData.finalAmount)}원이 저장되었습니다!", Toast.LENGTH_LONG).show()
            }
            .setNeutralButton("전체 텍스트 보기") { _, _ ->
                showFullTextDialog(context, fullText)
            }
            .setCancelable(false)
            .show()
    }

    private fun showFullTextDialog(context: Context, fullText: String) {
        AlertDialog.Builder(context)
            .setTitle("📝 인식된 전체 텍스트")
            .setMessage(fullText)
            .setPositiveButton("닫기") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun parseOcrText(ocrText: String): ParsedOcrData {
        val lines = ocrText.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        val menuItems = mutableListOf<MenuItem>()

        // 품목과 가격 추출
        for (line in lines) {
            val itemWithPricePattern = Regex("(.+?)\\s*([0-9]{1,3}(?:,[0-9]{3})*|[0-9]{3,})\\s*원?\\s*$")
            val match = itemWithPricePattern.find(line)

            if (match != null) {
                val itemName = match.groupValues[1].trim()
                val priceStr = match.groupValues[2].replace(",", "")

                try {
                    val price = priceStr.toInt()

                    if (itemName.length >= 2 &&
                        !itemName.contains(Regex("[0-9]{4,}")) &&
                        !itemName.matches(Regex(".*[합계|총계|소계|부가세|VAT|카드|현금].*")) &&
                        price >= 500 && price <= 100000) {

                        menuItems.add(MenuItem(itemName, price))
                    }
                } catch (e: NumberFormatException) {
                    Log.d("OCR_PARSE", "가격 변환 실패: $priceStr")
                }
            }
        }

        // 총합 찾기
        val totalPatterns = listOf(
            Regex("합\\s*계[:\\s]*([0-9]{1,3}(?:,[0-9]{3})*|[0-9]{3,})"),
            Regex("총\\s*계[:\\s]*([0-9]{1,3}(?:,[0-9]{3})*|[0-9]{3,})"),
            Regex("총\\s*액[:\\s]*([0-9]{1,3}(?:,[0-9]{3})*|[0-9]{3,})"),
            Regex("결제금액[:\\s]*([0-9]{1,3}(?:,[0-9]{3})*|[0-9]{3,})")
        )

        var receiptTotal = 0
        for (pattern in totalPatterns) {
            val totalMatch = pattern.find(ocrText)
            if (totalMatch != null) {
                try {
                    receiptTotal = totalMatch.groupValues[1].replace(",", "").toInt()
                    break
                } catch (e: NumberFormatException) {
                    continue
                }
            }
        }

        val itemsTotal = menuItems.sumOf { it.price }
        val finalAmount = if (receiptTotal > 0) receiptTotal else itemsTotal

        return ParsedOcrData(menuItems, itemsTotal, receiptTotal, finalAmount)
    }

    data class MenuItem(val name: String, val price: Int)
    data class ParsedOcrData(
        val menuItems: List<MenuItem>,
        val itemsTotal: Int,
        val receiptTotal: Int,
        val finalAmount: Int
    )
}