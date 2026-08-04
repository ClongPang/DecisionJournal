package com.example.decisionjournal.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.decisionjournal.data.DecisionInput
import com.example.decisionjournal.data.DecisionRepository
import com.example.decisionjournal.data.ReviewInput
import com.example.decisionjournal.data.SaveOutcome
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.time.LocalDate
import java.time.ZoneId

private fun minuteClock() = flow {
    while (currentCoroutineContext().isActive) {
        emit(System.currentTimeMillis())
        delay(60_000)
    }
}

sealed interface SaveState {
    data object Idle : SaveState
    data object Saving : SaveState
    data class Success(val reminderWarning: String? = null) : SaveState
    data class Error(val message: String) : SaveState
}

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel @Inject constructor(repo: DecisionRepository) : ViewModel() {
    val all = repo.decisions.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val clock = minuteClock().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), System.currentTimeMillis())
    val due = clock.flatMapLatest(repo::due)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

@HiltViewModel
class DecisionsViewModel @Inject constructor(repo: DecisionRepository) : ViewModel() {
    val decisions = repo.decisions.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val now = minuteClock().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), System.currentTimeMillis())
    val stats = combine(decisions, now) { items, currentTime -> calculateDecisionStats(items, currentTime) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DecisionStats(0, 0, null))
    private val filter = MutableStateFlow<DecisionFilter>(DecisionFilter.All)
    val selectedFilter = filter.asStateFlow()
    val periodCounts = combine(decisions, now)
        { items, currentTime -> calculatePeriodCounts(items, currentDate(currentTime), ZoneId.systemDefault()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PeriodCounts())
    val filteredDecisions = combine(decisions, filter, now) { items, currentFilter, currentTime ->
        filterDecisions(items, currentFilter, currentDate(currentTime), ZoneId.systemDefault())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun selectFilter(value: DecisionFilter) {
        filter.value = if (filter.value == value) DecisionFilter.All else value
    }

    fun clearFilter() {
        filter.value = DecisionFilter.All
    }
}

private fun currentDate(timestamp: Long): LocalDate =
    java.time.Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate()

@HiltViewModel
class CreateDecisionViewModel @Inject constructor(private val repo: DecisionRepository) : ViewModel() {
    var error: String? by mutableStateOf(null)
        private set
    var saveState: SaveState by mutableStateOf(SaveState.Idle)
        private set
    fun save(input: DecisionInput, onSaved: (SaveOutcome) -> Unit) = viewModelScope.launch {
        if (saveState == SaveState.Saving) return@launch
        error = null
        saveState = SaveState.Saving
        repo.save(input)
            .onSuccess { outcome ->
                saveState = SaveState.Success(outcome.reminderWarning)
                onSaved(outcome)
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
    val now = minuteClock().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), System.currentTimeMillis())
    var reminderRetrying by mutableStateOf(false)
        private set
    var reminderError: String? by mutableStateOf(null)
        private set
    var deleting by mutableStateOf(false)
        private set
    var deleteError: String? by mutableStateOf(null)
        private set
    fun decision(id: Long) = repo.observe(id)
    fun choices(id: Long) = repo.choices(id)
    fun reviews(id: Long) = repo.reviews(id)
    fun delete(id: Long, done: () -> Unit) = viewModelScope.launch {
        deleting = true
        deleteError = null
        try {
            repo.delete(id)
            done()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            deleteError = error.message ?: "删除失败，请稍后重试"
        }
        deleting = false
    }
    fun retryReminder(id: Long, done: () -> Unit = {}) = viewModelScope.launch {
        reminderRetrying = true
        reminderError = null
        repo.retryReminder(id)
            .onSuccess { done() }
            .onFailure { reminderError = it.message ?: "提醒安排失败" }
        reminderRetrying = false
    }
}

@HiltViewModel
class ReviewViewModel @Inject constructor(private val repo: DecisionRepository) : ViewModel() {
    var error: String? by mutableStateOf(null)
        private set
    var saveState: SaveState by mutableStateOf(SaveState.Idle)
        private set
    fun decision(id: Long) = repo.observe(id)
    fun choices(id: Long) = repo.choices(id)
    fun save(input: ReviewInput, done: (SaveOutcome) -> Unit) = viewModelScope.launch {
        if (saveState == SaveState.Saving) return@launch
        error = null
        saveState = SaveState.Saving
        repo.review(input)
            .onSuccess { outcome ->
                saveState = SaveState.Success(outcome.reminderWarning)
                done(outcome)
            }
            .onFailure {
                val message = it.message ?: "保存失败，请稍后重试"
                error = message
                saveState = SaveState.Error(message)
            }
    }
}
