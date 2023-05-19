package com.example.eyephone

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers

interface ReadingModeService {
    @Headers("Content-Type: application/json")
    @GET("/getReadingMode")
    fun getReadingMode(): Call<ReadingModeResponseBody>
}