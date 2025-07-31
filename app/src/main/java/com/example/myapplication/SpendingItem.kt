package com.example.myapplication

import com.google.firebase.Timestamp

data class SpendingItem(
    val id: String = "",
    val amount: Int = 0,
    val category: String = "",
    val asset: String = "",
    val memo: String = "",
    val date: Timestamp? = null,
    val isOcrGenerated: Boolean = false,
    val ocrDetails: Map<String, Any>? = null
)