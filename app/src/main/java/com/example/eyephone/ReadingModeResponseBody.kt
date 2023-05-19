package com.example.eyephone

import com.google.gson.annotations.SerializedName

data class ReadingModeResponseBody(
    @SerializedName("result")
    val result: String?,
    @SerializedName("status")
    val status: String
)
