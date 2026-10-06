package at.zocks.zleep.ui.massage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import at.zocks.zleep.R
import at.zocks.zleep.domain.massage.BuiltInMassagePrograms
import at.zocks.zleep.domain.massage.MassageController
import at.zocks.zleep.domain.massage.MassageProgramType
import at.zocks.zleep.domain.massage.MassageTempo
import at.zocks.zleep.domain.model.MassageZone
import at.zocks.zleep.ui.components.BigActionButton
import at.zocks.zleep.ui.components.BigSegmentedChoice
import at.zocks.zleep.ui.components.BigStepper
import at.zocks.zleep.ui.components.LoadingState
import at.zocks.zleep.ui.components.SideChoice
import at.zocks.zleep.ui.components.UserMessageEffect
import at.zocks.zleep.ui.components.ZocksCard
import at.zocks.zleep.ui.format.formatDuration
import at.zocks.zleep.ui.format.programDescription
import at.zocks.zleep.ui.format.programName
import at.zocks.zleep.ui.format.programTypeLabel
import at.zocks.zleep.ui.format.tempoLabel
import at.zocks.zleep.ui.format.zoneLabel
import at.zocks.zleep.ui.heat.Labeled
import at.zocks.zleep.ui.theme.Dimens
import at.zocks.zleep.ui.theme.ZocksTheme
import at.zocks.zleep.ui.theme.ZocksThemeExt
import java.time.Duration

@Composable
fun MassageRoute(viewModel: MassageViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    UserMessageEffect(state.userMessage) { viewModel.onEvent(MassageEvent.MessageShown) }
    MassagePanel(state, viewModel::onEvent)
}

@Composable
fun MassagePanel(state: MassageUiState, onEvent: (MassageEvent) -> Unit, modifier: Modifier = Modifier) {
    if (state.loading) {
        LoadingState(modifier)
        return
    }
    Column(modifier.testTag("massage_panel"), verticalArrangement = Arrangement.spacedBy(Dimens.SpaceL)) {
        MassageStatusCard(state, onEvent)
        ProgramList(state, onEvent)
        MassageSettingsCard(state, onEvent)
    }
    if (state.editorOpen) {
        CustomProgramDialog(
            onDismiss = { onEvent(MassageEvent.CloseEditor) },
            onSave = { name, type, tempo, zones -> onEvent(MassageEvent.SaveCustom(name, type, tempo, zones)) },
        )
    }
}

@Composable
private fun MassageStatusCard(state: MassageUiState, onEvent: (MassageEvent) -> Unit) {
    val colors = ZocksThemeExt.colors
    val session = state.session
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (session != null) colors.massageContainer else MaterialTheme.colorScheme.surfaceContainer,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("massage_status"),
    ) {
        Column(Modifier.padding(Dimens.CardPadding), verticalArrangement = Arrangement.spacedBy(Dimens.SpaceM)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Spa,
                    contentDescription = null,
                    tint = if (session != null) colors.massage else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(32.dp),
                )
                Text(
                    text = when {
                        session == null -> stringResource(R.string.massage_idle)
                        session.fadingOut -> stringResource(R.string.massage_fading, programName(session.program))
                        else -> stringResource(R.string.massage_running, programName(session.program), formatDuration(state.remaining!!))
                    },
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = Dimens.SpaceM),
                )
            }
            if (session != null && !session.fadingOut) {
                BigActionButton(
                    text = stringResource(R.string.massage_stop),
                    icon = Icons.Filled.Spa,
                    onClick = { onEvent(MassageEvent.Stop) },
                    containerColor = colors.massage,
                    contentColor = colors.onMassage,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("massage_stop"),
                )
            }
        }
    }
}

