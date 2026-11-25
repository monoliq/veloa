package az.gesture.app.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import az.gesture.app.R
import az.gesture.app.data.AppThemeMode
import az.gesture.app.data.HandlerType
import az.gesture.app.data.Repo
import az.gesture.app.data.SideTriggerMode
import az.gesture.app.service.BackgroundPermissionHelper
import az.gesture.app.service.GestureAccessibilityService
import az.gesture.app.service.VeloaKeepAliveService
import az.gesture.app.ui.theme.GestureTheme

@SuppressLint("BatteryLife")
@Composable
fun HomeScreen(
    repo: Repo,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val flowRunning by GestureAccessibilityService.isRunning.collectAsState()
    var resumedRunning by remember { mutableStateOf(GestureAccessibilityService.instance != null) }
    var enabledInSystem by remember { mutableStateOf(GestureAccessibilityService.isEnabledInSystemSettings(context)) }
    var overlayGranted by remember { mutableStateOf(BackgroundPermissionHelper.isOverlayPermissionGranted(context)) }
    var notificationGranted by remember { mutableStateOf(BackgroundPermissionHelper.isNotificationPermissionGranted(context)) }
    var notificationRequestedOnce by remember { mutableStateOf(repo.wasNotificationRequested()) }
    var batteryIgnored by remember { mutableStateOf(BackgroundPermissionHelper.isBatteryOptimizationDisabled(context)) }
    var backgroundRestricted by remember { mutableStateOf(BackgroundPermissionHelper.isBackgroundRestricted(context)) }
    var autoStartConfirmed by remember { mutableStateOf(repo.isAutoStartConfirmed()) }
    var xiaomiPopupConfirmed by remember { mutableStateOf(repo.isXiaomiPopupConfirmed()) }
    var settings by remember { mutableStateOf(repo.getSettings()) }
    var shortcutsCount by remember { mutableIntStateOf(repo.getShortcuts().size) }

    val autoStartTarget = remember(context) { BackgroundPermissionHelper.resolveAutoStartTarget(context) }
    val hasXiaomiPopup = remember(context) { BackgroundPermissionHelper.hasXiaomiBackgroundPopupSetting(context) }
    val canDirectBatteryDialog = remember(context) { BackgroundPermissionHelper.canLaunchDirectBatteryDialog(context) }

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        repo.markNotificationRequested()
        notificationRequestedOnce = true
        notificationGranted = granted || BackgroundPermissionHelper.isNotificationPermissionGranted(context)
        if (notificationGranted) {
            GestureAccessibilityService.instance?.ensureForegroundNotification()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                resumedRunning = GestureAccessibilityService.instance != null
                enabledInSystem = GestureAccessibilityService.isEnabledInSystemSettings(context)
                overlayGranted = BackgroundPermissionHelper.isOverlayPermissionGranted(context)
                notificationGranted = BackgroundPermissionHelper.isNotificationPermissionGranted(context)
                notificationRequestedOnce = repo.wasNotificationRequested()
                batteryIgnored = BackgroundPermissionHelper.isBatteryOptimizationDisabled(context)
                backgroundRestricted = BackgroundPermissionHelper.isBackgroundRestricted(context)
                autoStartConfirmed = repo.isAutoStartConfirmed()
                xiaomiPopupConfirmed = repo.isXiaomiPopupConfirmed()
                settings = repo.getSettings()
                shortcutsCount = repo.getShortcuts().size
                if (resumedRunning || enabledInSystem || overlayGranted) {
                    VeloaKeepAliveService.start(context)
                    if (notificationGranted) {
                        GestureAccessibilityService.instance?.ensureForegroundNotification()
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val isServiceActive = flowRunning || resumedRunning
    val serviceNeedsRestart = enabledInSystem && !isServiceActive
    val useNotificationDialog =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationGranted && !notificationRequestedOnce

    HomeContent(
        isServiceActive = isServiceActive,
        serviceNeedsRestart = serviceNeedsRestart,
        overlayGranted = overlayGranted,
        notificationGranted = notificationGranted,
        notificationRoadmap = stringResource(
            if (useNotificationDialog) {
                R.string.perm_roadmap_notification_dialog
            } else {
                R.string.perm_roadmap_notification_settings
            }
        ),
        batteryOptimizationDisabled = batteryIgnored,
        batteryRoadmap = stringResource(
            if (canDirectBatteryDialog) {
                R.string.perm_roadmap_battery_dialog
            } else {
                R.string.perm_roadmap_battery_list
            }
        ),
        autoStartGranted = autoStartConfirmed && !backgroundRestricted,
        autoStartRoadmap = stringResource(autoStartTarget.kind.roadmapRes),
        showXiaomiPopupPermission = hasXiaomiPopup,
        xiaomiPopupGranted = xiaomiPopupConfirmed,
        handlerType = settings.handlerType,
        sideTriggerMode = settings.sideTriggerMode,
        shortcutsCount = shortcutsCount,
        showRestrictedHint = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !isServiceActive && !enabledInSystem,
        onRequestAccessibility = { BackgroundPermissionHelper.openAccessibilitySettings(context) },
        onRequestOverlay = { BackgroundPermissionHelper.openOverlayPermissionSettings(context) },
        onRequestNotification = {
            if (useNotificationDialog) {
                repo.markNotificationRequested()
                notificationRequestedOnce = true
                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                BackgroundPermissionHelper.openAppNotificationSettings(context)
            }
        },
        onRequestBattery = { BackgroundPermissionHelper.openBatteryOptimizationSettings(context) },
        onRequestAutoStart = {
            repo.setAutoStartConfirmed(true)
            autoStartConfirmed = true
            BackgroundPermissionHelper.openAutoStartSettings(context)
        },
        onRequestXiaomiPopup = {
            repo.setXiaomiPopupConfirmed(true)
            xiaomiPopupConfirmed = true
            BackgroundPermissionHelper.openXiaomiBackgroundPopupSetting(context)
        },
        onOpenAppInfo = { BackgroundPermissionHelper.openAppInfo(context) },
        onSelectHandlerType = { type ->
            repo.updateSettings { it.copy(handlerType = type) }
            settings = repo.getSettings()
        },
        onSelectSideTriggerMode = { mode ->
            repo.updateSettings { it.copy(sideTriggerMode = mode) }
            settings = repo.getSettings()
        },
        onOpenGesturePad = {
            val svc = GestureAccessibilityService.instance
            if (svc != null) {
                svc.showGesturePad(fromEdge = false)
            } else {
                VeloaKeepAliveService.start(context)
                VeloaKeepAliveService.instance?.showFallbackGesturePad(fromEdge = false)
            }
        },
        modifier = modifier,
        listState = listState
    )
}

@Composable
fun HomeContent(
    isServiceActive: Boolean,
    serviceNeedsRestart: Boolean,
    overlayGranted: Boolean,
    notificationGranted: Boolean,
    notificationRoadmap: String,
    batteryOptimizationDisabled: Boolean,
    batteryRoadmap: String,
    autoStartGranted: Boolean,
    autoStartRoadmap: String,
    showXiaomiPopupPermission: Boolean,
    xiaomiPopupGranted: Boolean,
    handlerType: HandlerType,
    sideTriggerMode: SideTriggerMode,
    shortcutsCount: Int,
    showRestrictedHint: Boolean,
    onRequestAccessibility: () -> Unit,
    onRequestOverlay: () -> Unit,
    onRequestNotification: () -> Unit,
    onRequestBattery: () -> Unit,
    onRequestAutoStart: () -> Unit,
    onRequestXiaomiPopup: () -> Unit,
    onOpenAppInfo: () -> Unit,
    onSelectHandlerType: (HandlerType) -> Unit,
    onSelectSideTriggerMode: (SideTriggerMode) -> Unit,
    onOpenGesturePad: () -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState()
) {
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.home_welcome),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold
                )
                val ready = isServiceActive || overlayGranted
                val dotColor = if (ready) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        Canvas(modifier = Modifier.size(8.dp)) {
                            drawCircle(color = dotColor)
                        }
                        Text(
                            text = stringResource(R.string.stats_gestures_count, shortcutsCount),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = stringResource(R.string.required_permissions_title),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )

                    PermissionCheckRow(
                        label = stringResource(R.string.perm_accessibility),
                        roadmap = stringResource(R.string.perm_roadmap_accessibility),
                        checked = isServiceActive,
                        onClick = onRequestAccessibility
                    )

                    if (serviceNeedsRestart) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 46.dp, bottom = 4.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable(onClick = onRequestAccessibility)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.perm_accessibility_restart_hint),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    } else if (showRestrictedHint) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                            border = BorderStroke(0.7.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 46.dp, top = 2.dp, bottom = 4.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(onClick = onOpenAppInfo)
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.btn_open_app_info),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = stringResource(R.string.perm_roadmap_restricted),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }

                    if (!serviceNeedsRestart) {
                        val isLightBg = MaterialTheme.colorScheme.background.luminance() > 0.5f
                        val noticeRedText = if (isLightBg) Color(0xFFC81E32) else Color(0xFFFF5C77)
                        AnimatedContent(
                            targetState = isServiceActive,
                            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
                            label = "accessibilityNotice"
                        ) { active ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = Color(0xFFFF2A55).copy(alpha = if (isLightBg) 0.06f else 0.10f),
                                border = BorderStroke(0.8.dp, Color(0xFFFF4D6D).copy(alpha = 0.40f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 36.dp, top = 2.dp, bottom = 6.dp)
                            ) {
                                Text(
                                    text = stringResource(
                                        if (active) {
                                            R.string.perm_accessibility_active_bank_notice
                                        } else {
                                            R.string.perm_accessibility_inactive_limit_notice
                                        }
                                    ),
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = noticeRedText,
                                        lineHeight = 16.sp
                                    ),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }

                    PermissionDivider()

                    PermissionCheckRow(
                        label = stringResource(R.string.perm_overlay),
                        roadmap = stringResource(R.string.perm_roadmap_overlay),
                        checked = overlayGranted,
                        onClick = onRequestOverlay
                    )

                    PermissionDivider()

                    PermissionCheckRow(
                        label = stringResource(R.string.perm_notification),
                        roadmap = notificationRoadmap,
                        checked = notificationGranted,
                        onClick = onRequestNotification
                    )

                    PermissionDivider()

                    PermissionCheckRow(
                        label = stringResource(R.string.perm_battery),
                        roadmap = batteryRoadmap,
                        checked = batteryOptimizationDisabled,
                        onClick = onRequestBattery
                    )

                    PermissionDivider()

                    PermissionCheckRow(
                        label = stringResource(R.string.perm_autostart),
                        roadmap = autoStartRoadmap,
                        checked = autoStartGranted,
                        onClick = onRequestAutoStart
                    )

                    if (showXiaomiPopupPermission) {
                        PermissionDivider()

                        PermissionCheckRow(
                            label = stringResource(R.string.perm_xiaomi_popup),
                            roadmap = stringResource(R.string.perm_roadmap_xiaomi_popup),
                            checked = xiaomiPopupGranted,
                            onClick = onRequestXiaomiPopup
                        )
                    }
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = stringResource(R.string.handler_type_title),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )

                    HandlerTypeOptionRow(
                        title = stringResource(R.string.handler_side),
                        subtitle = stringResource(R.string.handler_side_desc),
                        selected = handlerType == HandlerType.SIDE,
                        onClick = { onSelectHandlerType(HandlerType.SIDE) }
                    )

                    HandlerTypeOptionRow(
                        title = stringResource(R.string.handler_floating),
                        subtitle = stringResource(R.string.handler_floating_desc),
                        selected = handlerType == HandlerType.FLOATING,
                        onClick = { onSelectHandlerType(HandlerType.FLOATING) }
                    )

                    HandlerTypeOptionRow(
                        title = stringResource(R.string.handler_both),
                        subtitle = stringResource(R.string.handler_both_desc),
                        selected = handlerType == HandlerType.BOTH,
                        onClick = { onSelectHandlerType(HandlerType.BOTH) }
                    )

                    val showSideActivationMode =
                        handlerType == HandlerType.SIDE || handlerType == HandlerType.BOTH
                    AnimatedVisibility(
                        visible = showSideActivationMode,
                        enter = fadeIn(tween(240)) + expandVertically(tween(240)),
                        exit = fadeOut(tween(180)) + shrinkVertically(tween(180))
                    ) {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 2.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.settings_side_mode_title),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                                CompactModeOptionRow(
                                    title = stringResource(R.string.settings_side_mode_two_step),
                                    subtitle = stringResource(R.string.settings_side_mode_two_step_desc),
                                    selected = sideTriggerMode == SideTriggerMode.OPEN_THEN_DRAW,
                                    onClick = { onSelectSideTriggerMode(SideTriggerMode.OPEN_THEN_DRAW) }
                                )
                                CompactModeOptionRow(
                                    title = stringResource(R.string.settings_side_mode_direct),
                                    subtitle = stringResource(R.string.settings_side_mode_direct_desc),
                                    selected = sideTriggerMode == SideTriggerMode.DIRECT_DRAW,
                                    onClick = { onSelectSideTriggerMode(SideTriggerMode.DIRECT_DRAW) }
                                )
                            }
                        }
                    }

                    val showFloatingHint =
                        handlerType == HandlerType.FLOATING || handlerType == HandlerType.BOTH
                    AnimatedVisibility(
                        visible = showFloatingHint,
                        enter = fadeIn(tween(240)) + expandVertically(tween(240)),
                        exit = fadeOut(tween(180)) + shrinkVertically(tween(180))
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                            border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 2.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.handler_floating_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End
                    ) {
                        FilledTonalButton(
                            onClick = onOpenGesturePad,
                            enabled = isServiceActive || overlayGranted,
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_action_gesture_pad),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.btn_open_pad_now))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionDivider() {
    HorizontalDivider(
        thickness = 0.6.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
        modifier = Modifier.padding(start = 36.dp)
    )
}

