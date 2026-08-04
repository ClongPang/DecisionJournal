package com.example.decisionjournal.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.decisionjournal.data.DecisionInput
import com.example.decisionjournal.data.DecisionRepository
import com.example.decisionjournal.data.ReviewInput
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

@HiltViewModel
class HomeViewModel @Inject constructor(repo: DecisionRepository) : ViewModel() {
    val due = repo.due().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val all = repo.decisions.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

@HiltViewModel
class DecisionsViewModel @Inject constructor(repo: DecisionRepository) : ViewModel() {
    val decisions = repo.decisions.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val stats = decisions.map { calculateDecisionStats(it, System.currentTimeMillis()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DecisionStats(0, 0, null))
}

@HiltViewModel
class CreateDecisionViewModel @Inject constructor(private val repo: DecisionRepository) : ViewModel() {
    var error: String? by mutableStateOf(null)
        private set
    fun save(input: DecisionInput, onSaved: (Long) -> Unit) = viewModelScope.launch {
        repo.save(input).onSuccess(onSaved).onFailure { error = "请填写问题，并至少添加一个候选选项" }
    }
    fun decision(id: Long) = repo.observe(id)
    fun choices(id: Long) = repo.choices(id)
}

@HiltViewModel
class DetailViewModel @Inject constructor(private val repo: DecisionRepository) : ViewModel() {
    fun decision(id: Long) = repo.observe(id)
    fun choices(id: Long) = repo.choices(id)
    fun reviews(id: Long) = repo.reviews(id)
    fun delete(id: Long, done: () -> Unit) = viewModelScope.launch { repo.delete(id); done() }
}

@HiltViewModel
class ReviewViewModel @Inject constructor(private val repo: DecisionRepository) : ViewModel() {
    var error: String? by mutableStateOf(null)
        private set
    fun save(input: ReviewInput, done: () -> Unit) = viewModelScope.launch {
        repo.review(input).onSuccess { done() }.onFailure { error = "请填写复盘结果，满意度需为 1 至 5" }
    }
}
