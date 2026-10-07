package at.zocks.zleep.ui.routine

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import at.zocks.zleep.R
import at.zocks.zleep.domain.massage.BuiltInMassagePrograms
import at.zocks.zleep.domain.routine.EveningRoutine
import at.zocks.zleep.domain.routine.RoutineHeat
import at.zocks.zleep.domain.routine.RoutineMassage
import at.zocks.zleep.domain.routine.RoutineStep
import at.zocks.zleep.ui.components.BigActionButton
import at.zocks.zleep.ui.components.BigStepper
import at.zocks.zleep.ui.components.Labeled
import at.zocks.zleep.ui.components.LoadingState
import at.zocks.zleep.ui.components.SwitchRow
import at.zocks.zleep.ui.components.UserMessageEffect
import at.zocks.zleep.ui.components.ZocksCard
import at.zocks.zleep.ui.format.currentLocale
import at.zocks.zleep.ui.format.formatDuration
import at.zocks.zleep.ui.format.formatTime
import at.zocks.zleep.ui.format.programName
import at.zocks.zleep.ui.theme.Dimens
import at.zocks.zleep.ui.theme.ZocksThemeExt
import java.time.Duration

@Composable
fun RoutineRoute(viewModel: RoutineViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    UserMessageEffect(state.userMessage) { viewModel.onEvent(RoutineEvent.MessageShown) }
    RoutinePanel(state, viewModel::onEvent)
}

