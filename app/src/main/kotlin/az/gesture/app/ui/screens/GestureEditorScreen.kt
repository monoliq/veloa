package az.gesture.app.ui.screens

import android.gesture.Gesture
import android.gesture.GesturePoint
import android.gesture.GestureStroke
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import az.gesture.app.R
import az.gesture.app.data.ActionType
import az.gesture.app.data.AppThemeMode
import az.gesture.app.data.GestureAction
import az.gesture.app.data.Repo
import az.gesture.app.data.Shortcut
import az.gesture.app.service.GestureAccessibilityService
import az.gesture.app.ui.components.ActionIcon
import az.gesture.app.ui.components.ActionPickerDialog
import az.gesture.app.ui.components.AppPickerDialog
import az.gesture.app.ui.components.formatActionLabel
import az.gesture.app.ui.components.resolveAppLabel
import az.gesture.app.ui.theme.GestureTheme
import kotlinx.coroutines.launch
import kotlin.math.hypot

data class TimedStrokePoint(
    val x: Float,
    val y: Float,
    val timestamp: Long
)

private fun buildAndroidGesture(strokes: List<List<TimedStrokePoint>>): Gesture? {
    val gesture = Gesture()
    var added = 0
    for (stroke in strokes) {
        if (stroke.size < 2) continue
        val pts = ArrayList<GesturePoint>(stroke.size)
        for (p in stroke) {
            pts.add(GesturePoint(p.x, p.y, p.timestamp))
        }
        gesture.addStroke(GestureStroke(pts))
        added++
    }
    return if (added > 0) gesture else null
}

private fun strokePathLength(stroke: List<TimedStrokePoint>): Float {
    var total = 0f
    for (i in 1 until stroke.size) {
        total += hypot(stroke[i].x - stroke[i - 1].x, stroke[i].y - stroke[i - 1].y)
    }
    return total
}

