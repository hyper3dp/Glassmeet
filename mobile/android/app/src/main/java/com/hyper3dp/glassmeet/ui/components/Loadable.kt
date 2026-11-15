package com.hyper3dp.glassmeet.ui.components

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hyper3dp.glassmeet.data.friendly
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What a page shows: loading the first time, its data, or an error; refreshing on pull. */
data class Loadable<T>(
    val data: T? = null,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
)

/** A ViewModel that loads one thing with [fetch] and exposes it as [state]. */
abstract class LoadingViewModel<T> : ViewModel() {
    private val _state = MutableStateFlow(Loadable<T>())
    val state: StateFlow<Loadable<T>> = _state.asStateFlow()
    private var job: Job? = null

    protected abstract suspend fun fetch(): T

    /** First load (spinner), or a pull to refresh when [refreshing]. */
    fun load(refreshing: Boolean = false) {
        job?.cancel()
        _state.update { it.copy(loading = it.data == null && !refreshing, refreshing = refreshing, error = null) }
        job = viewModelScope.launch {
            try {
                val data = fetch()
                _state.update { Loadable(data = data, loading = false, refreshing = false, error = null) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                val message = e.friendly("This didn't load. Check your connection.")
                if (_state.value.data != null) com.hyper3dp.glassmeet.util.Snack.show(message)
                _state.update { it.copy(loading = false, refreshing = false, error = message) }
            }
        }
    }

    fun refresh() = load(refreshing = true)

    /** Quiet reload after a change elsewhere; keeps what's on screen meanwhile. */
    fun reloadQuietly() {
        viewModelScope.launch {
            runCatching { fetch() }.onSuccess { data -> _state.update { it.copy(data = data, error = null) } }
        }
    }

    protected fun setData(transform: (T) -> T) {
        _state.update { s -> s.data?.let { s.copy(data = transform(it)) } ?: s }
    }
}