/** Abendroutine: Schritte bearbeiten, starten und den Ablauf verfolgen. */
@Composable
fun RoutinePanel(state: RoutineUiState, onEvent: (RoutineEvent) -> Unit, modifier: Modifier = Modifier) {
    if (state.loading) {
        LoadingState(modifier)
        return
    }
    Column(modifier.testTag("control_routine_content"), verticalArrangement = Arrangement.spacedBy(Dimens.SpaceL)) {
        state.running?.let { RunningCard(state, onEvent) }

        ZocksCard(title = stringResource(R.string.routine_title)) {
            Text(stringResource(R.string.routine_body), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                stringResource(R.string.routine_total, formatDuration(Duration.ofMinutes(state.routine.totalMinutes.toLong()))),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .padding(top = Dimens.SpaceS)
                    .testTag("routine_total"),
            )
        }

        val editable = state.running == null
        state.routine.steps.forEachIndexed { index, step ->
            StepCard(index, step, state, editable, onEvent)
        }
        if (editable && state.routine.steps.size < EveningRoutine.MAX_STEPS) {
            OutlinedButton(
                onClick = { onEvent(RoutineEvent.AddStep) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Dimens.ThumbTarget)
                    .testTag("routine_add_step"),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text(stringResource(R.string.routine_add_step), modifier = Modifier.padding(start = Dimens.SpaceS))
            }
        }

        ZocksCard {
            SwitchRow(
                title = stringResource(R.string.routine_end_when_asleep),
                body = stringResource(R.string.routine_end_when_asleep_body),
                checked = state.routine.endWhenAsleep,
                onCheckedChange = { onEvent(RoutineEvent.SetEndWhenAsleep(it)) },
                testTag = "routine_end_when_asleep",
            )
            SwitchRow(
                title = stringResource(R.string.routine_with_night),
                body = null,
                checked = state.withNight,
                onCheckedChange = { onEvent(RoutineEvent.SetWithNight(it)) },
                testTag = "routine_with_night",
            )
            Text(
                stringResource(R.string.routine_safety),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Dimens.SpaceS),
            )
            if (editable) {
                TextButton(onClick = { onEvent(RoutineEvent.Reset) }, modifier = Modifier.testTag("routine_reset")) {
                    Text(stringResource(R.string.routine_reset))
                }
            }
        }

        if (state.running == null) {
            BigActionButton(
                text = stringResource(R.string.routine_start),
                icon = Icons.Filled.Bedtime,
                onClick = { onEvent(RoutineEvent.Start) },
                enabled = state.routine.steps.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("routine_start"),
            )
            if (!state.connected) {
                Text(stringResource(R.string.snackbar_pair_first), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun RunningCard(state: RoutineUiState, onEvent: (RoutineEvent) -> Unit) {
    val running = state.running ?: return
    val locale = currentLocale()
    ZocksCard(modifier = Modifier.testTag("routine_running")) {
        Column(Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) {
            Text(
                stringResource(R.string.routine_running, running.stepIndex + 1, running.routine.steps.size),
                style = MaterialTheme.typography.titleLarge,
            )
            state.now?.let { now ->
                val remaining = Duration.between(now, running.stepEndsAt).coerceAtLeast(Duration.ZERO)
                Text(stringResource(R.string.routine_step_remaining, formatDuration(remaining)), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                stringResource(R.string.routine_ends_at, formatTime(running.endsAt, state.use24HourClock, locale)),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        BigActionButton(
            text = stringResource(R.string.routine_stop),
            icon = Icons.Filled.Stop,
            onClick = { onEvent(RoutineEvent.Stop) },
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Dimens.SpaceL)
                .testTag("routine_stop"),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StepCard(index: Int, step: RoutineStep, state: RoutineUiState, editable: Boolean, onEvent: (RoutineEvent) -> Unit) {
    val number = index + 1
    val active = state.running?.stepIndex == index
    fun change(transform: (RoutineStep) -> RoutineStep) = onEvent(RoutineEvent.ChangeStep(index, transform(step)))

    ZocksCard(modifier = Modifier.testTag("routine_step_$index")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.routine_step_title, number),
                style = MaterialTheme.typography.titleMedium,
                color = if (active) ZocksThemeExt.colors.sleep else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() },
            )
            if (editable) {
                IconButton(onClick = { onEvent(RoutineEvent.MoveStep(index, -1)) }, enabled = index > 0) {
                    Icon(Icons.Outlined.ArrowUpward, contentDescription = stringResource(R.string.routine_move_up, number))
                }
                IconButton(onClick = { onEvent(RoutineEvent.MoveStep(index, 1)) }, enabled = index < state.routine.steps.lastIndex) {
                    Icon(Icons.Outlined.ArrowDownward, contentDescription = stringResource(R.string.routine_move_down, number))
                }
                IconButton(
                    onClick = { onEvent(RoutineEvent.RemoveStep(index)) },
                    modifier = Modifier.testTag("routine_remove_$index"),
                ) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = stringResource(R.string.routine_remove_step, number))
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceM)) {
            Labeled(stringResource(R.string.routine_step_duration)) {
                BigStepper(
                    value = formatDuration(Duration.ofMinutes(step.durationMinutes.toLong())),
                    decreaseLabel = stringResource(R.string.routine_shorter),
                    increaseLabel = stringResource(R.string.routine_longer),
                    onDecrease = { change { it.copy(durationMinutes = (it.durationMinutes - MINUTES_STEP).coerceAtLeast(MINUTES_STEP)) } },
                    onIncrease = { change { it.copy(durationMinutes = it.durationMinutes + MINUTES_STEP) } },
                    canDecrease = editable && step.durationMinutes > MINUTES_STEP,
                    canIncrease = editable && step.durationMinutes < EveningRoutine.MAX_STEP_MINUTES,
                    testTag = "routine_duration_$index",
                )
            }

            SwitchRow(
                title = stringResource(R.string.routine_step_heat),
                body = null,
                checked = step.heat != null,
                onCheckedChange = { on -> if (editable) change { it.copy(heat = if (on) RoutineHeat(2) else null) } },
                testTag = "routine_heat_$index",
            )
            step.heat?.let { heat ->
                BigStepper(
                    value = stringResource(R.string.heat_level_value, heat.level),
                    decreaseLabel = stringResource(R.string.routine_level_lower),
                    increaseLabel = stringResource(R.string.routine_level_higher),
                    onDecrease = { change { it.copy(heat = RoutineHeat(heat.level - 1)) } },
                    onIncrease = { change { it.copy(heat = RoutineHeat(heat.level + 1)) } },
                    canDecrease = editable && heat.level > 1,
                    canIncrease = editable && heat.level < EveningRoutine.MAX_HEAT_LEVEL,
                    color = ZocksThemeExt.colors.heat,
                    testTag = "routine_level_$index",
                )
            }

            SwitchRow(
                title = stringResource(R.string.routine_step_massage),
                body = null,
                checked = step.massage != null,
                onCheckedChange = { on ->
                    if (editable) change { it.copy(massage = if (on) RoutineMassage(BuiltInMassagePrograms.RELAX.id, 40) else null) }
                },
                testTag = "routine_massage_$index",
            )
            step.massage?.let { massage ->
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
                    state.programs.forEach { program ->
                        FilterChip(
                            selected = program.id == massage.programId,
                            onClick = { if (editable) change { it.copy(massage = massage.copy(programId = program.id)) } },
                            label = { Text(programName(program)) },
                            modifier = Modifier.heightIn(min = 48.dp),
                        )
                    }
                }
                BigStepper(
                    value = stringResource(R.string.routine_intensity_value, massage.intensity),
                    decreaseLabel = stringResource(R.string.massage_intensity_decrease),
                    increaseLabel = stringResource(R.string.massage_intensity_increase),
                    onDecrease = { change { it.copy(massage = massage.copy(intensity = massage.intensity - INTENSITY_STEP)) } },
                    onIncrease = { change { it.copy(massage = massage.copy(intensity = massage.intensity + INTENSITY_STEP)) } },
                    canDecrease = editable && massage.intensity > EveningRoutine.MIN_INTENSITY,
                    canIncrease = editable && massage.intensity < 100,
                    color = ZocksThemeExt.colors.massage,
                    testTag = "routine_intensity_$index",
                )
            }
            if (step.isPause) {
                Text(stringResource(R.string.routine_step_pause), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private const val MINUTES_STEP = 5
private const val INTENSITY_STEP = 10
