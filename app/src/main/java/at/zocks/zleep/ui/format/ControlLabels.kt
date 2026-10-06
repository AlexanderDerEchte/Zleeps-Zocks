package at.zocks.zleep.ui.format

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import at.zocks.zleep.R
import at.zocks.zleep.domain.heat.HeatRejection
import at.zocks.zleep.domain.heat.ShutoffReason
import at.zocks.zleep.domain.massage.BuiltInMassagePrograms
import at.zocks.zleep.domain.massage.MassageProgram
import at.zocks.zleep.domain.massage.MassageProgramType
import at.zocks.zleep.domain.massage.MassageTempo
import at.zocks.zleep.domain.model.MassageZone
import at.zocks.zleep.domain.model.SockSide

@StringRes
fun sideLabelRes(side: SockSide): Int = when (side) {
    SockSide.LEFT -> R.string.sock_left
    SockSide.RIGHT -> R.string.sock_right
    SockSide.BOTH -> R.string.side_both
}

@StringRes
fun shutoffReasonRes(reason: ShutoffReason): Int = when (reason) {
    ShutoffReason.OVER_TEMPERATURE -> R.string.heat_reason_over_temperature
    ShutoffReason.MAX_RUNTIME -> R.string.heat_reason_max_runtime
    ShutoffReason.IMPLAUSIBLE_LOW, ShutoffReason.IMPLAUSIBLE_HIGH, ShutoffReason.IMPLAUSIBLE_JUMP ->
        R.string.heat_reason_implausible
    ShutoffReason.SENSOR_MISSING -> R.string.heat_reason_sensor_missing
}

@StringRes
fun rejectionRes(rejection: HeatRejection): Int = when (rejection) {
    HeatRejection.NO_TEMPERATURE_SENSOR -> R.string.heat_rejection_no_sensor
    HeatRejection.COOLDOWN -> R.string.heat_rejection_cooldown
    HeatRejection.NOT_CONNECTED -> R.string.heat_rejection_not_connected
}

private val builtInNames = mapOf(
    BuiltInMassagePrograms.RELAX.id to (R.string.massage_program_relax to R.string.massage_desc_relax),
    BuiltInMassagePrograms.WAVE.id to (R.string.massage_program_wave to R.string.massage_desc_wave),
    BuiltInMassagePrograms.KNEAD.id to (R.string.massage_program_knead to R.string.massage_desc_knead),
    BuiltInMassagePrograms.PULSE.id to (R.string.massage_program_pulse to R.string.massage_desc_pulse),
    BuiltInMassagePrograms.RECOVERY.id to (R.string.massage_program_recovery to R.string.massage_desc_recovery),
    BuiltInMassagePrograms.INTENSE.id to (R.string.massage_program_intense to R.string.massage_desc_intense),
    BuiltInMassagePrograms.SLEEP.id to (R.string.massage_program_sleep to R.string.massage_desc_sleep),
)

@Composable
fun programName(program: MassageProgram): String =
    builtInNames[program.id]?.let { stringResource(it.first) } ?: program.name.orEmpty()

@Composable
fun programDescription(program: MassageProgram): String =
    builtInNames[program.id]?.let { stringResource(it.second) } ?: stringResource(R.string.massage_desc_custom)

@Composable
fun zoneLabel(zone: MassageZone): String = stringResource(
    when (zone) {
        MassageZone.HEEL -> R.string.zone_heel
        MassageZone.ARCH -> R.string.zone_arch
        MassageZone.BALL -> R.string.zone_ball
        MassageZone.TOES -> R.string.zone_toes
    },
)

@Composable
fun tempoLabel(tempo: MassageTempo): String = stringResource(
    when (tempo) {
        MassageTempo.SLOW -> R.string.tempo_slow
        MassageTempo.MEDIUM -> R.string.tempo_medium
        MassageTempo.FAST -> R.string.tempo_fast
    },
)

@Composable
fun programTypeLabel(type: MassageProgramType): String = stringResource(
    when (type) {
        MassageProgramType.WAVE -> R.string.massage_type_wave
        MassageProgramType.PULSE -> R.string.massage_type_pulse
        MassageProgramType.KNEAD -> R.string.massage_type_knead
        MassageProgramType.RELAX -> R.string.massage_type_relax
        MassageProgramType.ALTERNATE -> R.string.massage_type_alternate
        MassageProgramType.SLEEP -> R.string.massage_type_sleep
    },
)