@Composable
fun GestureEditorScreen(
    repo: Repo,
    editingShortcut: Shortcut?,
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val settings = remember { repo.getSettings() }

    var name by remember(editingShortcut) { mutableStateOf(editingShortcut?.name ?: "") }
    var lastAutoName by remember(editingShortcut) { mutableStateOf("") }
    var selectedAction by remember(editingShortcut) {
        mutableStateOf(editingShortcut?.actions?.firstOrNull())
    }
    var completedStrokes by remember(editingShortcut) { mutableStateOf<List<List<TimedStrokePoint>>>(emptyList()) }
    var activeStroke by remember(editingShortcut) { mutableStateOf<List<TimedStrokePoint>>(emptyList()) }
    var drawnGesture by remember(editingShortcut) { mutableStateOf<Gesture?>(null) }
    var showActionPicker by remember { mutableStateOf(false) }
    var showQuickAppPicker by remember { mutableStateOf(false) }

    val strokeColor = settings.gestureColor
    val existingThumb = remember(editingShortcut, strokeColor) {
        editingShortcut?.let { repo.getGestureThumbnail(it.id, 300, 300, strokeColor)?.asImageBitmap() }
    }

    if (showQuickAppPicker) {
        AppPickerDialog(
            onDismiss = { showQuickAppPicker = false },
            onAppSelected = { pkg ->
                selectedAction = GestureAction(ActionType.LAUNCH_APP, pkg)
                val appLabel = resolveAppLabel(context, pkg) ?: ""
                if (name.isBlank() || name == lastAutoName) {
                    name = appLabel
                    lastAutoName = appLabel
                }
                showQuickAppPicker = false
            }
        )
    }

    if (showActionPicker) {
        ActionPickerDialog(
            includeNone = false,
            includeOpenPad = false,
            onDismiss = { showActionPicker = false },
            onActionSelected = { chosen ->
                selectedAction = chosen
                val actionLabel = if (chosen.type == ActionType.LAUNCH_APP) {
                    resolveAppLabel(context, chosen.param) ?: ""
                } else {
                    context.getString(chosen.type.labelRes)
                }
                if (name.isBlank() || name == lastAutoName) {
                    name = actionLabel
                    lastAutoName = actionLabel
                }
                showActionPicker = false
            }
        )
    }

    val allOffsets = remember(completedStrokes, activeStroke) {
        buildList {
            completedStrokes.forEach { stroke ->
                add(stroke.map { Offset(it.x, it.y) })
            }
            if (activeStroke.isNotEmpty()) {
                add(activeStroke.map { Offset(it.x, it.y) })
            }
        }
    }

    GestureEditorContent(
        isEditing = editingShortcut != null,
        name = name,
        onNameChange = { name = it },
        selectedAction = selectedAction,
        selectedActionLabel = selectedAction?.let { formatActionLabel(context, it) },
        strokes = allOffsets,
        hasDrawnStroke = drawnGesture != null,
        existingThumb = existingThumb,
        strokeColor = strokeColor,
        multiStroke = settings.multiStroke,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onDelete = {
            editingShortcut?.let {
                repo.deleteShortcut(it.id)
                GestureAccessibilityService.instance?.refreshOverlays()
                onSaved()
            }
        },
        onClearDrawing = {
            completedStrokes = emptyList()
            activeStroke = emptyList()
            drawnGesture = null
        },
        onClearAction = { selectedAction = null },
        onPickApp = { showQuickAppPicker = true },
        onPickOtherAction = { showActionPicker = true },
        onStrokeStart = { pt ->
            if (!settings.multiStroke) {
                completedStrokes = emptyList()
                drawnGesture = null
            }
            activeStroke = listOf(pt)
        },
        onStrokeMove = { pt ->
            val current = activeStroke
            val last = current.lastOrNull()
            if (last == null || hypot(pt.x - last.x, pt.y - last.y) >= 2f) {
                activeStroke = current + pt
            }
        },
        onStrokeEnd = {
            val finished = activeStroke
            activeStroke = emptyList()
            if (finished.size >= 2 && strokePathLength(finished) >= 12f) {
                val updated = completedStrokes + listOf(finished)
                completedStrokes = updated
                drawnGesture = buildAndroidGesture(updated)
            }
        },
        onSave = {
            val trimmedName = name.trim()
            val finalGesture = drawnGesture
            val action = selectedAction
            when {
                trimmedName.isEmpty() -> {
                    scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.editor_error_name)) }
                }
                editingShortcut == null && finalGesture == null -> {
                    scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.editor_error_draw)) }
                }
                action == null -> {
                    scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.editor_error_actions)) }
                }
                else -> {
                    val shortcut = Shortcut(
                        id = editingShortcut?.id ?: java.util.UUID.randomUUID().toString(),
                        name = trimmedName,
                        actions = listOf(action)
                    )
                    repo.saveShortcut(shortcut, finalGesture)
                    GestureAccessibilityService.instance?.refreshOverlays()
                    onSaved()
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GestureEditorContent(
    isEditing: Boolean,
    name: String,
    onNameChange: (String) -> Unit,
    selectedAction: GestureAction?,
    selectedActionLabel: String?,
    strokes: List<List<Offset>>,
    hasDrawnStroke: Boolean,
    existingThumb: ImageBitmap?,
    strokeColor: Int,
    multiStroke: Boolean,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onDelete: () -> Unit,
    onClearDrawing: () -> Unit,
    onClearAction: () -> Unit,
    onPickApp: () -> Unit,
    onPickOtherAction: () -> Unit,
    onStrokeStart: (TimedStrokePoint) -> Unit,
    onStrokeMove: (TimedStrokePoint) -> Unit,
    onStrokeEnd: () -> Unit,
    onSave: () -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (!isEditing) R.string.editor_title_new else R.string.editor_title_edit
                        )
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_action_back),
                            contentDescription = stringResource(R.string.editor_cancel)
                        )
                    }
                },
                actions = {
                    if (isEditing) {
                        IconButton(onClick = onDelete) {
                            Icon(
                                painter = painterResource(R.drawable.ic_delete),
                                contentDescription = stringResource(R.string.editor_delete),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.editor_draw_prompt),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                FilledTonalButton(
                    onClick = onClearDrawing,
                    enabled = strokes.isNotEmpty(),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text = stringResource(R.string.editor_clear_drawing),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            PersistentGesturePad(
                strokes = strokes,
                hasDrawnStroke = hasDrawnStroke,
                existingThumb = existingThumb,
                strokeColor = strokeColor,
                multiStroke = multiStroke,
                onStrokeStart = onStrokeStart,
                onStrokeMove = onStrokeMove,
                onStrokeEnd = onStrokeEnd
            )

            if (hasDrawnStroke) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                ) {
                    Text(
                        text = stringResource(R.string.editor_stroke_ready),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            } else if (isEditing) {
                Text(
                    text = stringResource(R.string.editor_existing_gesture_hint),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilledTonalButton(
                    onClick = onPickApp,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_action_apps),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.editor_select_app_quick), maxLines = 1)
                }

                OutlinedButton(
                    onClick = onPickOtherAction,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_add),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.editor_other_action_quick), maxLines = 1)
                }
            }

            Text(
                text = stringResource(R.string.editor_selected_action),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )

            if (selectedAction != null && selectedActionLabel != null) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                ActionIcon(
                                    type = selectedAction.type,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = selectedActionLabel,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        IconButton(
                            onClick = onClearAction,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_close),
                                contentDescription = stringResource(R.string.editor_delete),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Text(
                        text = stringResource(R.string.editor_no_action_yet),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 16.dp)
                    )
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = onNameChange,
                label = { Text(stringResource(R.string.editor_name_label)) },
                placeholder = { Text(stringResource(R.string.editor_name_placeholder)) },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(4.dp))

            Button(
                onClick = onSave,
                shape = RoundedCornerShape(26.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_check),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.editor_save))
            }
        }
    }
}

