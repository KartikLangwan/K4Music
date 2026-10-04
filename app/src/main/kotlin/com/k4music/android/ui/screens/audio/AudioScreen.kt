package com.k4music.android.ui.screens.audio

import androidx.annotation.OptIn
import androidx.annotation.StringRes
import androidx.media3.common.util.UnstableApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.k4music.android.LocalPlayerAwareWindowInsets
import com.k4music.android.LocalPlayerServiceBinder
import com.k4music.android.preferences.PlayerPreferences
import com.k4music.android.preferences.PlayerPreferences.VirtualizerMode
import com.k4music.android.service.AudioCapabilities
import com.k4music.android.service.EqualizerPreset
import com.k4music.android.ui.components.themed.Scaffold
import com.k4music.android.ui.components.themed.TextFieldDialog
import com.k4music.android.ui.screens.GlobalRoutes
import com.k4music.android.ui.screens.Route
import com.k4music.android.utils.rememberEqualizerLauncher
import com.k4music.app.R
import com.k4music.compose.routing.RouteHandler
import com.k4music.core.ui.LocalAppearance
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.math.roundToInt

private const val CUSTOM = "Custom"
private const val EQ_STEP = 50 // millibels; 0.5 dB steps keep writes and announcements sane

@Route
@Composable
fun AudioScreen() {
    val saveableStateHolder = rememberSaveableStateHolder()
    val (tabIndex, onTabChanged) = rememberSaveable { mutableIntStateOf(0) }

    RouteHandler {
        GlobalRoutes()

        Content {
            Scaffold(
                key = "audio",
                topIconButtonId = R.drawable.chevron_back,
                onTopIconButtonClick = pop,
                tabIndex = tabIndex,
                onTabChange = onTabChanged,
                tabColumnContent = {
                    tab(0, R.string.audio_effects, R.drawable.equalizer)
                }
            ) { currentTabIndex ->
                saveableStateHolder.SaveableStateProvider(currentTabIndex) {
                    AudioEffectsContent()
                }
            }
        }
    }
}

@Composable
private fun AudioEffectsContent() {
    val binder = LocalPlayerServiceBinder.current
    val capabilities by (binder?.audioEffects ?: remember { MutableStateFlow(null) }).collectAsState()
    val bottom = LocalPlayerAwareWindowInsets.current

    val caps = capabilities
    if (caps == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = LocalAppearance.current.colorPalette.accent)
        }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 4.dp,
            bottom = 24.dp + bottom.getBottomDp()
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item(key = "intro") {
            Text(
                text = stringResource(R.string.audio_effects_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = LocalAppearance.current.colorPalette.textSecondary,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
        item(key = "equalizer") { EqualizerSection(caps) }
        item(key = "bass") { BassBoostSection(caps) }
        item(key = "spatial") { SpatialSection(caps) }
        item(key = "atd") { WideStageSection() }
        item(key = "reverb") { ReverbSection(caps) }
        item(key = "system") { SystemEqualizerEntry() }
    }
}

@Composable
private fun androidx.compose.foundation.layout.WindowInsets.getBottomDp() =
    with(androidx.compose.ui.platform.LocalDensity.current) { getBottom(this).toDp() }

@Composable
private fun EffectCard(
    @StringRes title: Int,
    @StringRes description: Int,
    modifier: Modifier = Modifier,
    checked: Boolean? = null,
    onCheckedChange: (Boolean) -> Unit = {},
    enabled: Boolean = true,
    @StringRes unavailable: Int? = null,
    content: @Composable ColumnScope.() -> Unit = {}
) {
    val palette = LocalAppearance.current.colorPalette
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(palette.background1)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(title),
                    style = MaterialTheme.typography.titleMedium,
                    color = palette.text,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    stringResource(unavailable ?: description),
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textSecondary
                )
            }
            if (checked != null && unavailable == null) Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = palette.onAccent,
                    checkedTrackColor = palette.accent,
                    uncheckedThumbColor = palette.textSecondary,
                    uncheckedTrackColor = palette.background2,
                    uncheckedBorderColor = palette.background2
                )
            )
        }
        if (unavailable == null) content()
    }
}

