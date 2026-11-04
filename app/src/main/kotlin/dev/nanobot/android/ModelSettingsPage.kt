package dev.nanobot.android

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.nanobot.protocol.GatewayModelPreset
import dev.nanobot.protocol.GatewaySettings

private const val DEFAULT_PRESET = "default"

/** Test hook for the preset-name field shared by the create and rename dialogs. */
internal const val MODEL_PRESET_NAME_TAG = "modelPresetName"

/**
 * Settings mutations the model and gateway pages can issue. Every action defaults to a no-op so
 * previews and existing UI tests do not have to wire the full set.
 */
internal class GatewaySettingsActions(
    val updateAgent: (String?, String?, String?, Int?, String?) -> Unit =
        { _, _, _, _, _ -> },
    val saveModelConfiguration:
        (String, String, String, Int?, Int?, Double?, String?, String?) -> Unit =
        { _, _, _, _, _, _, _, _ -> },
    val createModelConfiguration:
        (String, String, String, Int?, Int?, Double?, String?) -> Unit =
        { _, _, _, _, _, _, _ -> },
    val renameModelConfiguration: (String, String) -> Unit = { _, _ -> },
    val deleteModelConfiguration: (String) -> Unit = {},
    val setActiveModelPreset: (String) -> Unit = {},
    val setModelPresetEnabled: (String, Boolean) -> Unit = { _, _ -> },
    val setUnifiedSession: (Boolean) -> Unit = {},
)

@Composable
internal fun ModelPage(
    state: UiState,
    strings: AppStrings,
    onRefresh: () -> Unit,
    actions: GatewaySettingsActions,
) {
    SettingsInfoPage(state, strings, strings.model, onRefresh) { settings ->
        item { ModelEditor(settings, state.settingsSaving, strings, actions) }
        item { ModelPresetList(settings, state.settingsSaving, strings, actions) }
    }
}

