package com.example.myapplication

import okhttp3.RequestBody
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

interface NaverOcrService {

    @POST("{path}")  // ← Retrofit이 baseUrl 뒤에 이 path를 붙여서 요청
    fun requestOcr(
        @Path(value = "path", encoded = true) path: String,
        @Body body: RequestBody,  // ← JSON 바디 전체
        @Header("X-OCR-SECRET") secret: String  // ← 클로바 API 인증 헤더
    ): Call<OcrResponse>  // ← 결과를 OcrResponse로 받음
}