@Composable
private fun EffectSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    label: String,
    valueText: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val palette = LocalAppearance.current.colorPalette
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = palette.textSecondary,
            modifier = Modifier.width(64.dp)
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = palette.accent,
                activeTrackColor = palette.accent,
                inactiveTrackColor = palette.background2
            ),
            modifier = Modifier
                .weight(1f)
                .semantics {
                    contentDescription = label
                    stateDescription = valueText
                }
        )
        Text(
            valueText,
            style = MaterialTheme.typography.labelMedium,
            color = palette.text,
            modifier = Modifier
                .width(64.dp)
                .padding(start = 8.dp)
        )
    }
}

@Composable
private fun EqualizerSection(caps: AudioCapabilities) {
    val palette = LocalAppearance.current.colorPalette
    var saving by rememberSaveable { mutableStateOf(false) }

    val bands = caps.bandFrequenciesHz
    val levels = PlayerPreferences.equalizerLevels.let { stored ->
        List(bands.size) { stored.getOrElse(it) { 0 }.coerceIn(caps.minLevel, caps.maxLevel) }
    }
    val enabled = PlayerPreferences.equalizerEnabled
    val userPresets = PlayerPreferences.equalizerUserPresets

    fun select(name: String, newLevels: List<Int>) {
        PlayerPreferences.equalizerLevels = newLevels
        PlayerPreferences.equalizerPreset = name
        PlayerPreferences.equalizerEnabled = true
    }

    if (saving) TextFieldDialog(
        hintText = stringResource(R.string.equalizer_preset_name),
        onDismiss = { saving = false },
        isTextInputValid = { it.isNotBlank() && it.length <= 24 },
        onAccept = { name ->
            val trimmed = name.trim()
            PlayerPreferences.equalizerUserPresets = userPresets + (trimmed to levels)
            PlayerPreferences.equalizerPreset = trimmed
            saving = false
        }
    )

    EffectCard(
        title = R.string.equalizer,
        description = R.string.equalizer_effect_description,
        checked = enabled,
        onCheckedChange = { PlayerPreferences.equalizerEnabled = it },
        unavailable = R.string.effect_unavailable.takeUnless { caps.equalizer }
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val selected = PlayerPreferences.equalizerPreset
            EqualizerPreset.entries.forEach { preset ->
                PresetChip(
                    label = stringResource(preset.title),
                    selected = enabled && selected == preset.name,
                    onClick = {
                        select(
                            preset.name,
                            EqualizerPreset.levelsFor(preset.gainsDb, bands, caps.minLevel, caps.maxLevel)
                        )
                    }
                )
            }
            userPresets.forEach { (name, presetLevels) ->
                PresetChip(
                    label = name,
                    selected = enabled && selected == name,
                    onClick = { select(name, presetLevels) },
                    onRemove = {
                        PlayerPreferences.equalizerUserPresets = userPresets - name
                        if (selected == name) PlayerPreferences.equalizerPreset = CUSTOM
                    }
                )
            }
            if (selected == CUSTOM) PresetChip(
                label = stringResource(R.string.equalizer_custom),
                selected = enabled,
                onClick = { PlayerPreferences.equalizerEnabled = true }
            )
        }

        ResponseCurve(
            levels = levels,
            minLevel = caps.minLevel,
            maxLevel = caps.maxLevel,
            active = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
        )

        bands.forEachIndexed { band, hz ->
            val level = levels[band]
            EffectSlider(
                value = level.toFloat(),
                onValueChange = { value ->
                    val snapped = (value / EQ_STEP).roundToInt() * EQ_STEP
                    if (snapped != level) {
                        PlayerPreferences.equalizerLevels = levels.toMutableList().also { it[band] = snapped }
                        PlayerPreferences.equalizerPreset = CUSTOM
                        PlayerPreferences.equalizerEnabled = true
                    }
                },
                valueRange = caps.minLevel.toFloat()..caps.maxLevel.toFloat(),
                label = formatHz(hz),
                valueText = stringResource(R.string.format_db_signed, level / 100f)
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { select(EqualizerPreset.Flat.name, List(bands.size) { 0 }) }) {
                Text(stringResource(R.string.reset), color = palette.accent)
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { saving = true }) {
                Icon(painterResource(R.drawable.add), null, Modifier.size(16.dp), tint = palette.accent)
                Text(stringResource(R.string.equalizer_save_preset), color = palette.accent, modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}

private fun formatHz(hz: Int) = if (hz >= 1000) {
    val khz = hz / 1000f
    if (khz >= 10f || khz % 1f == 0f) "${khz.roundToInt()} kHz" else "%.1f kHz".format(khz)
} else "$hz Hz"

@Composable
private fun PresetChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    onRemove: (() -> Unit)? = null
) {
    val palette = LocalAppearance.current.colorPalette
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, maxLines = 1) },
        trailingIcon = onRemove?.let { remove ->
            {
                Icon(
                    painter = painterResource(R.drawable.close),
                    contentDescription = stringResource(R.string.equalizer_delete_preset, label),
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .clickable(onClick = remove)
                )
            }
        },
        shape = CircleShape,
        border = null,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = palette.background2,
            labelColor = palette.text,
            iconColor = palette.textSecondary,
            selectedContainerColor = palette.accent,
            selectedLabelColor = palette.onAccent,
            selectedTrailingIconColor = palette.onAccent
        )
    )
}

