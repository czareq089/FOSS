package com.foss.app.network

import com.foss.app.FossApi
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object NetworkModule {
    // 10.0.2.2 = localhost hosta z poziomu emulatora Androida.
    // Na fizycznym urządzeniu podmień na adres IP komputera w sieci lokalnej.
    private const val BASE_URL = "http://192.168.18.13:8080/"

    val api: FossApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(FossApi::class.java)
    }
}