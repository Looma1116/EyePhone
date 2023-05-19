package com.example.eyephone

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers

interface WalkingModeService {
    @Headers("Content-Type: application/json")
    @GET("/getWalkingMode")
    fun getWalkingMode(): Call<WalkingModeResponseBody>
}