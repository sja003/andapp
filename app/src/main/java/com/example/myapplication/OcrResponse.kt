package com.example.myapplication

data class OcrResponse(
    val version: String,
    val requestId: String,
    val timestamp: Long,
    val images: List<OcrImage>
)

data class OcrImage(
    val uid: String,
    val name: String,
    val inferResult: String,
    val message: String?,
    val fields: List<OcrField>
)

data class OcrField(
    val name: OcrName?,
    val valueType: String?,
    val inferText: String,
    val inferConfidence: Double,
    val type: String,
    val lineBreak: Boolean
)

data class OcrName(
    val value: String,
    val confidence: Double
)