@Composable
private fun PermissionCheckRow(
    label: String,
    roadmap: String,
    checked: Boolean,
    onClick: () -> Unit
) {
    val badgeFill by animateColorAsState(
        targetValue = if (checked) MaterialTheme.colorScheme.primary else Color.Transparent,
        animationSpec = tween(200),
        label = "permBadgeFill"
    )
    val badgeBorder by animateColorAsState(
        targetValue = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        animationSpec = tween(200),
        label = "permBadgeBorder"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .padding(top = 2.dp)
                .size(22.dp)
                .clip(CircleShape)
                .background(badgeFill)
                .border(1.5.dp, badgeBorder, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (checked) {
                Icon(
                    painter = painterResource(R.drawable.ic_check),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(13.dp)
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = roadmap,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
private fun HandlerTypeOptionRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val bgColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        animationSpec = tween(200),
        label = "handlerBg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.60f)
        } else {
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
        },
        animationSpec = tween(200),
        label = "handlerBorder"
    )

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = bgColor,
        border = BorderStroke(if (selected) 1.1.dp else 0.7.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = selected,
                onClick = onClick
            )
            Spacer(Modifier.width(6.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
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

@Composable
private fun CompactModeOptionRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val bgColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.65f)
        },
        animationSpec = tween(180),
        label = "modeBg"
    )

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = bgColor,
        border = if (selected) {
            BorderStroke(0.9.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f))
        } else {
            null
        },
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = selected,
                onClick = onClick
            )
            Spacer(Modifier.width(4.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
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

@Preview(name = "Home AMOLED", showBackground = true, backgroundColor = 0xFF000000, widthDp = 393, heightDp = 851)
@Composable
fun HomeScreenPreview() {
    GestureTheme(themeMode = AppThemeMode.AMOLED, dynamicColor = false) {
        Surface(color = MaterialTheme.colorScheme.background) {
            HomeContent(
                isServiceActive = true,
                serviceNeedsRestart = false,
                overlayGranted = true,
                notificationGranted = true,
                notificationRoadmap = "Tətbiq bildirişləri → Bildirişləri göstər → Aktiv et",
                batteryOptimizationDisabled = true,
                batteryRoadmap = "Batareya sorğusu → İcazə ver",
                autoStartGranted = false,
                autoStartRoadmap = "Avtomatik başlatma → Veloa → Aktiv et",
                showXiaomiPopupPermission = true,
                xiaomiPopupGranted = false,
                handlerType = HandlerType.SIDE,
                sideTriggerMode = SideTriggerMode.OPEN_THEN_DRAW,
                shortcutsCount = 5,
                showRestrictedHint = false,
                onRequestAccessibility = {},
                onRequestOverlay = {},
                onRequestNotification = {},
                onRequestBattery = {},
                onRequestAutoStart = {},
                onRequestXiaomiPopup = {},
                onOpenAppInfo = {},
                onSelectHandlerType = {},
                onSelectSideTriggerMode = {},
                onOpenGesturePad = {}
            )
        }
    }
}
