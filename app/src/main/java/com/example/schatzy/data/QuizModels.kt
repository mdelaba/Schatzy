package com.example.schatzy.data

import android.text.Html
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class QuizResponse(
    @Json(name = "response_code") val responseCode: Int,
    val results: List<Question>
)

@JsonClass(generateAdapter = true)
data class Question(
    val category: String,
    val type: String,
    val difficulty: String,
    val question: String,
    @Json(name = "correct_answer") val correctAnswer: String,
    @Json(name = "incorrect_answers") val incorrectAnswers: List<String>
) {
    val decodedQuestion: String
        get() = Html.fromHtml(question, Html.FROM_HTML_MODE_LEGACY).toString()

    val decodedCorrectAnswer: String
        get() = Html.fromHtml(correctAnswer, Html.FROM_HTML_MODE_LEGACY).toString()

    val decodedIncorrectAnswers: List<String>
        get() = incorrectAnswers.map { Html.fromHtml(it, Html.FROM_HTML_MODE_LEGACY).toString() }

    val allAnswers: List<String>
        get() = (decodedIncorrectAnswers + decodedCorrectAnswer).shuffled()
} 