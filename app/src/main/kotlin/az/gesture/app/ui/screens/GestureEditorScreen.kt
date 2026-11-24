package az.gesture.app.ui.screens

import android.gesture.Gesture
import android.gesture.GestureOverlayView
import android.view.MotionEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
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
import az.gesture.app.ui.components.ScrollSafeGestureOverlayView
import az.gesture.app.ui.components.formatActionLabel
import az.gesture.app.ui.components.resolveAppLabel
import az.gesture.app.ui.theme.GestureTheme
import kotlinx.coroutines.launch

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
    var drawnGesture by remember(editingShortcut) { mutableStateOf<Gesture?>(null) }
    var overlayRef by remember { mutableStateOf<GestureOverlayView?>(null) }
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

    GestureEditorContent(
        isEditing = editingShortcut != null,
        name = name,
        onNameChange = { name = it },
        selectedAction = selectedAction,
        selectedActionLabel = selectedAction?.let { formatActionLabel(context, it) },
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
            overlayRef?.clear(false)
            drawnGesture = null
        },
        onClearAction = { selectedAction = null },
        onPickApp = { showQuickAppPicker = true },
        onPickOtherAction = { showActionPicker = true },
        onBindOverlay = { overlay -> overlayRef = overlay },
        onGestureUpdated = { gesture -> drawnGesture = gesture },
        onSave = {
            val trimmedName = name.trim()
            val finalGesture = drawnGesture ?: overlayRef?.gesture?.takeIf { it.strokesCount > 0 }
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
    onBindOverlay: (GestureOverlayView) -> Unit,
    onGestureUpdated: (Gesture) -> Unit,
    onSave: () -> Unit
) {
    val isInPreview = LocalInspectionMode.current

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
                TextButton(onClick = onClearDrawing) {
                    Text(stringResource(R.string.editor_clear_drawing))
                }
            }

            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (!hasDrawnStroke && existingThumb == null) {
                        Text(
                            text = stringResource(R.string.editor_draw_placeholder),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }

                    if (!hasDrawnStroke && existingThumb != null) {
                        Image(
                            bitmap = existingThumb,
                            contentDescription = null,
                            modifier = Modifier
                                .size(160.dp)
                                .align(Alignment.Center)
                                .alpha(0.35f)
                        )
                    }

                    if (!isInPreview) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { ctx ->
                                ScrollSafeGestureOverlayView(ctx).apply {
                                    gestureStrokeType = if (multiStroke) {
                                        GestureOverlayView.GESTURE_STROKE_TYPE_MULTIPLE
                                    } else {
                                        GestureOverlayView.GESTURE_STROKE_TYPE_SINGLE
                                    }
                                    isFadeEnabled = false
                                    gestureStrokeWidth = 12f
                                    orientation = GestureOverlayView.ORIENTATION_VERTICAL
                                    gestureStrokeAngleThreshold = 95f
                                    gestureStrokeSquarenessTreshold = 0f
                                    gestureStrokeLengthThreshold = 20f
                                    gestureColor = strokeColor
                                    uncertainGestureColor = strokeColor
                                    isEventsInterceptionEnabled = true
                                    addOnGestureListener(object : GestureOverlayView.OnGestureListener {
                                        override fun onGestureStarted(overlay: GestureOverlayView?, event: MotionEvent?) = Unit
                                        override fun onGesture(overlay: GestureOverlayView?, event: MotionEvent?) = Unit
                                        override fun onGestureEnded(overlay: GestureOverlayView?, event: MotionEvent?) {
                                            val g = overlay?.gesture
                                            if (g != null && g.strokesCount > 0) {
                                                onGestureUpdated(g)
                                            }
                                        }
                                        override fun onGestureCancelled(overlay: GestureOverlayView?, event: MotionEvent?) = Unit
                                    })
                                    addOnGesturePerformedListener { overlay, gesture ->
                                        val g = gesture ?: overlay.gesture
                                        if (g != null && g.strokesCount > 0) {
                                            onGestureUpdated(g)
                                        }
                                    }
                                    onBindOverlay(this)
                                }
                            }
                        )
                    }
                }
            }

            if (hasDrawnStroke) {
                Text(
                    text = stringResource(R.string.editor_stroke_ready),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
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
                Button(
                    onClick = onPickApp,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_action_apps),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.editor_select_app_quick), maxLines = 1)
                }

                FilledTonalButton(
                    onClick = onPickOtherAction,
                    modifier = Modifier.weight(1f)
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
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ActionIcon(type = selectedAction.type, modifier = Modifier.size(22.dp))
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
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Text(
                        text = stringResource(R.string.editor_no_action_yet),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    )
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = onNameChange,
                label = { Text(stringResource(R.string.editor_name_label)) },
                placeholder = { Text(stringResource(R.string.editor_name_placeholder)) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(4.dp))

            Button(
                onClick = onSave,
                modifier = Modifier.fillMaxWidth()
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

@Preview(name = "Editor AMOLED", showBackground = true, backgroundColor = 0xFF000000, widthDp = 393, heightDp = 851)
@Composable
fun GestureEditorPreview() {
    GestureTheme(themeMode = AppThemeMode.AMOLED, dynamicColor = false) {
        GestureEditorContent(
            isEditing = false,
            name = "Instagram",
            onNameChange = {},
            selectedAction = GestureAction(ActionType.LAUNCH_APP, "com.instagram.android"),
            selectedActionLabel = "Launch app: Instagram",
            hasDrawnStroke = false,
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
            onBindOverlay = {},
            onGestureUpdated = {},
            onSave = {}
        )
    }
}
