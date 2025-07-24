package com.example.schatzy.ui.home

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.schatzy.databinding.FragmentHomeBinding

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: HomeViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        viewModel = ViewModelProvider(this)[HomeViewModel::class.java]
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupObservers()
        setupClickListeners()
    }

    private fun setupObservers() {
        viewModel.currentQuestion.observe(viewLifecycleOwner) { question ->
            if (question == null) {
                showQuizCompleted()
            } else {
                displayQuestion(question)
            }
        }

        viewModel.feedback.observe(viewLifecycleOwner) { feedback ->
            if (feedback != null) {
                binding.feedbackTextView.apply {
                    text = feedback
                    setTextColor(if (feedback.startsWith("Correct")) Color.GREEN else Color.RED)
                    visibility = View.VISIBLE
                }
                binding.submitButton.visibility = View.GONE
                binding.nextButton.visibility = View.VISIBLE
                binding.answersRadioGroup.isEnabled = false
            } else {
                binding.feedbackTextView.visibility = View.GONE
                binding.submitButton.visibility = View.VISIBLE
                binding.nextButton.visibility = View.GONE
                binding.answersRadioGroup.isEnabled = true
            }
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            binding.questionCard.visibility = if (isLoading) View.GONE else View.VISIBLE
        }

        viewModel.error.observe(viewLifecycleOwner) { error ->
            if (error != null) {
                binding.errorTextView.text = error
                binding.errorTextView.visibility = View.VISIBLE
            } else {
                binding.errorTextView.visibility = View.GONE
            }
        }

        viewModel.score.observe(viewLifecycleOwner) { score ->
            updateScoreDisplay(score ?: 0, viewModel.highScore.value ?: 0)
        }

        viewModel.highScore.observe(viewLifecycleOwner) { highScore ->
            updateScoreDisplay(viewModel.score.value ?: 0, highScore ?: 0)
        }
    }

    private fun setupClickListeners() {
        binding.submitButton.setOnClickListener {
            val selectedId = binding.answersRadioGroup.checkedRadioButtonId
            if (selectedId != -1) {
                val selectedAnswer = view?.findViewById<RadioButton>(selectedId)?.text.toString()
                viewModel.checkAnswer(selectedAnswer)
            } else {
                Toast.makeText(context, "Please select an answer", Toast.LENGTH_SHORT).show()
            }
        }

        binding.nextButton.setOnClickListener {
            viewModel.moveToNextQuestion()
            binding.answersRadioGroup.clearCheck()
        }

        binding.restartButton.setOnClickListener {
            viewModel.restartQuiz()
            binding.answersRadioGroup.clearCheck()
            binding.restartButton.visibility = View.GONE
        }
    }

    private fun displayQuestion(question: com.example.schatzy.data.Question) {
        binding.questionTextView.text = question.decodedQuestion
        val answers = question.allAnswers
        binding.answer1RadioButton.text = answers[0]
        binding.answer2RadioButton.text = answers[1]
        binding.answer3RadioButton.text = answers[2]
        binding.answer4RadioButton.text = answers[3]
    }

    private fun showQuizCompleted() {
        binding.questionCard.visibility = View.GONE
        binding.feedbackTextView.visibility = View.GONE
        binding.nextButton.visibility = View.GONE
        binding.restartButton.visibility = View.VISIBLE
        
        val finalScore = viewModel.score.value ?: 0
        val highScore = viewModel.highScore.value ?: 0
        val message = if (finalScore > 0 && finalScore == highScore) {
            "Quiz completed! New high score: $finalScore"
        } else {
            "Quiz completed! Final score: $finalScore"
        }
        
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    private fun updateScoreDisplay(score: Int, highScore: Int) {
        binding.scoreTextView.text = "Score: $score | High Score: $highScore"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}