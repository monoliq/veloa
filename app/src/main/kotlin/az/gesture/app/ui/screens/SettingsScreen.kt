package az.gesture.app.ui.screens

import android.app.Activity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import az.gesture.app.R
import az.gesture.app.data.AppColorTheme
import az.gesture.app.data.AppLanguage
import az.gesture.app.data.AppThemeMode
import az.gesture.app.data.BUTTON_COLOR_PRESETS
import az.gesture.app.data.ButtonTrigger
import az.gesture.app.data.EdgeSide
import az.gesture.app.data.GESTURE_COLOR_OPTIONS
import az.gesture.app.data.LocaleHelper
import az.gesture.app.data.Repo
import az.gesture.app.data.SIDE_HANDLE_COLOR_OPTIONS
import az.gesture.app.ui.components.ActionIcon
import az.gesture.app.ui.components.ActionPickerDialog
import az.gesture.app.ui.components.formatActionLabel
import az.gesture.app.ui.theme.GestureTheme
import java.text.Collator
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    repo: Repo,
    themeMode: AppThemeMode,
    colorTheme: AppColorTheme,
    onThemeModeChange: (AppThemeMode) -> Unit,
    onColorThemeChange: (AppColorTheme) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState()
) {
    val context = LocalContext.current
    var settings by remember { mutableStateOf(repo.getSettings()) }
    var mappings by remember { mutableStateOf(repo.getAllTriggerMappings()) }
    var editingTrigger by remember { mutableStateOf<ButtonTrigger?>(null) }
    var expandTriggers by remember { mutableStateOf(false) }
    var languageMenuExpanded by remember { mutableStateOf(false) }

    editingTrigger?.let { trigger ->
        ActionPickerDialog(
            includeNone = true,
            includeOpenPad = true,
            onDismiss = { editingTrigger = null },
            onActionSelected = { action ->
                repo.setTriggerAction(trigger, action)
                mappings = repo.getAllTriggerMappings()
                editingTrigger = null
            }
        )
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. SYSTEM
        item {
            SettingsSectionTitle(stringResource(R.string.settings_system_title))
            Spacer(Modifier.height(8.dp))
            SettingsCard {
                Text(
                    text = stringResource(R.string.settings_language),
                    style = MaterialTheme.typography.titleSmall
                )
                Box {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { languageMenuExpanded = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(settings.language.labelRes),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Icon(
                                painter = painterResource(R.drawable.ic_arrow_down),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    val sortedLanguages = remember(settings.language) {
                        val collator = Collator.getInstance(LocaleHelper.localeFor(settings.language))
                        AppLanguage.entries
                            .map { it to context.getString(it.labelRes) }
                            .sortedWith { a, b -> collator.compare(a.second, b.second) }
                    }

                    DropdownMenu(
                        expanded = languageMenuExpanded,
                        onDismissRequest = { languageMenuExpanded = false }
                    ) {
                        sortedLanguages.forEach { (lang, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    languageMenuExpanded = false
                                    if (settings.language != lang) {
                                        repo.updateSettings { it.copy(language = lang) }
                                        settings = repo.getSettings()
                                        (context as? Activity)?.recreate()
                                    }
                                }
                            )
                        }
                    }
                }

                Text(
                    text = stringResource(R.string.settings_theme),
                    style = MaterialTheme.typography.titleSmall
                )
                val themeOptions = listOf(
                    Triple(AppThemeMode.LIGHT, R.drawable.ic_sun, R.string.theme_light),
                    Triple(AppThemeMode.DARK, R.drawable.ic_moon, R.string.theme_dark),
                    Triple(AppThemeMode.AMOLED, R.drawable.ic_theme_auto, R.string.theme_amoled)
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    themeOptions.forEachIndexed { idx, (mode, iconRes, labelRes) ->
                        SegmentedButton(
                            selected = themeMode == mode,
                            onClick = { onThemeModeChange(mode) },
                            shape = SegmentedButtonDefaults.itemShape(index = idx, count = themeOptions.size),
                            icon = {
                                Icon(
                                    painter = painterResource(iconRes),
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        ) {
                            Text(stringResource(labelRes), maxLines = 1)
                        }
                    }
                }

                Text(
                    text = stringResource(R.string.settings_app_color_theme),
                    style = MaterialTheme.typography.titleSmall
                )
                AppColorThemePicker(
                    selectedTheme = colorTheme,
                    onSelect = onColorThemeChange
                )

                SettingsCheckboxRow(
                    title = stringResource(R.string.settings_hide_keyboard),
                    subtitle = stringResource(R.string.settings_hide_keyboard_desc),
                    checked = settings.hideOnKeyboard,
                    onCheckedChange = { checked ->
                        repo.updateSettings { it.copy(hideOnKeyboard = checked) }
                        settings = repo.getSettings()
                    }
                )
            }
        }

        // 2. GESTURE OPTIONS
        item {
            SettingsSectionTitle(stringResource(R.string.settings_gesture_options_title))
            Spacer(Modifier.height(8.dp))
            SettingsCard {
                Column {
                    Text(
                        text = stringResource(R.string.settings_sensitivity_label),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = stringResource(R.string.settings_sensitivity_value, settings.sensitivityPercent),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = settings.sensitivityPercent.toFloat(),
                        onValueChange = { v ->
                            val snapped = ((v / 5f).roundToInt() * 5).coerceIn(50, 95)
                            if (snapped != settings.sensitivityPercent) {
                                repo.updateSettings { it.copy(sensitivityPercent = snapped) }
                                settings = repo.getSettings()
                            }
                        },
                        valueRange = 50f..95f,
                        steps = 8
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.settings_sensitivity_relaxed),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = stringResource(R.string.settings_sensitivity_strict),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.settings_gesture_color),
                    style = MaterialTheme.typography.titleSmall
                )
                ColorSwatchRow(
                    colors = GESTURE_COLOR_OPTIONS,
                    selectedColor = settings.gestureColor,
                    onSelect = { color ->
                        repo.updateSettings { it.copy(gestureColor = color) }
                        settings = repo.getSettings()
                    }
                )

                SettingsCheckboxRow(
                    title = stringResource(R.string.settings_direction_priority),
                    subtitle = stringResource(R.string.settings_direction_priority_desc),
                    checked = settings.directionPriority,
                    onCheckedChange = { checked ->
                        repo.updateSettings { it.copy(directionPriority = checked) }
                        settings = repo.getSettings()
                    }
                )

                SettingsCheckboxRow(
                    title = stringResource(R.string.settings_multi_stroke),
                    subtitle = stringResource(R.string.settings_multi_stroke_desc),
                    checked = settings.multiStroke,
                    onCheckedChange = { checked ->
                        repo.updateSettings { it.copy(multiStroke = checked) }
                        settings = repo.getSettings()
                    }
                )
            }
        }

        // 3. FLOATING HANDLER
        item {
            SettingsSectionTitle(stringResource(R.string.settings_floating_title))
            Spacer(Modifier.height(8.dp))
            SettingsCard {
                Column {
                    Text(
                        text = stringResource(R.string.settings_floating_size, settings.sizeDp),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Slider(
                        value = settings.sizeDp.toFloat(),
                        onValueChange = { v ->
                            val next = v.roundToInt().coerceIn(30, 80)
                            repo.updateSettings { it.copy(sizeDp = next) }
                            settings = repo.getSettings()
                        },
                        valueRange = 30f..80f
                    )
                }

                Column {
                    val alphaText = String.format(Locale.US, "%.1f", settings.idleOpacity / 100f)
                    Text(
                        text = stringResource(R.string.settings_floating_transparency, alphaText),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Slider(
                        value = settings.idleOpacity.toFloat(),
                        onValueChange = { v ->
                            val next = ((v / 10f).roundToInt() * 10).coerceIn(10, 100)
                            repo.updateSettings { it.copy(idleOpacity = next) }
                            settings = repo.getSettings()
                        },
                        valueRange = 10f..100f
                    )
                }

                Text(
                    text = stringResource(R.string.settings_floating_color),
                    style = MaterialTheme.typography.titleSmall
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BUTTON_COLOR_PRESETS.forEachIndexed { index, preset ->
                        val selected = settings.colorIndex == index
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .then(
                                    if (selected) {
                                        Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                    } else {
                                        Modifier
                                    }
                                )
                                .clickable {
                                    repo.updateSettings { it.copy(colorIndex = index) }
                                    settings = repo.getSettings()
                                }
                                .padding(3.dp)
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                drawCircle(color = Color(preset.outerColor))
                                drawCircle(color = Color(preset.innerColor), radius = size.minDimension * 0.28f)
                            }
                        }
                    }
                }

                SettingsCheckboxRow(
                    title = stringResource(R.string.settings_double_tap),
                    subtitle = stringResource(R.string.settings_double_tap_desc),
                    checked = settings.enableDoubleTap,
                    onCheckedChange = { checked ->
                        repo.updateSettings { it.copy(enableDoubleTap = checked) }
                        settings = repo.getSettings()
                    }
                )

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { expandTriggers = !expandTriggers }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.settings_floating_triggers),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Icon(
                            painter = painterResource(
                                if (expandTriggers) R.drawable.ic_arrow_up else R.drawable.ic_arrow_down
                            ),
                            contentDescription = null
                        )
                    }
                }

                if (expandTriggers) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ButtonTrigger.entries.forEach { trigger ->
                            val action = mappings[trigger] ?: trigger.defaultAction
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { editingTrigger = trigger }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    ActionIcon(type = action.type, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = stringResource(trigger.labelRes),
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                        Text(
                                            text = formatActionLabel(context, action),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. SIDE HANDLER
        item {
            SettingsSectionTitle(stringResource(R.string.settings_side_title))
            Spacer(Modifier.height(8.dp))
            SettingsCard {
                Column {
                    Text(
                        text = stringResource(R.string.settings_side_width, settings.sideWidthDp),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Slider(
                        value = settings.sideWidthDp.toFloat(),
                        onValueChange = { v ->
                            val next = v.roundToInt().coerceIn(8, 32)
                            repo.updateSettings { it.copy(sideWidthDp = next) }
                            settings = repo.getSettings()
                        },
                        valueRange = 8f..32f
                    )
                }

                Column {
                    Text(
                        text = stringResource(R.string.settings_side_height, settings.sideHeightDp),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Slider(
                        value = settings.sideHeightDp.toFloat(),
                        onValueChange = { v ->
                            val next = v.roundToInt().coerceIn(50, 320)
                            repo.updateSettings { it.copy(sideHeightDp = next) }
                            settings = repo.getSettings()
                        },
                        valueRange = 50f..320f
                    )
                }

                Column {
                    val alphaText = String.format(Locale.US, "%.1f", settings.sideTransparency)
                    Text(
                        text = stringResource(R.string.settings_side_transparency, alphaText),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Slider(
                        value = settings.sideTransparency,
                        onValueChange = { v ->
                            val snapped = ((v * 10f).roundToInt() / 10f).coerceIn(0.1f, 1.0f)
                            repo.updateSettings { it.copy(sideTransparency = snapped) }
                            settings = repo.getSettings()
                        },
                        valueRange = 0.1f..1.0f
                    )
                }

                Column {
                    Text(
                        text = stringResource(R.string.settings_side_location, (settings.sideLocation * 100).roundToInt()),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Slider(
                        value = settings.sideLocation,
                        onValueChange = { v ->
                            val snapped = ((v * 20f).roundToInt() / 20f).coerceIn(0f, 1f)
                            repo.updateSettings { it.copy(sideLocation = snapped) }
                            settings = repo.getSettings()
                        },
                        valueRange = 0f..1f
                    )
                }

                val sideOptions = listOf(
                    EdgeSide.LEFT to R.string.settings_side_left,
                    EdgeSide.RIGHT to R.string.settings_side_right,
                    EdgeSide.BOTH to R.string.settings_side_both
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    sideOptions.forEachIndexed { idx, (side, labelRes) ->
                        SegmentedButton(
                            selected = settings.edgeSide == side,
                            onClick = {
                                repo.updateSettings { it.copy(edgeSide = side) }
                                settings = repo.getSettings()
                            },
                            shape = SegmentedButtonDefaults.itemShape(index = idx, count = sideOptions.size)
                        ) {
                            Text(stringResource(labelRes), maxLines = 1)
                        }
                    }
                }

                Text(
                    text = stringResource(R.string.settings_side_color),
                    style = MaterialTheme.typography.titleSmall
                )
                ColorSwatchRow(
                    colors = SIDE_HANDLE_COLOR_OPTIONS,
                    selectedColor = settings.sideColor,
                    onSelect = { color ->
                        repo.updateSettings { it.copy(sideColor = color) }
                        settings = repo.getSettings()
                    }
                )
            }
        }

        // 5. HAPTICS & LANDSCAPE
        item {
            SettingsCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_haptics),
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = stringResource(R.string.settings_haptics_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(
                        checked = settings.haptics,
                        onCheckedChange = { checked ->
                            repo.updateSettings { it.copy(haptics = checked) }
                            settings = repo.getSettings()
                        }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_hide_landscape),
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = stringResource(R.string.settings_hide_landscape_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(
                        checked = settings.hideInLandscape,
                        onCheckedChange = { checked ->
                            repo.updateSettings { it.copy(hideInLandscape = checked) }
                            settings = repo.getSettings()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(horizontal = 4.dp)
    )
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            content()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppColorThemePicker(
    selectedTheme: AppColorTheme,
    onSelect: (AppColorTheme) -> Unit
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AppColorTheme.entries.forEach { theme ->
            val selected = theme == selectedTheme
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (selected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                },
                border = BorderStroke(
                    width = if (selected) 1.2.dp else 0.8.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                ),
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { onSelect(theme) }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Canvas(modifier = Modifier.size(12.dp)) {
                        drawCircle(color = Color(theme.previewColor))
                    }
                    Text(
                        text = stringResource(theme.labelRes),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsCheckboxRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onCheckedChange(!checked) }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
            Spacer(Modifier.width(6.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorSwatchRow(
    colors: List<Int>,
    selectedColor: Int,
    onSelect: (Int) -> Unit
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        colors.forEach { colorInt ->
            val selected = colorInt == selectedColor
            val composeColor = Color(colorInt)
            val checkTint = if (composeColor.luminance() > 0.45f) Color(0xFF042F2E) else Color.White
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .then(
                        if (selected) {
                            Modifier.border(2.2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                        } else {
                            Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                        }
                    )
                    .clickable { onSelect(colorInt) }
                    .padding(3.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(color = composeColor)
                }
                if (selected) {
                    Icon(
                        painter = painterResource(R.drawable.ic_check),
                        contentDescription = null,
                        tint = checkTint,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Preview(name = "Settings Theme Picker", showBackground = true, backgroundColor = 0xFF0C0E12, widthDp = 393, heightDp = 360)
@Composable
fun SettingsThemePreview() {
    GestureTheme(themeMode = AppThemeMode.DARK, colorTheme = AppColorTheme.VIOLET, dynamicColor = false) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SettingsSectionTitle(stringResource(R.string.settings_system_title))
                SettingsCard {
                    Text(
                        text = stringResource(R.string.settings_app_color_theme),
                        style = MaterialTheme.typography.titleSmall
                    )
                    AppColorThemePicker(
                        selectedTheme = AppColorTheme.VIOLET,
                        onSelect = {}
                    )
                }
            }
        }
    }
}

@Preview(
    name = "Settings Dark Ocean",
    locale = "en",
    showBackground = true,
    backgroundColor = 0xFF0C0E12,
    widthDp = 393,
    heightDp = 851
)
@Composable
fun SettingsShowcasePreview() {
    GestureTheme(themeMode = AppThemeMode.DARK, colorTheme = AppColorTheme.OCEAN, dynamicColor = false) {
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxSize()) {
                    az.gesture.app.VeloaTopBar(
                        themeMode = AppThemeMode.DARK,
                        onCycleTheme = {},
                        onOpenAbout = {}
                    )
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 112.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            SettingsSectionTitle(stringResource(R.string.settings_system_title))
                            Spacer(Modifier.height(8.dp))
                            SettingsCard {
                                Text(
                                    text = stringResource(R.string.settings_language),
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = stringResource(AppLanguage.EN.labelRes),
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                        Icon(
                                            painter = painterResource(R.drawable.ic_arrow_down),
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Text(
                                    text = stringResource(R.string.settings_theme),
                                    style = MaterialTheme.typography.titleSmall
                                )
                                val themeOptions = listOf(
                                    Triple(AppThemeMode.LIGHT, R.drawable.ic_sun, R.string.theme_light),
                                    Triple(AppThemeMode.DARK, R.drawable.ic_moon, R.string.theme_dark),
                                    Triple(AppThemeMode.AMOLED, R.drawable.ic_theme_auto, R.string.theme_amoled)
                                )
                                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                    themeOptions.forEachIndexed { idx, (mode, iconRes, labelRes) ->
                                        SegmentedButton(
                                            selected = mode == AppThemeMode.DARK,
                                            onClick = {},
                                            shape = SegmentedButtonDefaults.itemShape(index = idx, count = themeOptions.size),
                                            icon = {
                                                Icon(
                                                    painter = painterResource(iconRes),
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        ) {
                                            Text(stringResource(labelRes), maxLines = 1)
                                        }
                                    }
                                }

                                Text(
                                    text = stringResource(R.string.settings_app_color_theme),
                                    style = MaterialTheme.typography.titleSmall
                                )
                                AppColorThemePicker(
                                    selectedTheme = AppColorTheme.OCEAN,
                                    onSelect = {}
                                )
                            }
                        }

                        item {
                            SettingsSectionTitle(stringResource(R.string.settings_gesture_options_title))
                            Spacer(Modifier.height(8.dp))
                            SettingsCard {
                                Column {
                                    Text(
                                        text = stringResource(R.string.settings_sensitivity_label),
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        text = stringResource(R.string.settings_sensitivity_value, 75),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Slider(
                                        value = 75f,
                                        onValueChange = {},
                                        valueRange = 50f..95f,
                                        steps = 8
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = stringResource(R.string.settings_sensitivity_relaxed),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = stringResource(R.string.settings_sensitivity_strict),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Text(
                                    text = stringResource(R.string.settings_gesture_color),
                                    style = MaterialTheme.typography.titleSmall
                                )
                                ColorSwatchRow(
                                    colors = GESTURE_COLOR_OPTIONS,
                                    selectedColor = GESTURE_COLOR_OPTIONS.first(),
                                    onSelect = {}
                                )

                                SettingsCheckboxRow(
                                    title = stringResource(R.string.settings_multi_stroke),
                                    subtitle = stringResource(R.string.settings_multi_stroke_desc),
                                    checked = true,
                                    onCheckedChange = {}
                                )
                            }
                        }
                    }
                }

                az.gesture.app.FloatingBottomNavBar(
                    selectedTab = az.gesture.app.AppTab.SETTINGS,
                    themeMode = AppThemeMode.DARK,
                    onSelectTab = {},
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 14.dp)
                )
            }
        }
    }
}
