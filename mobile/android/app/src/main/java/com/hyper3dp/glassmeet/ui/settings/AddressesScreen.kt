package com.hyper3dp.glassmeet.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hyper3dp.glassmeet.data.EventTypesRepository
import com.hyper3dp.glassmeet.data.SavedAddress
import com.hyper3dp.glassmeet.data.friendly
import com.hyper3dp.glassmeet.ui.components.ConfirmDialog
import com.hyper3dp.glassmeet.ui.components.DetailScaffold
import com.hyper3dp.glassmeet.ui.components.EmptyState
import com.hyper3dp.glassmeet.ui.components.ErrorText
import com.hyper3dp.glassmeet.ui.components.LoadableContent
import com.hyper3dp.glassmeet.ui.components.LoadingViewModel
import com.hyper3dp.glassmeet.ui.components.SectionCard
import com.hyper3dp.glassmeet.util.Snack
import kotlinx.coroutines.launch

/** Saved addresses for in-person event types (addresses table; web: settings.jsx Addresses). */
class AddressesViewModel : LoadingViewModel<List<SavedAddress>>() {
    init { load() }

    override suspend fun fetch(): List<SavedAddress> = EventTypesRepository.addresses()

    fun add(label: String, address: String, onDone: (String?) -> Unit) {
        if (address.isBlank()) { onDone("Enter the address."); return }
        viewModelScope.launch {
            try {
                val saved = EventTypesRepository.addAddress(label.take(40), address.take(300))
                setData { it + saved }
                onDone(null)
            } catch (e: Exception) {
                onDone(e.friendly("Could not save the address."))
            }
        }
    }

    fun delete(a: SavedAddress) {
        setData { list -> list.filterNot { it.id == a.id } }
        viewModelScope.launch {
            try {
                EventTypesRepository.deleteAddress(a.id)
            } catch (e: Exception) {
                Snack.show("Could not delete the address. ${e.friendly("")}".trim())
                reloadQuietly()
            }
        }
    }
}

@Composable
fun AddressesScreen(onBack: () -> Unit) {
    val vm: AddressesViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<SavedAddress?>(null) }

    DetailScaffold(
        title = "Addresses", onBack = onBack,
        actions = { IconButton(onClick = { adding = true }) { Icon(Icons.Filled.Add, contentDescription = "Add an address") } },
    ) { inner ->
        LoadableContent(state, onRetry = { vm.load() }, onRefresh = vm::refresh, modifier = Modifier.padding(inner)) { list ->
            if (list.isEmpty()) {
                EmptyState(
                    Icons.Outlined.Place, "No saved addresses",
                    "Save the places you meet in person, then pick them in an event type.",
                    actionLabel = "Add an address", actionIcon = Icons.Filled.Add, onAction = { adding = true },
                )
            } else {
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
                    items(list, key = { it.id }) { a ->
                        SectionCard(padding = 0.dp) {
                            Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.Place, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Column(Modifier.weight(1f).padding(horizontal = 16.dp, vertical = 10.dp)) {
                                    if (!a.label.isNullOrBlank()) Text(a.label, style = MaterialTheme.typography.titleMedium)
                                    Text(a.address, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton(onClick = { deleting = a }) { Icon(Icons.Outlined.Delete, contentDescription = "Delete address") }
                            }
                        }
                    }
                }
            }
        }
    }

    if (adding) AddAddressDialog(vm, onDismiss = { adding = false })
    deleting?.let { a ->
        ConfirmDialog(
            "Delete this address?", "Event types that use it keep their address.", "Delete",
            onConfirm = { deleting = null; vm.delete(a) }, onDismiss = { deleting = null }, dismissLabel = "Cancel",
        )
    }
}

@Composable
private fun AddAddressDialog(vm: AddressesViewModel, onDismiss: () -> Unit) {
    var label by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New address") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(label, { label = it.take(40) }, label = { Text("Label (optional)") }, placeholder = { Text("Office") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(address, { address = it.take(300); error = null }, label = { Text("Address") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                ErrorText(error)
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = {
                busy = true
                vm.add(label, address) { problem ->
                    busy = false
                    if (problem == null) { onDismiss() } else { error = problem }
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
