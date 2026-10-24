package com.k4music.android.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.k4music.app.R
import com.k4music.android.preferences.AppearancePreferences
import com.k4music.android.preferences.PlayerPreferences
import com.k4music.android.ui.screens.Route
import com.k4music.android.utils.currentLocale
import com.k4music.android.utils.findActivity
import com.k4music.android.utils.startLanguagePicker
import com.k4music.core.ui.BuiltInFontFamily
import com.k4music.core.ui.ColorMode
import com.k4music.core.ui.ColorSource
import com.k4music.core.ui.Darkness
import com.k4music.core.ui.LocalAppearance
import com.k4music.core.ui.ThumbnailRoundness
import com.k4music.core.ui.googleFontsAvailable
import com.k4music.core.ui.utils.isAtLeastAndroid13

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.k4music.android.utils.secondary
import com.k4music.android.utils.semiBold

@Route
@Composable
fun AppearanceSettings() = with(AppearancePreferences) {
    val (colorPalette) = LocalAppearance.current
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()

    SettingsCategoryScreen(title = stringResource(R.string.appearance)) {
        SettingsGroup(title = stringResource(R.string.colors)) {
            EnumValueSelectorSettingsEntry(
                title = stringResource(R.string.color_source),
                selectedValue = colorSource,
                onValueSelect = { colorSource = it },
                valueText = { it.nameLocalized }
            )
            AnimatedVisibility(visible = colorSource == ColorSource.Default) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp)
                ) {
                    androidx.compose.foundation.text.BasicText(
                        text = stringResource(R.string.theme_accents),
                        style = LocalAppearance.current.typography.s.semiBold.copy(color = colorPalette.text)
                    )
                    androidx.compose.foundation.text.BasicText(
                        text = stringResource(R.string.theme_accents_description),
                        style = LocalAppearance.current.typography.xs.secondary,
                        modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val presets = listOf(
                            "#7B5CFF" to "Violet",
                            "#FF2A6D" to "Rose",
                            "#FF9100" to "Amber",
                            "#00E676" to "Emerald",
                            "#00E5FF" to "Cyan",
                            "#2979FF" to "Blue",
                            "#FF80AB" to "Pink",
                            "#9D4EDD" to "Amethyst"
                        )
                        items(presets, key = { it.first }) { (hex, name) ->
                            val color = com.k4music.android.ui.theme.parseHexColor(hex)
                            val isSelected = accentColorHex.equals(hex, ignoreCase = true)
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(
                                        width = if (isSelected) 3.dp else 1.dp,
                                        color = if (isSelected) Color.White else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .clickable {
                                        accentColorHex = hex
                                    }
                            ) {
                                if (isSelected) {
                                    androidx.compose.material3.Icon(
                                        painter = androidx.compose.ui.res.painterResource(R.drawable.star),
                                        contentDescription = name,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            EnumValueSelectorSettingsEntry(
                title = stringResource(R.string.color_mode),
                selectedValue = colorMode,
                onValueSelect = { colorMode = it },
                valueText = { it.nameLocalized }
            )
            AnimatedVisibility(visible = colorMode == ColorMode.Dark || (colorMode == ColorMode.System && isDark)) {
                EnumValueSelectorSettingsEntry(
                    title = stringResource(R.string.darkness),
                    selectedValue = darkness,
                    onValueSelect = { darkness = it },
                    valueText = { it.nameLocalized }
                )
            }
        }
        SettingsGroup(title = stringResource(R.string.shapes)) {
            EnumValueSelectorSettingsEntry(
                title = stringResource(R.string.thumbnail_roundness),
                selectedValue = thumbnailRoundness,
                onValueSelect = { thumbnailRoundness = it },
                trailingContent = {
                    Spacer(
                        modifier = Modifier
                            .border(
                                width = 1.dp,
                                color = colorPalette.accent,
                                shape = thumbnailRoundness.shape
                            )
                            .background(
                                color = colorPalette.background1,
                                shape = thumbnailRoundness.shape
                            )
                            .size(36.dp)
                    )
                },
                valueText = { it.nameLocalized }
            )
        }
        SettingsGroup(title = stringResource(R.string.text)) {
            if (isAtLeastAndroid13) SettingsEntry(
                title = stringResource(R.string.language),
                text = currentLocale()?.displayLanguage
                    ?: stringResource(R.string.color_source_default),
                onClick = {
                    context.findActivity().startLanguagePicker()
                }
            )

            if (googleFontsAvailable()) EnumValueSelectorSettingsEntry(
                title = stringResource(R.string.font),
                selectedValue = fontFamily,
                onValueSelect = { fontFamily = it },
                valueText = {
                    if (it == BuiltInFontFamily.System) stringResource(R.string.use_system_font) else it.name
                }
            ) else SwitchSettingsEntry(
                title = stringResource(R.string.use_system_font),
                text = stringResource(R.string.use_system_font_description),
                isChecked = fontFamily == BuiltInFontFamily.System,
                onCheckedChange = {
                    fontFamily = if (it) BuiltInFontFamily.System else BuiltInFontFamily.Poppins
                }
            )

            SwitchSettingsEntry(
                title = stringResource(R.string.apply_font_padding),
                text = stringResource(R.string.apply_font_padding_description),
                isChecked = applyFontPadding,
                onCheckedChange = { applyFontPadding = it }
            )
        }
        if (!isAtLeastAndroid13) SettingsGroup(title = stringResource(R.string.lockscreen)) {
            SwitchSettingsEntry(
                title = stringResource(R.string.show_song_cover),
                text = stringResource(R.string.show_song_cover_description),
                isChecked = isShowingThumbnailInLockscreen,
                onCheckedChange = { isShowingThumbnailInLockscreen = it }
            )
        }
        SettingsGroup(title = stringResource(R.string.player)) {
            SwitchSettingsEntry(
                title = stringResource(R.string.previous_button_while_collapsed),
                text = stringResource(R.string.previous_button_while_collapsed_description),
                isChecked = PlayerPreferences.isShowingPrevButtonCollapsed,
                onCheckedChange = { PlayerPreferences.isShowingPrevButtonCollapsed = it }
            )

            SwitchSettingsEntry(
                title = stringResource(R.string.show_like_button),
                text = stringResource(R.string.show_like_button_description),
                isChecked = PlayerPreferences.showLike,
                onCheckedChange = { PlayerPreferences.showLike = it }
            )

            EnumValueSelectorSettingsEntry(
                title = stringResource(R.string.seek_bar_style),
                selectedValue = PlayerPreferences.seekBarStyle,
                onValueSelect = { PlayerPreferences.seekBarStyle = it },
                valueText = { it.displayName() }
            )

            AnimatedVisibility(
                visible = PlayerPreferences.seekBarStyle == PlayerPreferences.SeekBarStyle.Wavy,
                label = ""
            ) {
                EnumValueSelectorSettingsEntry(
                    title = stringResource(R.string.seek_bar_quality),
                    selectedValue = PlayerPreferences.wavySeekBarQuality,
                    onValueSelect = { PlayerPreferences.wavySeekBarQuality = it },
                    valueText = { it.displayName() }
                )
            }

            SwitchSettingsEntry(
                title = stringResource(R.string.swipe_to_remove_item),
                text = stringResource(R.string.swipe_to_remove_item_description),
                isChecked = PlayerPreferences.horizontalSwipeToRemoveItem,
                onCheckedChange = { PlayerPreferences.horizontalSwipeToRemoveItem = it }
            )

            SwitchSettingsEntry(
                title = stringResource(R.string.lyrics_keep_screen_awake),
                text = stringResource(R.string.lyrics_keep_screen_awake_description),
                isChecked = PlayerPreferences.lyricsKeepScreenAwake,
                onCheckedChange = { PlayerPreferences.lyricsKeepScreenAwake = it }
            )

            SwitchSettingsEntry(
                title = stringResource(R.string.lyrics_show_system_bars),
                text = stringResource(R.string.lyrics_show_system_bars_description),
                isChecked = PlayerPreferences.lyricsShowSystemBars,
                onCheckedChange = { PlayerPreferences.lyricsShowSystemBars = it }
            )

            SwitchSettingsEntry(
                title = stringResource(R.string.pip),
                text = stringResource(R.string.pip_description),
                isChecked = autoPip,
                onCheckedChange = { autoPip = it }
            )

            SwitchSettingsEntry(
                title = stringResource(R.string.auto_open_player),
                text = stringResource(R.string.auto_open_player_description),
                isChecked = openPlayer,
                onCheckedChange = { openPlayer = it }
            )
        }
        SettingsGroup(title = stringResource(R.string.songs)) {
            SwitchSettingsEntry(
                title = stringResource(R.string.swipe_to_hide_song),
                text = stringResource(R.string.swipe_to_hide_song_description),
                isChecked = swipeToHideSong,
                onCheckedChange = { swipeToHideSong = it }
            )
            AnimatedVisibility(
                visible = swipeToHideSong,
                label = ""
            ) {
                SwitchSettingsEntry(
                    title = stringResource(R.string.swipe_to_hide_song_confirm),
                    text = stringResource(R.string.swipe_to_hide_song_confirm_description),
                    isChecked = swipeToHideSongConfirm,
                    onCheckedChange = { swipeToHideSongConfirm = it }
                )
            }
            SwitchSettingsEntry(
                title = stringResource(R.string.hide_explicit),
                text = stringResource(R.string.hide_explicit_description),
                isChecked = hideExplicit,
                onCheckedChange = { hideExplicit = it }
            )
        }
    }
}

val ColorSource.nameLocalized
    @Composable get() = stringResource(
        when (this) {
            ColorSource.Default -> R.string.color_source_default
            ColorSource.Dynamic -> R.string.color_source_dynamic
            ColorSource.MaterialYou -> R.string.color_source_material_you
        }
    )

val ColorMode.nameLocalized
    @Composable get() = stringResource(
        when (this) {
            ColorMode.System -> R.string.color_mode_system
            ColorMode.Light -> R.string.color_mode_light
            ColorMode.Dark -> R.string.color_mode_dark
        }
    )

val Darkness.nameLocalized
    @Composable get() = stringResource(
        when (this) {
            Darkness.Normal -> R.string.darkness_normal
            Darkness.AMOLED -> R.string.darkness_amoled
            Darkness.PureBlack -> R.string.darkness_pureblack
        }
    )

val ThumbnailRoundness.nameLocalized
    @Composable get() = stringResource(
        when (this) {
            ThumbnailRoundness.None -> R.string.none
            ThumbnailRoundness.Light -> R.string.thumbnail_roundness_light
            ThumbnailRoundness.Medium -> R.string.thumbnail_roundness_medium
            ThumbnailRoundness.Heavy -> R.string.thumbnail_roundness_heavy
            ThumbnailRoundness.Heavier -> R.string.thumbnail_roundness_heavier
            ThumbnailRoundness.Heaviest -> R.string.thumbnail_roundness_heaviest
        }
    )
