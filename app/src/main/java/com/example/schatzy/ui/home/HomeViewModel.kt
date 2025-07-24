package com.example.schatzy.ui.home

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.schatzy.data.Question
import com.example.schatzy.data.QuizApiService
import com.example.schatzy.data.QuizResponse
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val apiService: QuizApiService = Retrofit.Builder()
        .baseUrl("https://opentdb.com/")
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(QuizApiService::class.java)

    private val _currentQuestion = MutableLiveData<Question>()
    val currentQuestion: LiveData<Question> = _currentQuestion

    private val _feedback = MutableLiveData<String>()
    val feedback: LiveData<String> = _feedback

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String>()
    val error: LiveData<String> = _error

    private val _score = MutableLiveData<Int>()
    val score: LiveData<Int> = _score

    private val _highScore = MutableLiveData<Int>()
    val highScore: LiveData<Int> = _highScore

    private var questions: List<Question> = emptyList()
    private var currentQuestionIndex = 0
    private var currentScore = 0
    private var currentHighScore = 0

    init {
        loadHighScore()
        loadQuestions()
    }

    private fun loadQuestions() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val response = apiService.getQuestions()
                if (response.responseCode == 0) {
                    questions = response.results
                    if (questions.isNotEmpty()) {
                        _currentQuestion.value = questions[0]
                    }
                } else {
                    _error.value = "Failed to load questions"
                }
            } catch (e: Exception) {
                _error.value = "Error: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun checkAnswer(selectedAnswer: String) {
        val question = _currentQuestion.value ?: return
        val isCorrect = selectedAnswer == question.decodedCorrectAnswer
        
        if (isCorrect) {
            currentScore++
            _score.value = currentScore
        }
        
        _feedback.value = if (isCorrect) {
            "Correct!"
        } else {
            "Incorrect. The correct answer was: ${question.decodedCorrectAnswer}"
        }
    }

    fun moveToNextQuestion() {
        currentQuestionIndex++
        if (currentQuestionIndex < questions.size) {
            _currentQuestion.value = questions[currentQuestionIndex]
            _feedback.value = null
        } else {
            // Quiz completed - check for new high score
            if (currentScore > currentHighScore) {
                currentHighScore = currentScore
                _highScore.value = currentHighScore
                saveHighScore()
            }
            _currentQuestion.value = null
        }
    }

    fun restartQuiz() {
        currentQuestionIndex = 0
        currentScore = 0
        _score.value = currentScore
        _feedback.value = null
        loadQuestions()
    }

    private fun loadHighScore() {
        val sharedPrefs = getApplication<Application>().getSharedPreferences("quiz_game", Context.MODE_PRIVATE)
        currentHighScore = sharedPrefs.getInt("high_score", 0)
        _highScore.value = currentHighScore
        _score.value = 0
    }

    private fun saveHighScore() {
        val sharedPrefs = getApplication<Application>().getSharedPreferences("quiz_game", Context.MODE_PRIVATE)
        sharedPrefs.edit().putInt("high_score", currentHighScore).apply()
    }
}