/** A smooth curve through the band levels: a quick read of the overall shape. */
@Composable
private fun ResponseCurve(
    levels: List<Int>,
    minLevel: Int,
    maxLevel: Int,
    active: Boolean,
    modifier: Modifier = Modifier
) {
    val palette = LocalAppearance.current.colorPalette
    val color = if (active) palette.accent else palette.textDisabled
    Canvas(modifier) {
        val mid = size.height / 2
        drawLine(palette.text.copy(alpha = 0.08f), Offset(0f, mid), Offset(size.width, mid), 1.dp.toPx())
        if (levels.isEmpty()) return@Canvas
        val span = (maxLevel - minLevel).coerceAtLeast(1)
        val points = levels.mapIndexed { i, level ->
            val x = if (levels.size == 1) size.width / 2 else size.width * i / (levels.size - 1)
            Offset(x, size.height * (1f - (level - minLevel).toFloat() / span))
        }
        val path = Path().apply {
            moveTo(points.first().x, points.first().y)
            for (i in 1 until points.size) {
                val (p0, p1) = points[i - 1] to points[i]
                val cx = (p0.x + p1.x) / 2
                cubicTo(cx, p0.y, cx, p1.y, p1.x, p1.y)
            }
        }
        val fill = Path().apply {
            addPath(path)
            lineTo(points.last().x, size.height)
            lineTo(points.first().x, size.height)
            close()
        }
        drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.25f), color.copy(alpha = 0f))))
        drawPath(path, color, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
    }
}

@Composable
private fun BassBoostSection(caps: AudioCapabilities) {
    val on = PlayerPreferences.bassBoost
    EffectCard(
        title = R.string.bass_boost,
        description = R.string.bass_boost_effect_description,
        checked = on,
        onCheckedChange = { PlayerPreferences.bassBoost = it },
        unavailable = R.string.effect_unavailable.takeUnless { caps.bassBoost }
    ) {
        val level = PlayerPreferences.bassBoostLevel.coerceIn(0, 1000)
        EffectSlider(
            value = level.toFloat(),
            onValueChange = {
                val snapped = (it / 50).roundToInt() * 50
                if (snapped != level) PlayerPreferences.bassBoostLevel = snapped
            },
            valueRange = 0f..1000f,
            enabled = on,
            label = stringResource(R.string.effect_strength),
            valueText = "${level / 10}%"
        )
    }
}

