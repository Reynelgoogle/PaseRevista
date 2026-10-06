package com.reynelbusto.paserevista.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.reynelbusto.paserevista.core.IsoDate
import com.reynelbusto.paserevista.core.isoDateFromPickerMillis
import com.reynelbusto.paserevista.core.pickerMillisFromIso
import com.reynelbusto.paserevista.domain.model.ClinicalOptions
import com.reynelbusto.paserevista.domain.model.Sex
import com.reynelbusto.paserevista.presentation.theme.TextSecondary

/**
 * Desplegable con opción "Otro…" que revela un campo de texto libre.
 * - Si el valor guardado está en la lista, el desplegable lo muestra.
 * - Si no está (texto manual previo), muestra "Otro…" + el texto.
 * - La opción "—" vacía el campo (cero obligatorios).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OptionField(
    label: String,
    options: List<String>,
    value: String?,
    onValueChange: (String?) -> Unit,
    modifier: Modifier = Modifier,
    allowOther: Boolean = true,
) {
    val items = if (allowOther) ClinicalOptions.withOther(options) else options
    var expanded by remember { mutableStateOf(false) }
    val (initSelection, initOther) = remember(value) {
        ClinicalOptions.matchOrOther(value, items.filter { it != ClinicalOptions.OTHER })
    }
    var selection by remember(value) { mutableStateOf(initSelection) }
    var otherText by remember(value) { mutableStateOf(initOther) }

    fun emit() {
        onValueChange(ClinicalOptions.effectiveValue(selection, otherText))
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
        ) {
            OutlinedTextField(
                value = selection ?: "",
                onValueChange = {},
                readOnly = true,
                label = { Text(label) },
                placeholder = { Text("Opcional") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth(),
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text("—", color = TextSecondary) },
                    onClick = {
                        selection = null; otherText = ""; expanded = false; emit()
                    },
                )
                items.forEach { opt ->
                    DropdownMenuItem(
                        text = { Text(opt) },
                        onClick = { selection = opt; expanded = false; emit() },
                    )
                }
            }
        }
        if (selection == ClinicalOptions.OTHER) {
            OutlinedTextField(
                value = otherText,
                onValueChange = { otherText = it; emit() },
                label = { Text("Especifique") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }
    }
}

/** Selector de sexo M/F opcional: tocar el seleccionado lo desmarca. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SexSegmentedButton(
    selected: Sex?,
    onSelect: (Sex?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "Sexo (opcional)",
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = selected == Sex.M,
                onClick = { onSelect(if (selected == Sex.M) null else Sex.M) },
                label = { Text("M") },
            )
            FilterChip(
                selected = selected == Sex.F,
                onClick = { onSelect(if (selected == Sex.F) null else Sex.F) },
                label = { Text("F") },
            )
        }
    }
}

/** Campo de fecha con DatePickerDialog. Guarda ISO `yyyy-MM-dd`. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerField(
    label: String,
    isoDate: IsoDate?,
    onSelect: (IsoDate?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var show by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = isoDate ?: "",
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        placeholder = { Text("Opcional") },
        trailingIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isoDate != null) {
                    IconButton(onClick = { onSelect(null) }) {
                        Icon(Icons.Filled.Clear, contentDescription = "Limpiar fecha")
                    }
                }
                IconButton(onClick = { show = true }) {
                    Icon(Icons.Filled.DateRange, contentDescription = "Elegir fecha")
                }
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .clickable { show = true },
        singleLine = true,
    )
    if (show) {
        val initialMillis = remember(isoDate) {
            isoDate?.let { runCatching { pickerMillisFromIso(it) }.getOrNull() }
        }
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { show = false },
            confirmButton = {
                TextButton(onClick = {
                    onSelect(pickerState.selectedDateMillis?.let(::isoDateFromPickerMillis))
                    show = false
                }) { Text("Aceptar") }
            },
            dismissButton = {
                TextButton(onClick = { show = false }) { Text("Cancelar") }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}
