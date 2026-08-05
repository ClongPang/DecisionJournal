package com.example.decisionjournal.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.decisionjournal.data.DecisionInput
import com.example.decisionjournal.data.DecisionValidation
import com.example.decisionjournal.data.DecisionRepository
import com.example.decisionjournal.data.ReviewInput
import com.example.decisionjournal.data.SaveOutcome
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
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

sealed interface DecisionEditorState {
    data object Loading : DecisionEditorState
    data object Missing : DecisionEditorState
    data class Content(val data: com.example.decisionjournal.data.DecisionEditorData) : DecisionEditorState
}

sealed interface DecisionLoadState {
    data object Loading : DecisionLoadState
    data object Missing : DecisionLoadState
    data class Content(val decision: com.example.decisionjournal.data.model.Decision) : DecisionLoadState
}

sealed interface DecisionListState {
    data object Loading : DecisionListState
    data object Empty : DecisionListState
    data class Content(val decisions: List<com.example.decisionjournal.data.model.Decision>) : DecisionListState
    data class Error(val message: String) : DecisionListState
}

private fun Flow<List<com.example.decisionjournal.data.model.Decision>>.asDecisionListState(): Flow<DecisionListState> =
    map< List<com.example.decisionjournal.data.model.Decision>, DecisionListState> { decisions ->
        if (decisions.isEmpty()) DecisionListState.Empty else DecisionListState.Content(decisions)
    }
        // Database and platform exceptions are implementation details. Keep them out of the
        // archive UI so a recoverable local read failure does not look like data corruption.
        .catch { emit(DecisionListState.Error("暂时无法读取本机记录，请稍后重试。")) }

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel @Inject constructor(repo: DecisionRepository) : ViewModel() {
    private val refresh = MutableStateFlow(0)
    // Keep exactly one Room subscription for the home archive. Previously `listState` and
    // `all` independently collected the same cold Flow, so a page resume could show Loading
    // while the other collector still held data. The state is now the single source of truth.
    private val decisionStates = refresh.flatMapLatest { repo.decisions.asDecisionListState() }
    val listState = decisionStates
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DecisionListState.Loading)
    private val clock = minuteClock().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), System.currentTimeMillis())
    val due = combine(refresh, clock) { _, now -> now }.flatMapLatest(repo::due)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun retry() { refresh.value += 1 }
}

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class DecisionsViewModel @Inject constructor(repo: DecisionRepository) : ViewModel() {
    private val refresh = MutableStateFlow(0)
    // Share one observed state with the archive, filters and statistics instead of opening
    // parallel Room collectors that can briefly disagree during a resume or a write.
    private val decisionStates = refresh.flatMapLatest { repo.decisions.asDecisionListState() }
    val listState = decisionStates
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DecisionListState.Loading)
    val decisions = listState.map { state ->
        (state as? DecisionListState.Content)?.decisions.orEmpty()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val searchFields = repo.searchFields.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val now = minuteClock().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), System.currentTimeMillis())
    val stats = combine(decisions, now) { items, currentTime -> calculateDecisionStats(items, currentTime) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DecisionStats(0, 0, null))
    private val filter = MutableStateFlow<DecisionFilter>(DecisionFilter.All)
    val selectedFilter = filter.asStateFlow()
    val periodCounts = combine(decisions, now)
        { items, currentTime -> calculatePeriodCounts(items, currentDate(currentTime), ZoneId.systemDefault()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PeriodCounts())
    val statusCounts = combine(decisions, now)
        { items, currentTime -> calculateDecisionStatusCounts(items, currentTime) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DecisionStatusCounts())
    val filteredDecisions = combine(decisions, filter, now) { items, currentFilter, currentTime ->
        filterDecisions(items, currentFilter, currentDate(currentTime), ZoneId.systemDefault(), currentTime)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun selectFilter(value: DecisionFilter) {
        filter.value = value
    }

    fun clearFilter() {
        filter.value = DecisionFilter.All
    }

    fun setFilter(value: DecisionFilter) {
        filter.value = value
    }

    fun retry() { refresh.value += 1 }
}

private fun currentDate(timestamp: Long): LocalDate =
    java.time.Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate()

@HiltViewModel
class CreateDecisionViewModel @Inject constructor(private val repo: DecisionRepository) : ViewModel() {
    var error: String? by mutableStateOf(null)
        private set
    var saveState: SaveState by mutableStateOf(SaveState.Idle)
        private set

    fun clearError() {
        error = null
        if (saveState is SaveState.Error) saveState = SaveState.Idle
    }

    /** Do not ask for optional notification permission when the form itself is invalid. */
    fun validateBeforePermissionRequest(input: DecisionInput): Boolean {
        val message = DecisionValidation.validate(input) ?: return true
        error = message
        saveState = SaveState.Error(message)
        return false
    }

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
        .map { data -> data?.let(DecisionEditorState::Content) ?: DecisionEditorState.Missing }
        .onStart { emit(DecisionEditorState.Loading) }
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
    fun decisionState(id: Long) = repo.observe(id)
        .map { decision -> decision?.let(DecisionLoadState::Content) ?: DecisionLoadState.Missing }
        .onStart { emit(DecisionLoadState.Loading) }
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

    fun clearError() {
        error = null
        if (saveState is SaveState.Error) saveState = SaveState.Idle
    }
    fun decisionState(id: Long) = repo.observe(id)
        .map { decision -> decision?.let(DecisionLoadState::Content) ?: DecisionLoadState.Missing }
        .onStart { emit(DecisionLoadState.Loading) }
    fun choices(id: Long) = repo.choices(id)
    fun reviews(id: Long) = repo.reviews(id)
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
