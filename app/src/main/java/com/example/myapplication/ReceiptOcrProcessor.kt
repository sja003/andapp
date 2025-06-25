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

    // 👉 클로바 OCR API에서 발급받은 Secret Key (Base64 아님, 그대로 사용)
    private const val CLIENT_SECRET = "Q1h2UVhkdFNBYkxIQ1dXVEVtS0d6eHBlWVJTWHhraVQ="

    // Gateway 경로: {path+}
    private const val OCR_PATH = "document"

    fun processImage(context: Context, imageFile: File) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d("OCR", "🔍 파일 존재: ${imageFile.exists()}, 경로: ${imageFile.absolutePath}")

                // 이미지 → Base64 변환
// 이미지 축소를 위한 옵션 설정
                val options = BitmapFactory.Options().apply {
                    inSampleSize = 2  // 이미지 크기를 절반으로 줄임 (메모리 1/4)
                }

                val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath, options)
                if (bitmap == null) {
                    Log.e("OCR_ERROR", "❌ bitmap is null")
                    return@launch
                }

// ✅ format은 무조건 "jpg"로 고정
                val format = "jpg"
                Log.d("OCR", "🧾 OCR format: $format")

                val outputStream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)

                val imageBytes = outputStream.toByteArray()
                val base64Image = Base64.encodeToString(imageBytes, Base64.NO_WRAP)

                val jsonObject = mapOf(
                    "version" to "V2",
                    "requestId" to UUID.randomUUID().toString(),
                    "timestamp" to System.currentTimeMillis(),
                    "images" to listOf(
                        mapOf(
                            "name" to "demo",
                            "format" to format,  // 무조건 jpg
                            "data" to base64Image,
                            "lang" to "ko"
                        )
                    )
                )


                val json = Gson().toJson(jsonObject)
                Log.d("OCR", "🧾 JSON 생성 완료: ${json.take(100)}...")

                // Retrofit 요청 준비
                val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
                val requestBody: RequestBody = json.toRequestBody(mediaType)

                val retrofit = RetrofitInstance.getInstance()
                val service = retrofit.create(NaverOcrService::class.java)

                Log.d("OCR", "🚀 Retrofit 호출 시작")

                val call = service.requestOcr(
                    path = "general",
                    body = requestBody,
                    secret = CLIENT_SECRET
                )

                call.enqueue(object : Callback<OcrResponse> {
                    override fun onResponse(call: Call<OcrResponse>, response: Response<OcrResponse>) {
                        if (response.isSuccessful) {
                            val result = response.body()
                            val texts = result?.images
                                ?.flatMap { it.fields }
                                ?.map { it.inferText }

                            Log.d("OCR", "✅ OCR 성공: ${texts?.joinToString()}")

                            if (response.isSuccessful) {
                                val result = response.body()
                                val texts = result?.images
                                    ?.flatMap { it.fields }
                                    ?.map { it.inferText }
                                    ?: emptyList()

                                val ocrText = texts.joinToString(" ")
                                Log.d("OCR", "✅ OCR 전체 텍스트: $ocrText")

                                // 자동 저장 실행
                                FirestoreHelper.saveParsedOcrResult(ocrText)



                            }

                        } else {
                            Log.e("OCR_ERROR", "❌ 응답 실패: ${response.code()} - ${response.errorBody()?.string()}")
                        }
                    }

                    override fun onFailure(call: Call<OcrResponse>, t: Throwable) {
                        Log.e("OCR_ERROR", "❌ 네트워크 오류: ${t.message}")
                        t.printStackTrace()
                    }
                })
            } catch (e: Exception) {
                Log.e("OCR_ERROR", "❌ 예외 발생: ${e.message}")
                e.printStackTrace()
            }
        }
    }
}
