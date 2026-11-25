package az.gesture.app.ui.components

import android.content.Context
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import az.gesture.app.R
import az.gesture.app.data.Repo
import az.gesture.app.service.BackgroundPermissionHelper
import az.gesture.app.service.VeloaKeepAliveService

enum class InitialPermissionStep {
    OVERLAY,
    BATTERY,
    AUTOSTART,
    XIAOMI_POPUP
}

fun buildInitialPermissionSteps(hasXiaomiPopup: Boolean): List<InitialPermissionStep> = buildList {
    add(InitialPermissionStep.OVERLAY)
    add(InitialPermissionStep.BATTERY)
    add(InitialPermissionStep.AUTOSTART)
    if (hasXiaomiPopup) {
        add(InitialPermissionStep.XIAOMI_POPUP)
    }
}

fun nextPendingInitialPermissionStep(
    steps: List<InitialPermissionStep>,
    granted: Set<InitialPermissionStep>,
    skipped: Set<InitialPermissionStep>
): InitialPermissionStep? = steps.firstOrNull { it !in granted && it !in skipped }

fun hasPendingInitialPermissions(context: Context, repo: Repo): Boolean {
    val hasXiaomiPopup = BackgroundPermissionHelper.hasXiaomiBackgroundPopupSetting(context)
    val steps = buildInitialPermissionSteps(hasXiaomiPopup)
    val granted = buildSet {
        if (BackgroundPermissionHelper.isOverlayPermissionGranted(context)) {
            add(InitialPermissionStep.OVERLAY)
        }
        if (BackgroundPermissionHelper.isBatteryOptimizationDisabled(context)) {
            add(InitialPermissionStep.BATTERY)
        }
        if (repo.isAutoStartConfirmed() && !BackgroundPermissionHelper.isBackgroundRestricted(context)) {
            add(InitialPermissionStep.AUTOSTART)
        }
        if (hasXiaomiPopup && repo.isXiaomiPopupConfirmed()) {
            add(InitialPermissionStep.XIAOMI_POPUP)
        }
    }
    return nextPendingInitialPermissionStep(steps, granted, emptySet()) != null
}

