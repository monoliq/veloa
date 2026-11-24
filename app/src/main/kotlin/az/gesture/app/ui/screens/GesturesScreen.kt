package az.gesture.app.ui.screens

import android.gesture.GestureOverlayView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import az.gesture.app.R
import az.gesture.app.data.Repo
import az.gesture.app.data.Shortcut
import az.gesture.app.service.GestureAccessibilityService
import az.gesture.app.ui.components.ScrollSafeGestureOverlayView
import az.gesture.app.ui.components.formatActionLabel
import kotlin.math.roundToInt

@Composable
fun GesturesScreen(
    repo: Repo,
    refreshKey: Int,
    onCreateShortcut: () -> Unit,
    onEditShortcut: (Shortcut) -> Unit,
    onChanged: () -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState()
) {
    val context = LocalContext.current
    val settings = remember(refreshKey) { repo.getSettings() }
    val shortcuts = remember(refreshKey) { repo.getShortcuts() }
    val strokeColor = settings.gestureColor
    var showTestDialog by remember { mutableStateOf(false) }

    if (showTestDialog) {
        TestRecognitionDialog(
            repo = repo,
            strokeColor = strokeColor,
            multiStroke = settings.multiStroke,
            onDismiss = { showTestDialog = false }
        )
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
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
                    fontWeight = FontWeight.Normal
                )
                if (shortcuts.isNotEmpty()) {
                    FilledTonalButton(onClick = { showTestDialog = true }) {
                        Text(stringResource(R.string.gestures_test_mode))
                    }
                }
            }
        }

        if (shortcuts.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_action_gesture_pad),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = stringResource(R.string.gestures_empty_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.gestures_empty_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(shortcuts, key = { it.id }) { shortcut ->
                val thumb = remember(shortcut.id, refreshKey, strokeColor) {
                    repo.getGestureThumbnail(shortcut.id, 112, 112, strokeColor)?.asImageBitmap()
                }
                val actionText = remember(shortcut, refreshKey) {
                    shortcut.actions.firstOrNull()?.let { formatActionLabel(context, it) } ?: ""
                }

                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .clickable { onEditShortcut(shortcut) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.size(48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (thumb != null) {
                                Image(
                                    bitmap = thumb,
                                    contentDescription = shortcut.name,
                                    modifier = Modifier.size(46.dp)
                                )
                            } else {
                                Icon(
                                    painter = painterResource(R.drawable.ic_action_gesture_pad),
                                    contentDescription = null,
                                    tint = Color(strokeColor)
                                )
                            }
                        }

                        Spacer(Modifier.width(18.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = shortcut.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (actionText.isNotEmpty()) {
                                Text(
                                    text = actionText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                repo.deleteShortcut(shortcut.id)
                                GestureAccessibilityService.instance?.refreshOverlays()
                                onChanged()
                            }
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_close),
                                contentDescription = stringResource(R.string.editor_delete),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }

        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp, bottom = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color.Transparent,
                    border = BorderStroke(1.8.dp, MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .size(58.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onCreateShortcut)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(R.drawable.ic_add),
                            contentDescription = stringResource(R.string.editor_title_new),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TestRecognitionDialog(
    repo: Repo,
    strokeColor: Int,
    multiStroke: Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var statusText by remember { mutableStateOf(context.getString(R.string.gestures_test_hint)) }
    var matchedColor by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.gestures_test_mode)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                ) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            ScrollSafeGestureOverlayView(ctx).apply {
                                gestureStrokeType = if (multiStroke) {
                                    GestureOverlayView.GESTURE_STROKE_TYPE_MULTIPLE
                                } else {
                                    GestureOverlayView.GESTURE_STROKE_TYPE_SINGLE
                                }
                                fadeOffset = 500L
                                gestureStrokeWidth = 12f
                                orientation = GestureOverlayView.ORIENTATION_VERTICAL
                                gestureStrokeAngleThreshold = 95f
                                gestureStrokeSquarenessTreshold = 0f
                                gestureStrokeLengthThreshold = 20f
                                gestureColor = strokeColor
                                uncertainGestureColor = strokeColor
                                isEventsInterceptionEnabled = true
                                addOnGesturePerformedListener { _, gesture ->
                                    val result = repo.recognize(gesture)
                                    val scoreStr = "${(result.bestScore * 100.0).roundToInt()}%"
                                    if (result.shortcut != null) {
                                        matchedColor = true
                                        statusText = ctx.getString(
                                            R.string.gestures_test_matched,
                                            result.shortcut.name,
                                            scoreStr
                                        )
                                    } else {
                                        matchedColor = false
                                        statusText = ctx.getString(R.string.gestures_test_unmatched, scoreStr)
                                    }
                                }
                            }
                        }
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (matchedColor) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.overlay_close))
            }
        }
    )
}
