package com.example.myapplication

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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

                if (!imageFile.exists() || imageFile.length() == 0L) {
                    Log.e("OCR_ERROR", "❌ 파일이 존재하지 않거나 비어있습니다")
                    return@launch
                }

                // 이미지 로드 및 압축
                val options = BitmapFactory.Options().apply {
                    inSampleSize = 2
                }

                val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath, options)
                if (bitmap == null) {
                    Log.e("OCR_ERROR", "❌ 이미지 디코딩 실패")
                    return@launch
                }

                Log.d("OCR_DEBUG", "✅ 이미지 로드 성공: ${bitmap.width}x${bitmap.height}")

                // JPEG로 압축
                val outputStream = ByteArrayOutputStream()
                val compressed = bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)

                if (!compressed) {
                    Log.e("OCR_ERROR", "❌ 이미지 압축 실패")
                    return@launch
                }

                val imageBytes = outputStream.toByteArray()
                val base64Image = Base64.encodeToString(imageBytes, Base64.NO_WRAP)

                Log.d("OCR_DEBUG", "✅ Base64 인코딩 완료: ${base64Image.length} chars")
                Log.d("OCR_DEBUG", "Base64 preview: ${base64Image.take(50)}...")

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
                Log.d("OCR_DEBUG", "Request ID: $requestId")
                Log.d("OCR_DEBUG", "Timestamp: $timestamp")

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
                        Log.d("OCR_DEBUG", "응답 성공: ${response.isSuccessful}")

                        if (response.isSuccessful) {
                            val result = response.body()
                            Log.d("OCR_DEBUG", "응답 바디 존재: ${result != null}")

                            if (result?.images?.isNotEmpty() == true) {
                                val extractedTexts = mutableListOf<String>()

                                result.images.forEach { image ->
                                    Log.d("OCR_DEBUG", "이미지 필드 수: ${image.fields.size}")
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
                                Log.d("OCR_SUCCESS", "=== 전체 OCR 결과 ===")
                                Log.d("OCR_SUCCESS", fullOcrText)
                                Log.d("OCR_SUCCESS", "========================")

                                if (fullOcrText.isNotEmpty()) {
                                    FirestoreHelper.saveParsedOcrResult(fullOcrText)
                                } else {
                                    Log.w("OCR_WARNING", "⚠️ 추출된 텍스트가 비어있습니다")
                                }

                            } else {
                                Log.w("OCR_WARNING", "⚠️ OCR 결과 이미지가 없습니다")
                                Log.d("OCR_DEBUG", "Result: $result")
                            }

                        } else {
                            val errorBody = response.errorBody()?.string()
                            Log.e("OCR_ERROR", "❌ API 호출 실패")
                            Log.e("OCR_ERROR", "상태 코드: ${response.code()}")
                            Log.e("OCR_ERROR", "에러 바디: $errorBody")
                            Log.e("OCR_ERROR", "응답 헤더: ${response.headers()}")
                        }
                    }

                    override fun onFailure(call: Call<OcrResponse>, t: Throwable) {
                        Log.e("OCR_ERROR", "❌ 네트워크 오류")
                        Log.e("OCR_ERROR", "오류 메시지: ${t.message}")
                        Log.e("OCR_ERROR", "오류 타입: ${t.javaClass.simpleName}")
                        t.printStackTrace()
                    }
                })

            } catch (e: Exception) {
                Log.e("OCR_ERROR", "❌ 처리 중 예외 발생")
                Log.e("OCR_ERROR", "예외 메시지: ${e.message}")
                Log.e("OCR_ERROR", "예외 타입: ${e.javaClass.simpleName}")
                e.printStackTrace()
            }
        }
    }
}