@Composable
private fun ProgramList(state: MassageUiState, onEvent: (MassageEvent) -> Unit) {
    val colors = ZocksThemeExt.colors
    ZocksCard(title = stringResource(R.string.massage_programs)) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXs)) {
            state.programs.forEach { program ->
                val selected = program.id == state.selected?.id
                val name = programName(program)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = Dimens.ThumbTarget)
                        .background(
                            if (selected) colors.massageContainer else MaterialTheme.colorScheme.surfaceContainer,
                            MaterialTheme.shapes.medium,
                        )
                        .selectable(selected = selected, role = Role.RadioButton, onClick = { onEvent(MassageEvent.Select(program.id)) })
                        .padding(start = Dimens.SpaceL)
                        .testTag("program_${program.id}"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
                        Text(name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            programDescription(program),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (!program.isBuiltIn) {
                        IconButton(onClick = { onEvent(MassageEvent.DeleteCustom(program.id)) }, modifier = Modifier.size(56.dp)) {
                            Icon(Icons.Outlined.DeleteOutline, contentDescription = stringResource(R.string.massage_delete_custom, name))
                        }
                    }
                    val favorite = state.isFavorite(program)
                    IconButton(
                        onClick = { onEvent(MassageEvent.ToggleFavorite(program.id)) },
                        modifier = Modifier
                            .size(56.dp)
                            .testTag("favorite_${program.id}"),
                    ) {
                        Icon(
                            if (favorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                            contentDescription = stringResource(
                                if (favorite) R.string.massage_favorite_remove else R.string.massage_favorite_add,
                                name,
                            ),
                            tint = if (favorite) colors.massage else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MassageSettingsCard(state: MassageUiState, onEvent: (MassageEvent) -> Unit) {
    val colors = ZocksThemeExt.colors
    val prefs = state.prefs
    ZocksCard {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceL)) {
            Labeled(stringResource(R.string.massage_intensity)) {
                BigStepper(
                    value = stringResource(R.string.value_percent, prefs.intensity),
                    decreaseLabel = stringResource(R.string.massage_intensity_decrease),
                    increaseLabel = stringResource(R.string.massage_intensity_increase),
                    onDecrease = { onEvent(MassageEvent.ChangeIntensity(-1)) },
                    onIncrease = { onEvent(MassageEvent.ChangeIntensity(+1)) },
                    canDecrease = prefs.intensity > MassageController.MIN_INTENSITY,
                    canIncrease = prefs.intensity < 100,
                    color = colors.massage,
                    testTag = "massage_intensity",
                )
            }
            Labeled(stringResource(R.string.massage_duration)) {
                BigSegmentedChoice(
                    options = MassageUiState.DURATION_OPTIONS,
                    selected = prefs.durationMinutes,
                    label = { it.toString() },
                    onSelect = { onEvent(MassageEvent.SetDuration(it)) },
                    activeColor = colors.massageContainer,
                    testTagPrefix = "massage_duration",
                )
                Text(
                    formatDuration(Duration.ofMinutes(prefs.durationMinutes.toLong())),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Labeled(stringResource(R.string.control_side)) {
                SideChoice(prefs.side, { onEvent(MassageEvent.SetSide(it)) }, activeColor = colors.massageContainer)
            }
            if (state.session != null) {
                // Läuft schon: neues Programm oder neue Dauer per Neustart übernehmen (Intensität wirkt sofort).
                OutlinedButton(
                    onClick = { onEvent(MassageEvent.Start) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = Dimens.ThumbTarget)
                        .testTag("massage_apply"),
                ) {
                    Text(stringResource(R.string.control_apply), style = MaterialTheme.typography.labelLarge)
                }
            } else {
                BigActionButton(
                    text = stringResource(R.string.massage_start),
                    icon = Icons.Filled.Spa,
                    onClick = { onEvent(MassageEvent.Start) },
                    containerColor = colors.massage,
                    contentColor = colors.onMassage,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("massage_start"),
                )
            }
            OutlinedButton(
                onClick = { onEvent(MassageEvent.OpenEditor) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Dimens.ThumbTarget)
                    .testTag("massage_open_editor"),
            ) {
                Text(stringResource(R.string.massage_save_custom), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun CustomProgramDialog(
    onDismiss: () -> Unit,
    onSave: (String, MassageProgramType, MassageTempo, Set<MassageZone>) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf(MassageProgramType.WAVE) }
    var tempo by rememberSaveable { mutableStateOf(MassageTempo.MEDIUM) }
    var zones by rememberSaveable { mutableStateOf(MassageZone.entries.toSet()) }
    val valid = name.isNotBlank() && zones.isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.massage_custom_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceM)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(MAX_NAME_LENGTH) },
                    label = { Text(stringResource(R.string.massage_custom_name)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_name"),
                )
                Labeled(stringResource(R.string.massage_custom_type)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
                        MassageProgramType.entries.forEach { option ->
                            FilterChip(selected = type == option, onClick = { type = option }, label = { Text(programTypeLabel(option)) })
                        }
                    }
                }
                Labeled(stringResource(R.string.massage_custom_tempo)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
                        MassageTempo.entries.forEach { option ->
                            FilterChip(selected = tempo == option, onClick = { tempo = option }, label = { Text(tempoLabel(option)) })
                        }
                    }
                }
                Labeled(stringResource(R.string.massage_custom_zones)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
                        MassageZone.entries.forEach { zone ->
                            FilterChip(
                                selected = zone in zones,
                                onClick = { zones = if (zone in zones) zones - zone else zones + zone },
                                label = { Text(zoneLabel(zone)) },
                            )
                        }
                    }
                }
                Text(
                    stringResource(R.string.massage_custom_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name.trim(), type, tempo, zones) },
                enabled = valid,
                modifier = Modifier.testTag("custom_save"),
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

private const val MAX_NAME_LENGTH = 30

@Preview
@Composable
private fun MassagePanelPreview() {
    ZocksTheme {
        MassagePanel(
            MassageUiState(
                loading = false,
                programs = BuiltInMassagePrograms.all,
            ),
            {},
        )
    }
}
