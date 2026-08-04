package com.example.decisionjournal.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.decisionjournal.data.DecisionInput
import com.example.decisionjournal.data.DecisionRepository
import com.example.decisionjournal.data.ReviewInput
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.time.LocalDate
import java.time.ZoneId

sealed interface SaveState {
    data object Idle : SaveState
    data object Saving : SaveState
    data object Success : SaveState
    data class Error(val message: String) : SaveState
}

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
    private val filter = MutableStateFlow<DecisionFilter>(DecisionFilter.All)
    val selectedFilter = filter.asStateFlow()
    val periodCounts = decisions
        .map { calculatePeriodCounts(it, LocalDate.now(), ZoneId.systemDefault()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PeriodCounts())
    val filteredDecisions = combine(decisions, filter) { items, currentFilter ->
        filterDecisions(items, currentFilter, LocalDate.now(), ZoneId.systemDefault())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun selectFilter(value: DecisionFilter) {
        filter.value = if (filter.value == value) DecisionFilter.All else value
    }

    fun clearFilter() {
        filter.value = DecisionFilter.All
    }
}

@HiltViewModel
class CreateDecisionViewModel @Inject constructor(private val repo: DecisionRepository) : ViewModel() {
    var error: String? by mutableStateOf(null)
        private set
    var saveState: SaveState by mutableStateOf(SaveState.Idle)
        private set
    fun save(input: DecisionInput, onSaved: (Long) -> Unit) = viewModelScope.launch {
        if (saveState == SaveState.Saving) return@launch
        error = null
        saveState = SaveState.Saving
        repo.save(input)
            .onSuccess {
                saveState = SaveState.Success
                onSaved(it)
            }
            .onFailure {
                val message = it.message ?: "保存失败，请稍后重试"
                error = message
                saveState = SaveState.Error(message)
            }
    }
    fun decision(id: Long) = repo.observe(id)
    fun editor(id: Long) = repo.editor(id)
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
    var saveState: SaveState by mutableStateOf(SaveState.Idle)
        private set
    fun decision(id: Long) = repo.observe(id)
    fun choices(id: Long) = repo.choices(id)
    fun save(input: ReviewInput, done: () -> Unit) = viewModelScope.launch {
        if (saveState == SaveState.Saving) return@launch
        error = null
        saveState = SaveState.Saving
        repo.review(input)
            .onSuccess {
                saveState = SaveState.Success
                done()
            }
            .onFailure {
                val message = it.message ?: "保存失败，请稍后重试"
                error = message
                saveState = SaveState.Error(message)
            }
    }
}
