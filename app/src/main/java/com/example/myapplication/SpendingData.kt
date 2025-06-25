package com.example.myapplication


data class SpendingData(
    val date: String = "",
    val items: List<String> = emptyList(),
    val totalAmount: Int = 0,
    val memo: String = "",
    val uid: String = ""
)