@Composable
private fun PersistentGesturePad(
    strokes: List<List<Offset>>,
    hasDrawnStroke: Boolean,
    existingThumb: ImageBitmap?,
    strokeColor: Int,
    multiStroke: Boolean,
    onStrokeStart: (TimedStrokePoint) -> Unit,
    onStrokeMove: (TimedStrokePoint) -> Unit,
    onStrokeEnd: () -> Unit
) {
    val activeHighlight = hasDrawnStroke || strokes.isNotEmpty()
    val dotGridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)
    val composeStrokeColor = Color(strokeColor)

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            width = if (activeHighlight) 1.5.dp else 1.dp,
            color = if (activeHighlight) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(multiStroke) {
                    awaitEachGesture {
                        val down: PointerInputChange = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        onStrokeStart(
                            TimedStrokePoint(
                                down.position.x,
                                down.position.y,
                                down.uptimeMillis
                            )
                        )
                        val pointerId = down.id
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                            if (change.pressed) {
                                change.consume()
                                onStrokeMove(
                                    TimedStrokePoint(
                                        change.position.x,
                                        change.position.y,
                                        change.uptimeMillis
                                    )
                                )
                            } else {
                                change.consume()
                                onStrokeEnd()
                                break
                            }
                        }
                    }
                }
        ) {
            if (strokes.isEmpty() && existingThumb == null) {
                Text(
                    text = stringResource(R.string.editor_draw_placeholder),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.50f),
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            if (strokes.isEmpty() && existingThumb != null) {
                Image(
                    bitmap = existingThumb,
                    contentDescription = null,
                    modifier = Modifier
                        .size(160.dp)
                        .align(Alignment.Center)
                        .alpha(0.45f)
                )
            }

            Canvas(modifier = Modifier.fillMaxSize()) {
                val spacing = 22.dp.toPx()
                val dotRadius = 1.2.dp.toPx()
                var x = spacing * 0.8f
                while (x < size.width - spacing * 0.4f) {
                    var y = spacing * 0.8f
                    while (y < size.height - spacing * 0.4f) {
                        drawCircle(
                            color = dotGridColor,
                            radius = dotRadius,
                            center = Offset(x, y)
                        )
                        y += spacing
                    }
                    x += spacing
                }

                val glowWidth = 13.dp.toPx()
                val coreWidth = 5.5.dp.toPx()
                for (pts in strokes) {
                    if (pts.isEmpty()) continue
                    if (pts.size == 1) {
                        drawCircle(
                            color = composeStrokeColor,
                            radius = coreWidth * 0.6f,
                            center = pts.first()
                        )
                        continue
                    }
                    val path = Path().apply {
                        moveTo(pts.first().x, pts.first().y)
                        for (i in 1 until pts.size) {
                            val prev = pts[i - 1]
                            val curr = pts[i]
                            quadraticTo(
                                prev.x,
                                prev.y,
                                (prev.x + curr.x) * 0.5f,
                                (prev.y + curr.y) * 0.5f
                            )
                        }
                        lineTo(pts.last().x, pts.last().y)
                    }
                    drawPath(
                        path = path,
                        color = composeStrokeColor.copy(alpha = 0.22f),
                        style = Stroke(width = glowWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                    drawPath(
                        path = path,
                        color = composeStrokeColor,
                        style = Stroke(width = coreWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                    drawCircle(
                        color = composeStrokeColor,
                        radius = 4.5.dp.toPx(),
                        center = pts.first()
                    )
                }
            }
        }
    }
}

@Preview(name = "Editor AMOLED", showBackground = true, backgroundColor = 0xFF000000, widthDp = 393, heightDp = 851)
@Composable
fun GestureEditorPreview() {
    GestureTheme(themeMode = AppThemeMode.AMOLED, dynamicColor = false) {
        GestureEditorContent(
            isEditing = true,
            name = "Instagram",
            onNameChange = {},
            selectedAction = GestureAction(ActionType.LAUNCH_APP, "com.instagram.android"),
            selectedActionLabel = "Launch app: Instagram",
            strokes = listOf(
                listOf(
                    Offset(340f, 120f),
                    Offset(340f, 440f),
                    Offset(620f, 440f)
                )
            ),
            hasDrawnStroke = true,
            existingThumb = null,
            strokeColor = 0xFF2DD4BF.toInt(),
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
