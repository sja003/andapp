package com.example.myapplication

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.InetAddress
import java.net.Inet4Address

object RetrofitInstance {

    private val okHttpClient = OkHttpClient.Builder()
        .dns(object : okhttp3.Dns {
            override fun lookup(hostname: String): List<InetAddress> {
                return InetAddress.getAllByName(hostname)
                    .filter { it is Inet4Address }
            }
        })
        .build()

    fun getInstance(): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://tslvy4ez2z.apigw.ntruss.com/custom/v1/42332/2a22a9f61acdc652a557fc20938ac17040a1049bee97a4214c8e853cd1051aa4/")
            .addConverterFactory(GsonConverterFactory.create())
            .client(okHttpClient)
            .build()
    }
}