@Composable
fun InitialPermissionsDialog(
    repo: Repo,
    onFinished: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val autoStartTarget = remember(context) { BackgroundPermissionHelper.resolveAutoStartTarget(context) }
    val hasXiaomiPopup = remember(context) { BackgroundPermissionHelper.hasXiaomiBackgroundPopupSetting(context) }
    val canDirectBatteryDialog = remember(context) { BackgroundPermissionHelper.canLaunchDirectBatteryDialog(context) }
    val steps = remember(hasXiaomiPopup) { buildInitialPermissionSteps(hasXiaomiPopup) }

    var overlayGranted by remember { mutableStateOf(BackgroundPermissionHelper.isOverlayPermissionGranted(context)) }
    var batteryIgnored by remember { mutableStateOf(BackgroundPermissionHelper.isBatteryOptimizationDisabled(context)) }
    var backgroundRestricted by remember { mutableStateOf(BackgroundPermissionHelper.isBackgroundRestricted(context)) }
    var autoStartConfirmed by remember { mutableStateOf(repo.isAutoStartConfirmed()) }
    var xiaomiPopupConfirmed by remember { mutableStateOf(repo.isXiaomiPopupConfirmed()) }
    var skippedSteps by remember { mutableStateOf(emptySet<InitialPermissionStep>()) }

    val grantedSteps = remember(
        overlayGranted,
        batteryIgnored,
        backgroundRestricted,
        autoStartConfirmed,
        xiaomiPopupConfirmed
    ) {
        buildSet {
            if (overlayGranted) add(InitialPermissionStep.OVERLAY)
            if (batteryIgnored) add(InitialPermissionStep.BATTERY)
            if (autoStartConfirmed && !backgroundRestricted) add(InitialPermissionStep.AUTOSTART)
            if (hasXiaomiPopup && xiaomiPopupConfirmed) add(InitialPermissionStep.XIAOMI_POPUP)
        }
    }

    val activeStep = remember(steps, grantedSteps, skippedSteps) {
        nextPendingInitialPermissionStep(steps, grantedSteps, skippedSteps)
    }

    LaunchedEffect(activeStep) {
        if (activeStep == null) {
            repo.setInitialPermissionFlowDone(true)
            onFinished()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                overlayGranted = BackgroundPermissionHelper.isOverlayPermissionGranted(context)
                batteryIgnored = BackgroundPermissionHelper.isBatteryOptimizationDisabled(context)
                backgroundRestricted = BackgroundPermissionHelper.isBackgroundRestricted(context)
                autoStartConfirmed = repo.isAutoStartConfirmed()
                xiaomiPopupConfirmed = repo.isXiaomiPopupConfirmed()
                if (overlayGranted) {
                    VeloaKeepAliveService.start(context)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val currentStep = activeStep ?: return
    val activeIndex = steps.indexOf(currentStep).coerceAtLeast(0)

    val batteryRoadmap = stringResource(
        if (canDirectBatteryDialog) {
            R.string.perm_roadmap_battery_dialog
        } else {
            R.string.perm_roadmap_battery_list
        }
    )
    val autoStartRoadmap = stringResource(autoStartTarget.kind.roadmapRes)

    fun launchStep(step: InitialPermissionStep) {
        when (step) {
            InitialPermissionStep.OVERLAY -> {
                BackgroundPermissionHelper.openOverlayPermissionSettings(context)
            }
            InitialPermissionStep.BATTERY -> {
                BackgroundPermissionHelper.openBatteryOptimizationSettings(context)
            }
            InitialPermissionStep.AUTOSTART -> {
                repo.setAutoStartConfirmed(true)
                BackgroundPermissionHelper.openAutoStartSettings(context)
            }
            InitialPermissionStep.XIAOMI_POPUP -> {
                repo.setXiaomiPopupConfirmed(true)
                BackgroundPermissionHelper.openXiaomiBackgroundPopupSetting(context)
            }
        }
    }

    fun stepLabelRes(step: InitialPermissionStep): Int = when (step) {
        InitialPermissionStep.OVERLAY -> R.string.perm_overlay
        InitialPermissionStep.BATTERY -> R.string.perm_battery
        InitialPermissionStep.AUTOSTART -> R.string.perm_autostart
        InitialPermissionStep.XIAOMI_POPUP -> R.string.perm_xiaomi_popup
    }

    @Composable
    fun stepRoadmap(step: InitialPermissionStep): String = when (step) {
        InitialPermissionStep.OVERLAY -> stringResource(R.string.perm_roadmap_overlay)
        InitialPermissionStep.BATTERY -> batteryRoadmap
        InitialPermissionStep.AUTOSTART -> autoStartRoadmap
        InitialPermissionStep.XIAOMI_POPUP -> stringResource(R.string.perm_roadmap_xiaomi_popup)
    }

    AlertDialog(
        onDismissRequest = {
            repo.setInitialPermissionFlowDone(true)
            onFinished()
        },
        shape = RoundedCornerShape(26.dp),
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.initial_perm_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "${activeIndex + 1} / ${steps.size}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.initial_perm_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                steps.forEachIndexed { idx, step ->
                    val isGranted = step in grantedSteps
                    val isActive = step == currentStep
                    InitialPermissionStepRow(
                        index = idx + 1,
                        title = stringResource(stepLabelRes(step)),
                        roadmap = if (isActive) stepRoadmap(step) else null,
                        granted = isGranted,
                        active = isActive,
                        onClick = {
                            if (!isGranted) {
                                launchStep(step)
                            }
                        }
                    )
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    skippedSteps = skippedSteps + currentStep
                }
            ) {
                Text(stringResource(R.string.initial_perm_skip))
            }
        },
        confirmButton = {
            Button(
                onClick = { launchStep(currentStep) },
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(stringResource(R.string.initial_perm_grant))
            }
        }
    )
}

@Composable
private fun InitialPermissionStepRow(
    index: Int,
    title: String,
    roadmap: String?,
    granted: Boolean,
    active: Boolean,
    onClick: () -> Unit
) {
    val bgColor by animateColorAsState(
        targetValue = when {
            active -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.48f)
            else -> MaterialTheme.colorScheme.surfaceContainerLow
        },
        animationSpec = tween(200),
        label = "initPermBg"
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            active -> MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
            else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
        },
        animationSpec = tween(200),
        label = "initPermBorder"
    )

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = bgColor,
        border = BorderStroke(if (active) 1.1.dp else 0.7.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = !granted, onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 1.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            granted -> MaterialTheme.colorScheme.primary
                            active -> MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                            else -> Color.Transparent
                        }
                    )
                    .border(
                        width = 1.4.dp,
                        color = if (granted || active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (granted) {
                    Icon(
                        painter = painterResource(R.drawable.ic_check),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(13.dp)
                    )
                } else {
                    Text(
                        text = index.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (granted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                )
                if (!roadmap.isNullOrBlank()) {
                    Text(
                        text = roadmap,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}