@Composable
private fun ModelEditor(
    settings: GatewaySettings,
    saving: Boolean,
    strings: AppStrings,
    actions: GatewaySettingsActions,
) {
    val presets = settings.modelPresets.filter { it.name.isNotBlank() }
    val choices = presets.ifEmpty { listOf(defaultPreset(settings)) }
    val activeName = settings.agent.modelPreset?.takeIf { it.isNotBlank() } ?: DEFAULT_PRESET
    var selectedName by rememberSaveable { mutableStateOf(activeName) }
    // `rememberSaveable` only seeds the selection once. The preset list's "make active" action
    // (and a model switch from another client) changes the server-side active preset, so follow
    // it here; otherwise the editor above keeps showing — and saving — the previous preset while
    // the success banner claims the switch worked.
    LaunchedEffect(activeName) { selectedName = activeName }
    val selected = choices.firstOrNull { it.name == selectedName }
        ?: choices.firstOrNull { it.name == activeName }
        ?: choices.first()

    var model by rememberSaveable { mutableStateOf(selected.model) }
    var provider by rememberSaveable { mutableStateOf(selected.provider) }
    var maxTokens by rememberSaveable { mutableStateOf("") }
    var contextWindow by rememberSaveable { mutableStateOf("") }
    var temperature by rememberSaveable { mutableStateOf("") }
    var reasoning by rememberSaveable { mutableStateOf("") }
    var timezone by rememberSaveable { mutableStateOf(settings.agent.timezone) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(selectedName, settings) {
        model = selected.model
        provider = selected.provider
        maxTokens = selected.maxTokens.takeIf { it > 0 }?.toString().orEmpty()
        contextWindow = selected.contextWindowTokens.takeIf { it > 0 }?.toString().orEmpty()
        temperature = selected.temperature.toString()
        reasoning = selected.reasoningEffort.orEmpty()
        timezone = settings.agent.timezone
        error = null
    }

    val isDefault = selected.name == DEFAULT_PRESET
    val reasoningOptions = selected.reasoningEffortValues

    fun save() {
        val trimmedModel = model.trim()
        if (trimmedModel.isEmpty()) {
            error = strings.modelRequired
            return
        }
        val trimmedProvider = provider.trim()
        if (trimmedProvider.isEmpty()) {
            error = strings.providerRequired
            return
        }
        val maxValue = parsePositive(maxTokens)
        if (maxTokens.isNotBlank() && maxValue == null) {
            error = strings.invalidNumber
            return
        }
        val contextValue = parsePositive(contextWindow)
        if (contextWindow.isNotBlank() && contextValue == null) {
            error = strings.invalidNumber
            return
        }
        val temperatureValue = temperature.trim().toDoubleOrNull()
        if (temperature.isNotBlank() &&
            (temperatureValue == null || temperatureValue < 0.0 || temperatureValue > 2.0)
        ) {
            error = strings.invalidTemperature
            return
        }
        val trimmedTimezone = timezone.trim()
        error = null
        if (isDefault) {
            actions.updateAgent(
                selected.name,
                trimmedModel,
                trimmedProvider,
                contextValue,
                trimmedTimezone.takeIf { it.isNotEmpty() },
            )
        } else {
            actions.saveModelConfiguration(
                selected.name,
                trimmedModel,
                trimmedProvider,
                maxValue,
                contextValue,
                temperatureValue,
                reasoning,
                trimmedTimezone.takeIf { it.isNotEmpty() },
            )
        }
    }

    SettingsSection(strings.currentModel) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            DropdownField(
                label = strings.selectPreset,
                value = presetLabel(selected, strings),
                options = choices.map { it.name to presetLabel(it, strings) },
                enabled = !saving,
                onSelect = { selectedName = it },
            )
            OutlinedTextField(
                value = model,
                onValueChange = { model = it },
                label = { Text(strings.model) },
                singleLine = true,
                enabled = !saving,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = provider,
                onValueChange = { provider = it },
                label = { Text(strings.provider) },
                singleLine = true,
                enabled = !saving,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = maxTokens,
                onValueChange = { maxTokens = it },
                label = { Text(strings.maxOutput) },
                singleLine = true,
                enabled = !saving && !isDefault,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = contextWindow,
                onValueChange = { contextWindow = it },
                label = { Text(strings.contextWindow) },
                singleLine = true,
                enabled = !saving,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = temperature,
                onValueChange = { temperature = it },
                label = { Text(strings.temperature) },
                singleLine = true,
                enabled = !saving && !isDefault,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            if (reasoningOptions.isEmpty()) {
                OutlinedTextField(
                    value = reasoning,
                    onValueChange = { reasoning = it },
                    label = { Text(strings.reasoningEffort) },
                    singleLine = true,
                    enabled = !saving && !isDefault,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                DropdownField(
                    label = strings.reasoningEffort,
                    value = reasoning.ifBlank { strings.reasoningDefault },
                    options = listOf("" to strings.reasoningDefault) +
                        reasoningOptions.map { it to it },
                    enabled = !saving && !isDefault,
                    onSelect = { reasoning = it },
                )
            }
            OutlinedTextField(
                value = timezone,
                onValueChange = { timezone = it },
                label = { Text(strings.timezone) },
                singleLine = true,
                enabled = !saving,
                modifier = Modifier.fillMaxWidth(),
            )
            if (isDefault) {
                Text(
                    strings.defaultPresetHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            error?.let { message ->
                Text(
                    message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { save() }, enabled = !saving) {
                    if (saving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(strings.save)
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelPresetList(
    settings: GatewaySettings,
    saving: Boolean,
    strings: AppStrings,
    actions: GatewaySettingsActions,
) {
    val presets = settings.modelPresets.filter { it.name.isNotBlank() }
    var createOpen by rememberSaveable { mutableStateOf(false) }
    var renameTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteTarget by rememberSaveable { mutableStateOf<String?>(null) }

    SettingsSection(strings.modelPreset) {
        presets.forEachIndexed { index, preset ->
            PresetRow(
                preset = preset,
                settings = settings,
                saving = saving,
                strings = strings,
                actions = actions,
                onRename = { renameTarget = preset.name },
                onDelete = { deleteTarget = preset.name },
            )
            if (index != presets.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.padding(start = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                )
            }
        }
        TextButton(
            onClick = { createOpen = true },
            enabled = !saving,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(strings.newPreset)
        }
    }

    if (createOpen) {
        val base = presets.firstOrNull { it.active } ?: presets.firstOrNull()
        PresetCreateDialog(
            base = base,
            strings = strings,
            saving = saving,
            onDismiss = { createOpen = false },
            onConfirm = { name, model, provider, maxTokens, contextWindow, temperature, reasoning ->
                actions.createModelConfiguration(
                    name,
                    model,
                    provider,
                    maxTokens,
                    contextWindow,
                    temperature,
                    reasoning,
                )
                createOpen = false
            },
        )
    }

    renameTarget?.let { name ->
        RenamePresetDialog(
            currentName = name,
            strings = strings,
            saving = saving,
            onDismiss = { renameTarget = null },
            onConfirm = { newName ->
                actions.renameModelConfiguration(name, newName)
                renameTarget = null
            },
        )
    }

    deleteTarget?.let { name ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(strings.delete) },
            text = { Text(strings.deletePresetConfirmation(name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        actions.deleteModelConfiguration(name)
                        deleteTarget = null
                    },
                    enabled = !saving,
                ) { Text(strings.delete) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text(strings.cancel) }
            },
        )
    }
}

@Composable
private fun PresetRow(
    preset: GatewayModelPreset,
    settings: GatewaySettings,
    saving: Boolean,
    strings: AppStrings,
    actions: GatewaySettingsActions,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val active = preset.active || preset.name == settings.agent.modelPreset
    val enabled = if (settings.modelCallOrder.isEmpty()) {
        active
    } else {
        preset.name in settings.modelCallOrder
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        presetLabel(preset, strings),
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (active) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            strings.activePreset,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Text(
                    "${preset.model} · ${preset.provider}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (preset.name != DEFAULT_PRESET) {
                Switch(
                    checked = enabled,
                    onCheckedChange = { actions.setModelPresetEnabled(preset.name, it) },
                    enabled = !saving && settings.modelCallOrderEditable,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (!active) {
                TextButton(
                    onClick = { actions.setActiveModelPreset(preset.name) },
                    enabled = !saving,
                ) { Text(strings.makeActive) }
            }
            if (preset.name != DEFAULT_PRESET) {
                TextButton(onClick = onRename, enabled = !saving) { Text(strings.rename) }
                TextButton(onClick = onDelete, enabled = !saving) { Text(strings.delete) }
            }
        }
    }
}

@Composable
private fun PresetCreateDialog(
    base: GatewayModelPreset?,
    strings: AppStrings,
    saving: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, Int?, Int?, Double?, String?) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var model by remember { mutableStateOf(base?.model.orEmpty()) }
    var provider by remember { mutableStateOf(base?.provider.orEmpty()) }
    var maxTokens by remember {
        mutableStateOf(base?.maxTokens?.takeIf { it > 0 }?.toString().orEmpty())
    }
    var contextWindow by remember {
        mutableStateOf(base?.contextWindowTokens?.takeIf { it > 0 }?.toString().orEmpty())
    }
    var temperature by remember { mutableStateOf(base?.temperature?.toString().orEmpty()) }
    var reasoning by remember { mutableStateOf(base?.reasoningEffort.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    val reasoningOptions = base?.reasoningEffortValues.orEmpty()

    fun confirm() {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) {
            error = strings.presetNameRequired
            return
        }
        val trimmedModel = model.trim()
        if (trimmedModel.isEmpty()) {
            error = strings.modelRequired
            return
        }
        val trimmedProvider = provider.trim()
        if (trimmedProvider.isEmpty()) {
            error = strings.providerRequired
            return
        }
        val maxValue = parsePositive(maxTokens)
        if (maxTokens.isNotBlank() && maxValue == null) {
            error = strings.invalidNumber
            return
        }
        val contextValue = parsePositive(contextWindow)
        if (contextWindow.isNotBlank() && contextValue == null) {
            error = strings.invalidNumber
            return
        }
        val temperatureValue = temperature.trim().toDoubleOrNull()
        if (temperature.isNotBlank() &&
            (temperatureValue == null || temperatureValue < 0.0 || temperatureValue > 2.0)
        ) {
            error = strings.invalidTemperature
            return
        }
        onConfirm(
            trimmedName,
            trimmedModel,
            trimmedProvider,
            maxValue,
            contextValue,
            temperatureValue,
            reasoning,
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.newPreset) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(strings.presetName) },
                    singleLine = true,
                    enabled = !saving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(MODEL_PRESET_NAME_TAG),
                )
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text(strings.model) },
                    singleLine = true,
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = provider,
                    onValueChange = { provider = it },
                    label = { Text(strings.provider) },
                    singleLine = true,
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = maxTokens,
                    onValueChange = { maxTokens = it },
                    label = { Text(strings.maxOutput) },
                    singleLine = true,
                    enabled = !saving,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = contextWindow,
                    onValueChange = { contextWindow = it },
                    label = { Text(strings.contextWindow) },
                    singleLine = true,
                    enabled = !saving,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = temperature,
                    onValueChange = { temperature = it },
                    label = { Text(strings.temperature) },
                    singleLine = true,
                    enabled = !saving,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (reasoningOptions.isEmpty()) {
                    OutlinedTextField(
                        value = reasoning,
                        onValueChange = { reasoning = it },
                        label = { Text(strings.reasoningEffort) },
                        singleLine = true,
                        enabled = !saving,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    DropdownField(
                        label = strings.reasoningEffort,
                        value = reasoning.ifBlank { strings.reasoningDefault },
                        options = listOf("" to strings.reasoningDefault) +
                            reasoningOptions.map { it to it },
                        enabled = !saving,
                        onSelect = { reasoning = it },
                    )
                }
                error?.let { message ->
                    Text(
                        message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { confirm() }, enabled = !saving) { Text(strings.save) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(strings.cancel) }
        },
    )
}

@Composable
private fun RenamePresetDialog(
    currentName: String,
    strings: AppStrings,
    saving: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(currentName) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.rename) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(strings.presetName) },
                    singleLine = true,
                    enabled = !saving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(MODEL_PRESET_NAME_TAG),
                )
                error?.let { message ->
                    Text(
                        message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val trimmed = name.trim()
                    if (trimmed.isEmpty()) {
                        error = strings.presetNameRequired
                    } else {
                        onConfirm(trimmed)
                    }
                },
                enabled = !saving,
            ) { Text(strings.save) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(strings.cancel) }
        },
    )
}

@Composable
private fun DropdownField(
    label: String,
    value: String,
    options: List<Pair<String, String>>,
    enabled: Boolean,
    onSelect: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
        )
        if (enabled) {
            Box(
                Modifier
                    .matchParentSize()
                    .clickable { open = true },
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (key, text) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = {
                        open = false
                        onSelect(key)
                    },
                )
            }
        }
    }
}

private fun presetLabel(preset: GatewayModelPreset, strings: AppStrings): String =
    if (preset.isDefault || preset.name == DEFAULT_PRESET) strings.defaultPreset else preset.name

private fun defaultPreset(settings: GatewaySettings): GatewayModelPreset {
    val agent = settings.agent
    return GatewayModelPreset(
        name = DEFAULT_PRESET,
        label = "Default",
        active = agent.modelPreset.isNullOrBlank() || agent.modelPreset == DEFAULT_PRESET,
        isDefault = true,
        model = agent.model,
        provider = agent.provider,
        resolvedProvider = agent.resolvedProvider,
        maxTokens = agent.maxTokens,
        contextWindowTokens = agent.contextWindowTokens,
        temperature = agent.temperature,
        reasoningEffort = agent.reasoningEffort,
    )
}

private fun parsePositive(value: String): Int? =
    value.trim().toIntOrNull()?.takeIf { it > 0 }
