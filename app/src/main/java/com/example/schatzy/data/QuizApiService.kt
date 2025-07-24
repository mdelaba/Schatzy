package com.example.schatzy.data

import retrofit2.http.GET

interface QuizApiService {
    @GET("api.php?amount=20&category=9&type=multiple")
    suspend fun getQuestions(): QuizResponse
} 