package az.gesture.app.ui.components

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.gesture.GestureOverlayView
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import az.gesture.app.R
import az.gesture.app.data.ActionType
import az.gesture.app.data.BUTTON_COLOR_PRESETS
import az.gesture.app.data.GestureAction
import az.gesture.app.data.ParamType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ScrollSafeGestureOverlayView(context: Context) : GestureOverlayView(context) {
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> parent?.requestDisallowInterceptTouchEvent(true)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> parent?.requestDisallowInterceptTouchEvent(false)
        }
        return super.dispatchTouchEvent(ev)
    }
}

@Composable
fun ActionIcon(
    type: ActionType,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary
) {
    Icon(
        painter = painterResource(type.iconRes),
        contentDescription = stringResource(type.labelRes),
        modifier = modifier,
        tint = tint
    )
}

fun resolveAppLabel(context: Context, packageName: String?): String? {
    if (packageName.isNullOrBlank()) return null
    return try {
        val pm = context.packageManager
        val appInfo = pm.getApplicationInfo(packageName, 0)
        pm.getApplicationLabel(appInfo).toString()
    } catch (_: Exception) {
        packageName
    }
}

fun formatActionLabel(context: Context, action: GestureAction): String {
    val base = context.getString(action.type.labelRes)
    return when (action.type.paramType) {
        ParamType.NONE -> base
        ParamType.APP -> {
            val appName = resolveAppLabel(context, action.param)
            if (appName.isNullOrBlank()) base else "$base: $appName"
        }
        ParamType.URL, ParamType.PHONE -> {
            if (action.param.isNullOrBlank()) base else "$base: ${action.param}"
        }
    }
}

@Composable
fun ButtonPreviewGraphic(
    sizeDp: Int,
    opacityPercent: Int,
    colorIndex: Int,
    modifier: Modifier = Modifier
) {
    val preset = BUTTON_COLOR_PRESETS.getOrElse(colorIndex) { BUTTON_COLOR_PRESETS[0] }
    val alpha = (opacityPercent / 100f).coerceIn(0.15f, 1f)
    Canvas(
        modifier = modifier
            .size(sizeDp.dp)
            .alpha(alpha)
    ) {
        val radius = (size.minDimension / 2f) - 2.dp.toPx()
        val center = Offset(size.width / 2f, size.height / 2f)
        drawCircle(color = Color(preset.outerColor), radius = radius, center = center)
        drawCircle(
            color = Color.White.copy(alpha = 0.35f),
            radius = radius,
            center = center,
            style = Stroke(width = 1.5.dp.toPx())
        )
        drawCircle(color = Color(preset.innerColor), radius = radius * 0.58f, center = center)
    }
}

private data class InstalledAppItem(
    val label: String,
    val packageName: String,
    val icon: ImageBitmap?
)

@Composable
fun ActionPickerDialog(
    includeNone: Boolean,
    includeOpenPad: Boolean,
    onDismiss: () -> Unit,
    onActionSelected: (GestureAction) -> Unit
) {
    var pendingParamType by remember { mutableStateOf<ActionType?>(null) }

    if (pendingParamType == ActionType.LAUNCH_APP) {
        AppPickerDialog(
            onDismiss = { pendingParamType = null },
            onAppSelected = { pkg ->
                pendingParamType = null
                onActionSelected(GestureAction(ActionType.LAUNCH_APP, pkg))
            }
        )
        return
    }

    if (pendingParamType == ActionType.OPEN_URL || pendingParamType == ActionType.DIAL) {
        val targetType = pendingParamType!!
        TextParamDialog(
            type = targetType,
            onDismiss = { pendingParamType = null },
            onConfirm = { value ->
                pendingParamType = null
                onActionSelected(GestureAction(targetType, value))
            }
        )
        return
    }

    val actions = remember(includeNone, includeOpenPad) {
        ActionType.entries.filter { type ->
            (includeNone || type != ActionType.NONE) &&
                (includeOpenPad || type != ActionType.OPEN_GESTURE_PAD)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pick_action_title)) },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(actions, key = { it.name }) { type ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (type.paramType == ParamType.NONE) {
                                    onActionSelected(GestureAction(type))
                                } else {
                                    pendingParamType = type
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ActionIcon(type = type, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = stringResource(type.labelRes),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.editor_cancel))
            }
        }
    )
}

@Composable
private fun TextParamDialog(
    type: ActionType,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var value by remember { mutableStateOf("") }
    val titleRes = if (type == ActionType.OPEN_URL) R.string.param_url_title else R.string.param_dial_title
    val hintRes = if (type == ActionType.OPEN_URL) R.string.param_url_hint else R.string.param_dial_hint

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(titleRes)) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                placeholder = { Text(stringResource(hintRes)) },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (value.isNotBlank()) onConfirm(value.trim()) },
                enabled = value.isNotBlank()
            ) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.editor_cancel))
            }
        }
    )
}

@Composable
fun AppPickerDialog(
    onDismiss: () -> Unit,
    onAppSelected: (String) -> Unit
) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<InstalledAppItem>?>(null) }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val resolved = pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            resolved
                .distinctBy { it.activityInfo.packageName }
                .map { info ->
                    val label = info.loadLabel(pm).toString()
                    val pkg = info.activityInfo.packageName
                    val bmp = try {
                        val d = info.loadIcon(pm)
                        val w = 72
                        val h = 72
                        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        val c = Canvas(b)
                        d.setBounds(0, 0, w, h)
                        d.draw(c)
                        b.asImageBitmap()
                    } catch (_: Exception) {
                        null
                    }
                    InstalledAppItem(label = label, packageName = pkg, icon = bmp)
                }
                .sortedBy { it.label.lowercase() }
        }
    }

    val filtered = remember(apps, query) {
        val list = apps ?: emptyList()
        if (query.isBlank()) list
        else list.filter {
            it.label.contains(query, ignoreCase = true) ||
                it.packageName.contains(query, ignoreCase = true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pick_app_title)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
                    placeholder = { Text(stringResource(R.string.pick_app_search)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                if (apps == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(filtered, key = { it.packageName }) { app ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onAppSelected(app.packageName) }
                                    .padding(vertical = 8.dp, horizontal = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (app.icon != null) {
                                    Image(
                                        bitmap = app.icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(34.dp)
                                    )
                                } else {
                                    Spacer(Modifier.size(34.dp))
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = app.label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = app.packageName,
                                        style = MaterialTheme.typography.labelSmall,
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
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.editor_cancel))
            }
        }
    )
}
