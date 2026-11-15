package com.hyper3dp.glassmeet.ui.scheduling

import androidx.lifecycle.viewModelScope
import com.hyper3dp.glassmeet.data.EventTemplate
import com.hyper3dp.glassmeet.data.EventType
import com.hyper3dp.glassmeet.data.EventTypesRepository
import com.hyper3dp.glassmeet.data.friendly
import com.hyper3dp.glassmeet.ui.components.LoadingViewModel
import com.hyper3dp.glassmeet.util.Snack
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

data class SchedulingData(val eventTypes: List<EventType>, val templates: List<EventTemplate>)

class SchedulingViewModel : LoadingViewModel<SchedulingData>() {
    init {
        load()
        viewModelScope.launch { EventTypesRepository.version.drop(1).collect { reloadQuietly() } }
    }

    override suspend fun fetch(): SchedulingData {
        val (events, templates) = com.hyper3dp.glassmeet.data.BookingsRepository.both(
            { EventTypesRepository.list() }, { EventTypesRepository.templates() },
        )
        return SchedulingData(events, templates)
    }

    fun setActive(e: EventType, active: Boolean) {
        setData { d -> d.copy(eventTypes = d.eventTypes.map { if (it.id == e.id) it.copy(active = active) else it }) }
        viewModelScope.launch {
            try {
                EventTypesRepository.setActive(e.id, active)
            } catch (ex: Exception) {
                setData { d -> d.copy(eventTypes = d.eventTypes.map { if (it.id == e.id) it.copy(active = !active) else it }) }
                Snack.show("Could not turn ${if (active) "on" else "off"} ${e.name}. ${ex.friendly("")}".trim())
            }
        }
    }

    fun delete(e: EventType) {
        viewModelScope.launch {
            try {
                EventTypesRepository.delete(e.id)
                setData { d -> d.copy(eventTypes = d.eventTypes.filterNot { it.id == e.id }) }
                Snack.show("Deleted ${e.name}")
            } catch (ex: Exception) {
                Snack.show("Could not delete this event type. ${ex.friendly("")}".trim())
            }
        }
    }

    fun deleteTemplate(t: EventTemplate) {
        viewModelScope.launch {
            try {
                EventTypesRepository.deleteTemplate(t.id)
                setData { d -> d.copy(templates = d.templates.filterNot { it.id == t.id }) }
            } catch (ex: Exception) {
                Snack.show("Could not delete this template. ${ex.friendly("")}".trim())
            }
        }
    }
}
