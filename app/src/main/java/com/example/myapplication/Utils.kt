package com.example.myapplication

import android.util.Base64
import java.io.File

object Utils {
    fun encodeBase64(file: File): String {
        val bytes = file.readBytes()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }
}