@Composable
private fun SpatialSection(caps: AudioCapabilities) {
    val palette = LocalAppearance.current.colorPalette
    val mode = PlayerPreferences.virtualizerMode
    EffectCard(
        title = R.string.spatial_audio,
        description = R.string.spatial_audio_description,
        unavailable = R.string.effect_unavailable.takeUnless { caps.virtualizer }
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                Triple(VirtualizerMode.Off, R.string.off, true),
                Triple(VirtualizerMode.ThreeD, R.string.spatial_3d, caps.threeD),
                Triple(VirtualizerMode.Surround, R.string.spatial_surround, caps.surround)
            ).forEach { (option, label, supported) ->
                FilterChip(
                    selected = mode == option,
                    onClick = { PlayerPreferences.virtualizerMode = option },
                    enabled = supported,
                    label = { Text(stringResource(label)) },
                    shape = CircleShape,
                    border = null,
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = palette.background2,
                        labelColor = palette.text,
                        selectedContainerColor = palette.accent,
                        selectedLabelColor = palette.onAccent
                    )
                )
            }
        }
        Text(
            text = stringResource(
                when (mode) {
                    VirtualizerMode.Off -> R.string.spatial_off_hint
                    VirtualizerMode.ThreeD -> R.string.spatial_3d_hint
                    VirtualizerMode.Surround -> R.string.spatial_surround_hint
                }
            ),
            style = MaterialTheme.typography.bodySmall,
            color = palette.textSecondary
        )
        val strength = PlayerPreferences.virtualizerStrength.coerceIn(0, 1000)
        EffectSlider(
            value = strength.toFloat(),
            onValueChange = {
                val snapped = (it / 50).roundToInt() * 50
                if (snapped != strength) PlayerPreferences.virtualizerStrength = snapped
            },
            valueRange = 0f..1000f,
            enabled = mode != VirtualizerMode.Off,
            label = stringResource(R.string.effect_depth),
            valueText = "${strength / 10}%"
        )
    }
}

@Composable
private fun WideStageSection() {
    val width = PlayerPreferences.stereoWidening.coerceIn(0, 100)
    EffectCard(
        title = R.string.atd_wide_stage,
        description = R.string.atd_wide_stage_description,
        checked = width > 0,
        onCheckedChange = { PlayerPreferences.stereoWidening = if (it) 50 else 0 }
    ) {
        EffectSlider(
            value = width.toFloat(),
            onValueChange = {
                val snapped = (it / 5).roundToInt() * 5
                if (snapped != width) PlayerPreferences.stereoWidening = snapped
            },
            valueRange = 0f..100f,
            enabled = width > 0,
            label = stringResource(R.string.effect_intensity),
            valueText = "$width%"
        )
    }
}

@Composable
private fun ReverbSection(caps: AudioCapabilities) {
    val palette = LocalAppearance.current.colorPalette
    EffectCard(
        title = R.string.reverb,
        description = R.string.reverb_effect_description,
        unavailable = R.string.effect_unavailable.takeUnless { caps.reverb }
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            PlayerPreferences.Reverb.entries.forEach { reverb ->
                FilterChip(
                    selected = PlayerPreferences.reverb == reverb,
                    onClick = { PlayerPreferences.reverb = reverb },
                    label = { Text(reverb.displayName()) },
                    shape = CircleShape,
                    border = null,
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = palette.background2,
                        labelColor = palette.text,
                        selectedContainerColor = palette.accent,
                        selectedLabelColor = palette.onAccent
                    )
                )
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun SystemEqualizerEntry() {
    val binder = LocalPlayerServiceBinder.current
    val palette = LocalAppearance.current.colorPalette
    val launch by rememberEqualizerLauncher(audioSessionId = { binder?.player?.audioSessionId })

    Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        TextButton(onClick = launch) {
            Text(stringResource(R.string.open_system_equalizer), color = palette.accent)
        }
        Text(
            text = stringResource(R.string.audio_effects_device_note),
            style = MaterialTheme.typography.bodySmall,
            color = palette.textDisabled,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
    }
}

@get:StringRes
private val EqualizerPreset.title
    get() = when (this) {
        EqualizerPreset.Flat -> R.string.eq_flat
        EqualizerPreset.Vocal -> R.string.eq_vocal
        EqualizerPreset.Bass -> R.string.eq_bass
        EqualizerPreset.BassBoost -> R.string.eq_bass_boost
        EqualizerPreset.Treble -> R.string.eq_treble
        EqualizerPreset.Rock -> R.string.eq_rock
        EqualizerPreset.Pop -> R.string.eq_pop
        EqualizerPreset.Classical -> R.string.eq_classical
        EqualizerPreset.Electronic -> R.string.eq_electronic
    }
