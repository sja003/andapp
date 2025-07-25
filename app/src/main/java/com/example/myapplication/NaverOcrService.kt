package com.example.myapplication

import okhttp3.RequestBody
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface NaverOcrService {

    @POST("custom/v1/42332/2a22a9f61acdc652a557fc20938ac17040a1049bee97a4214c8e853cd1051aa4/general")
    fun requestOcr(
        @Body body: RequestBody,
        @Header("X-OCR-SECRET") secret: String,
        @Header("Content-Type") contentType: String = "application/json"
    ): Call<OcrResponse>
}