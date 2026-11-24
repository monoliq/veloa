package az.gesture.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import az.gesture.app.AppTab
import az.gesture.app.FloatingBottomNavBar
import az.gesture.app.R
import az.gesture.app.VeloaTopBar
import az.gesture.app.data.ActionType
import az.gesture.app.data.AppColorTheme
import az.gesture.app.data.AppThemeMode
import az.gesture.app.data.GestureAction
import az.gesture.app.data.HandlerType
import az.gesture.app.data.SideTriggerMode
import az.gesture.app.ui.screens.GestureEditorContent
import az.gesture.app.ui.screens.HomeContent
import az.gesture.app.ui.theme.GestureTheme

private data class PreviewGestureRow(
    val title: String,
    val subtitle: String,
    val glyph: String
)

@Preview(
    name = "Home Dark Ocean",
    locale = "en",
    showBackground = true,
    backgroundColor = 0xFF0C0E12,
    widthDp = 393,
    heightDp = 851
)
@Composable
fun HomeShowcasePreview() {
    GestureTheme(
        themeMode = AppThemeMode.DARK,
        colorTheme = AppColorTheme.OCEAN,
        dynamicColor = false
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                VeloaTopBar(
                    themeMode = AppThemeMode.DARK,
                    onCycleTheme = {},
                    onOpenAbout = {}
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = innerPadding.calculateTopPadding())
            ) {
                HomeContent(
                    isServiceActive = true,
                    serviceNeedsRestart = false,
                    overlayGranted = true,
                    notificationGranted = true,
                    notificationRoadmap = "App notifications -> Show notifications -> Turn on",
                    batteryOptimizationDisabled = true,
                    batteryRoadmap = "Battery optimization -> Veloa -> Unrestricted",
                    autoStartGranted = true,
                    autoStartRoadmap = "Autostart -> Veloa -> Turn on",
                    showXiaomiPopupPermission = false,
                    xiaomiPopupGranted = false,
                    showXiaomiLockPermission = false,
                    xiaomiLockGranted = false,
                    xiaomiLockRoadmap = "",
                    handlerType = HandlerType.BOTH,
                    sideTriggerMode = SideTriggerMode.OPEN_THEN_DRAW,
                    shortcutsCount = 5,
                    showRestrictedHint = false,
                    onRequestAccessibility = {},
                    onRequestOverlay = {},
                    onRequestNotification = {},
                    onRequestBattery = {},
                    onRequestAutoStart = {},
                    onRequestXiaomiPopup = {},
                    onRequestXiaomiLock = {},
                    onOpenAppInfo = {},
                    onSelectHandlerType = {},
                    onSelectSideTriggerMode = {},
                    onOpenGesturePad = {}
                )
                FloatingBottomNavBar(
                    selectedTab = AppTab.HOME,
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

@Preview(
    name = "Gestures Dark Violet",
    locale = "en",
    showBackground = true,
    backgroundColor = 0xFF0C0E12,
    widthDp = 393,
    heightDp = 851
)
@Composable
fun GesturesShowcasePreview() {
    val rows = remember {
        listOf(
            PreviewGestureRow("Instagram", "Launch app: Instagram", "I"),
            PreviewGestureRow("Phone", "Launch app: Phone", "L"),
            PreviewGestureRow("Camera", "Launch app: Camera", "C"),
            PreviewGestureRow("Flashlight", "Toggle flashlight", "V"),
            PreviewGestureRow("Screenshot", "Screenshot", "Z")
        )
    }
    GestureTheme(
        themeMode = AppThemeMode.DARK,
        colorTheme = AppColorTheme.VIOLET,
        dynamicColor = false
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                VeloaTopBar(
                    themeMode = AppThemeMode.DARK,
                    onCycleTheme = {},
                    onOpenAbout = {}
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = innerPadding.calculateTopPadding())
            ) {
                val strokeColor = MaterialTheme.colorScheme.primary
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 112.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(R.string.saved_gestures_title),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            FilledTonalButton(
                                onClick = {},
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Text(stringResource(R.string.gestures_test_mode))
                            }
                        }
                    }

                    items(rows, key = { it.title }) { item ->
                        Card(
                            shape = RoundedCornerShape(22.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    border = BorderStroke(0.7.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                                    modifier = Modifier.size(52.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Canvas(modifier = Modifier.size(34.dp)) {
                                            val w = size.width
                                            val h = size.height
                                            val path = Path().apply {
                                                when (item.glyph) {
                                                    "I" -> {
                                                        moveTo(w * 0.50f, h * 0.14f)
                                                        lineTo(w * 0.50f, h * 0.86f)
                                                    }
                                                    "L" -> {
                                                        moveTo(w * 0.28f, h * 0.16f)
                                                        lineTo(w * 0.28f, h * 0.82f)
                                                        lineTo(w * 0.78f, h * 0.82f)
                                                    }
                                                    "C" -> {
                                                        moveTo(w * 0.76f, h * 0.24f)
                                                        cubicTo(
                                                            w * 0.18f, h * 0.10f,
                                                            w * 0.18f, h * 0.90f,
                                                            w * 0.76f, h * 0.76f
                                                        )
                                                    }
                                                    "V" -> {
                                                        moveTo(w * 0.20f, h * 0.20f)
                                                        lineTo(w * 0.50f, h * 0.82f)
                                                        lineTo(w * 0.80f, h * 0.20f)
                                                    }
                                                    else -> {
                                                        moveTo(w * 0.22f, h * 0.22f)
                                                        lineTo(w * 0.78f, h * 0.22f)
                                                        lineTo(w * 0.22f, h * 0.78f)
                                                        lineTo(w * 0.78f, h * 0.78f)
                                                    }
                                                }
                                            }
                                            drawPath(
                                                path = path,
                                                color = strokeColor,
                                                style = Stroke(
                                                    width = 3.6.dp.toPx(),
                                                    cap = StrokeCap.Round,
                                                    join = StrokeJoin.Round
                                                )
                                            )
                                        }
                                    }
                                }

                                Spacer(Modifier.width(16.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = item.subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                IconButton(onClick = {}) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_close),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                            }
                        }
                    }

                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp, bottom = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_add),
                                        contentDescription = stringResource(R.string.editor_title_new),
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                FloatingBottomNavBar(
                    selectedTab = AppTab.GESTURES,
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

@Preview(
    name = "Editor AMOLED Emerald",
    locale = "en",
    showBackground = true,
    backgroundColor = 0xFF000000,
    widthDp = 393,
    heightDp = 851
)
@Composable
fun EditorShowcasePreview() {
    GestureTheme(
        themeMode = AppThemeMode.AMOLED,
        colorTheme = AppColorTheme.EMERALD,
        dynamicColor = false
    ) {
        GestureEditorContent(
            isEditing = false,
            name = "Instagram",
            onNameChange = {},
            selectedAction = GestureAction(ActionType.LAUNCH_APP, "com.instagram.android"),
            selectedActionLabel = "Launch app: Instagram",
            strokes = listOf(
                listOf(
                    Offset(340f, 110f),
                    Offset(340f, 450f),
                    Offset(620f, 450f)
                )
            ),
            hasDrawnStroke = true,
            existingThumb = null,
            strokeColor = 0xFF34D399.toInt(),
            multiStroke = true,
            snackbarHostState = remember { SnackbarHostState() },
            onBack = {},
            onDelete = {},
            onClearDrawing = {},
            onClearAction = {},
            onPickApp = {},
            onPickOtherAction = {},
            onStrokeStart = {},
            onStrokeMove = {},
            onStrokeEnd = {},
            onSave = {}
        )
    }
}
