package az.gesture.app

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import az.gesture.app.data.AppColorTheme
import az.gesture.app.data.AppLanguage
import az.gesture.app.data.AppThemeMode
import az.gesture.app.data.HandlerType
import az.gesture.app.data.LocaleHelper
import az.gesture.app.data.Repo
import az.gesture.app.data.Shortcut
import az.gesture.app.data.SideTriggerMode
import az.gesture.app.ui.components.InitialPermissionsDialog
import az.gesture.app.ui.components.UpdateDialogHost
import az.gesture.app.ui.components.hasPendingInitialPermissions
import az.gesture.app.ui.screens.GestureEditorScreen
import az.gesture.app.ui.screens.GesturesScreen
import az.gesture.app.ui.screens.HomeContent
import az.gesture.app.ui.screens.HomeScreen
import az.gesture.app.ui.screens.SettingsScreen
import az.gesture.app.ui.theme.GestureTheme
import az.gesture.app.update.AppReleaseInfo
import az.gesture.app.update.AppUpdateChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

private class ScrollSnapshot(
    var index: Int = 0,
    var offset: Int = 0
)

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrapContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val context = LocalContext.current
            val repo = remember(context) { Repo.get(context) }
            val initial = remember { repo.getSettings() }
            var themeMode by remember { mutableStateOf(initial.themeMode) }
            var colorTheme by remember { mutableStateOf(initial.colorTheme) }

            // Hoisted navigation & scroll snapshots so theme crossfade never resets tab or scroll
            var selectedTab by remember { mutableStateOf(AppTab.HOME) }
            var editorOpen by remember { mutableStateOf(false) }
            var editingShortcut by remember { mutableStateOf<Shortcut?>(null) }
            var gesturesRefreshKey by remember { mutableIntStateOf(0) }
            var showAboutDialog by remember { mutableStateOf(false) }
            var showInitialPermissions by remember {
                mutableStateOf(
                    !repo.isInitialPermissionFlowDone() &&
                        hasPendingInitialPermissions(context, repo)
                )
            }
            var availableUpdate by remember { mutableStateOf<AppReleaseInfo?>(null) }
            val currentVersion = remember(context) { AppUpdateChecker.getInstalledVersionName(context) }

            val homeScroll = remember { ScrollSnapshot() }
            val gesturesScroll = remember { ScrollSnapshot() }
            val settingsScroll = remember { ScrollSnapshot() }

            LaunchedEffect(Unit) {
                if (!repo.isInitialPermissionFlowDone() && !hasPendingInitialPermissions(context, repo)) {
                    repo.setInitialPermissionFlowDone(true)
                }
                withContext(Dispatchers.IO) {
                    AppUpdateChecker.cleanupOldUpdateApks(context, currentVersion)
                }
                val latest = AppUpdateChecker.fetchLatestRelease()
                if (latest != null &&
                    AppUpdateChecker.shouldPromptUpdate(
                        remoteVersion = latest.versionName,
                        currentVersion = currentVersion,
                        skippedVersion = repo.getSkippedUpdateVersion()
                    )
                ) {
                    availableUpdate = latest
                }
            }

            LaunchedEffect(themeMode) {
                val isDark = themeMode != AppThemeMode.LIGHT
                val windowBg = when (themeMode) {
                    AppThemeMode.LIGHT -> 0xFFF6F7F9.toInt()
                    AppThemeMode.DARK -> 0xFF0C0E12.toInt()
                    AppThemeMode.AMOLED -> 0xFF000000.toInt()
                }
                window.decorView.setBackgroundColor(windowBg)
                enableEdgeToEdge(
                    statusBarStyle = if (isDark) {
                        SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
                    },
                    navigationBarStyle = if (isDark) {
                        SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
                    }
                )
            }

            // Pure GPU-composited Crossfade: zero per-frame recompositions for silky 60/120fps theme transitions
            Crossfade(
                targetState = themeMode to colorTheme,
                animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing),
                label = "themeCrossfade"
            ) { (activeMode, activeColor) ->
                GestureTheme(
                    themeMode = activeMode,
                    colorTheme = activeColor,
                    dynamicColor = false
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        AppRoot(
                            repo = repo,
                            themeMode = activeMode,
                            colorTheme = activeColor,
                            selectedTab = selectedTab,
                            onSelectTab = { selectedTab = it },
                            editorOpen = editorOpen,
                            editingShortcut = editingShortcut,
                            gesturesRefreshKey = gesturesRefreshKey,
                            showAboutDialog = showAboutDialog,
                            onShowAboutDialog = { showAboutDialog = it },
                            onOpenEditor = { shortcut ->
                                editingShortcut = shortcut
                                editorOpen = true
                            },
                            onCloseEditor = { saved ->
                                if (saved) gesturesRefreshKey++
                                editorOpen = false
                                editingShortcut = null
                            },
                            onGesturesChanged = { gesturesRefreshKey++ },
                            homeScroll = homeScroll,
                            gesturesScroll = gesturesScroll,
                            settingsScroll = settingsScroll,
                            onThemeModeChange = { next ->
                                repo.updateSettings { it.copy(themeMode = next) }
                                themeMode = next
                            },
                            onColorThemeChange = { next ->
                                repo.updateSettings { it.copy(colorTheme = next) }
                                colorTheme = next
                            }
                        )

                        if (showInitialPermissions) {
                            InitialPermissionsDialog(
                                repo = repo,
                                onFinished = { showInitialPermissions = false }
                            )
                        } else {
                            availableUpdate?.let { release ->
                                UpdateDialogHost(
                                    repo = repo,
                                    release = release,
                                    currentVersion = currentVersion,
                                    onFinished = { availableUpdate = null }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

enum class AppTab(
    @param:StringRes val labelRes: Int,
    @param:DrawableRes val iconRes: Int
) {
    HOME(R.string.nav_home, R.drawable.ic_action_home),
    GESTURES(R.string.nav_gestures, R.drawable.ic_tab_dot),
    SETTINGS(R.string.nav_settings, R.drawable.ic_tab_settings)
}

@Composable
private fun rememberSyncedListState(snapshot: ScrollSnapshot): LazyListState {
    val state = rememberLazyListState(
        initialFirstVisibleItemIndex = snapshot.index,
        initialFirstVisibleItemScrollOffset = snapshot.offset
    )
    LaunchedEffect(state) {
        snapshotFlow { state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset }
            .collect { (idx, off) ->
                snapshot.index = idx
                snapshot.offset = off
            }
    }
    return state
}

@Composable
private fun AppRoot(
    repo: Repo,
    themeMode: AppThemeMode,
    colorTheme: AppColorTheme,
    selectedTab: AppTab,
    onSelectTab: (AppTab) -> Unit,
    editorOpen: Boolean,
    editingShortcut: Shortcut?,
    gesturesRefreshKey: Int,
    showAboutDialog: Boolean,
    onShowAboutDialog: (Boolean) -> Unit,
    onOpenEditor: (Shortcut?) -> Unit,
    onCloseEditor: (saved: Boolean) -> Unit,
    onGesturesChanged: () -> Unit,
    homeScroll: ScrollSnapshot,
    gesturesScroll: ScrollSnapshot,
    settingsScroll: ScrollSnapshot,
    onThemeModeChange: (AppThemeMode) -> Unit,
    onColorThemeChange: (AppColorTheme) -> Unit
) {
    val homeListState = rememberSyncedListState(homeScroll)
    val gesturesListState = rememberSyncedListState(gesturesScroll)
    val settingsListState = rememberSyncedListState(settingsScroll)

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { onShowAboutDialog(false) },
            title = { Text(stringResource(R.string.about_dialog_title)) },
            text = { Text(stringResource(R.string.about_dialog_body)) },
            confirmButton = {
                TextButton(onClick = { onShowAboutDialog(false) }) {
                    Text(stringResource(R.string.ok))
                }
            }
        )
    }

    if (editorOpen) {
        BackHandler { onCloseEditor(false) }
        GestureEditorScreen(
            repo = repo,
            editingShortcut = editingShortcut,
            onBack = { onCloseEditor(false) },
            onSaved = { onCloseEditor(true) }
        )
        return
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            VeloaTopBar(
                themeMode = themeMode,
                onCycleTheme = {
                    val next = when (themeMode) {
                        AppThemeMode.LIGHT -> AppThemeMode.DARK
                        AppThemeMode.DARK -> AppThemeMode.AMOLED
                        AppThemeMode.AMOLED -> AppThemeMode.LIGHT
                    }
                    onThemeModeChange(next)
                },
                onOpenAbout = { onShowAboutDialog(true) }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
        ) {
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    val forward = targetState.ordinal >= initialState.ordinal
                    val enter = slideInHorizontally(
                        animationSpec = tween(250, easing = FastOutSlowInEasing)
                    ) { w -> if (forward) w / 10 else -w / 10 } +
                        fadeIn(tween(220, easing = FastOutSlowInEasing)) +
                        scaleIn(initialScale = 0.985f, animationSpec = tween(250))
                    val exit = slideOutHorizontally(
                        animationSpec = tween(190, easing = FastOutSlowInEasing)
                    ) { w -> if (forward) -w / 12 else w / 12 } +
                        fadeOut(tween(170))
                    enter togetherWith exit
                },
                label = "tabTransition",
                modifier = Modifier.fillMaxSize()
            ) { tab ->
                when (tab) {
                    AppTab.HOME -> HomeScreen(repo = repo, listState = homeListState)
                    AppTab.GESTURES -> GesturesScreen(
                        repo = repo,
                        refreshKey = gesturesRefreshKey,
                        onCreateShortcut = { onOpenEditor(null) },
                        onEditShortcut = { shortcut -> onOpenEditor(shortcut) },
                        onChanged = onGesturesChanged,
                        listState = gesturesListState
                    )
                    AppTab.SETTINGS -> SettingsScreen(
                        repo = repo,
                        themeMode = themeMode,
                        colorTheme = colorTheme,
                        onThemeModeChange = onThemeModeChange,
                        onColorThemeChange = onColorThemeChange,
                        listState = settingsListState
                    )
                }
            }

            FloatingBottomNavBar(
                selectedTab = selectedTab,
                themeMode = themeMode,
                onSelectTab = onSelectTab,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 14.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VeloaTopBar(
    themeMode: AppThemeMode,
    onCycleTheme: () -> Unit,
    onOpenAbout: () -> Unit
) {
    TopAppBar(
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val brandPrimary = MaterialTheme.colorScheme.primary
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    border = BorderStroke(0.8.dp, brandPrimary.copy(alpha = 0.35f)),
                    modifier = Modifier.size(30.dp)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize().padding(7.dp)) {
                        val w = size.width
                        val h = size.height
                        val path = Path().apply {
                            moveTo(w * 0.14f, h * 0.20f)
                            lineTo(w * 0.50f, h * 0.84f)
                            lineTo(w * 0.86f, h * 0.20f)
                        }
                        drawPath(
                            path = path,
                            color = brandPrimary,
                            style = Stroke(
                                width = 2.6.dp.toPx(),
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background
        ),
        actions = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(end = 14.dp)
            ) {
                val themeIcon = when (themeMode) {
                    AppThemeMode.LIGHT -> R.drawable.ic_sun
                    AppThemeMode.DARK -> R.drawable.ic_moon
                    AppThemeMode.AMOLED -> R.drawable.ic_theme_auto
                }
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onCycleTheme)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(themeIcon),
                            contentDescription = stringResource(R.string.settings_theme),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onOpenAbout)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(R.drawable.ic_info),
                            contentDescription = stringResource(R.string.about_dialog_title),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }
        }
    )
}

@Composable
fun FloatingBottomNavBar(
    selectedTab: AppTab,
    themeMode: AppThemeMode,
    onSelectTab: (AppTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val dockColor = when (themeMode) {
        AppThemeMode.AMOLED -> Color(0xFF0C0F14)
        AppThemeMode.DARK -> MaterialTheme.colorScheme.surfaceContainerHigh
        AppThemeMode.LIGHT -> MaterialTheme.colorScheme.surface
    }

    Surface(
        shape = RoundedCornerShape(32.dp),
        color = dockColor,
        shadowElevation = 12.dp,
        tonalElevation = 4.dp,
        border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .animateContentSize(
                    animationSpec = spring(
                        dampingRatio = 0.82f,
                        stiffness = Spring.StiffnessMediumLow
                    )
                ),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppTab.entries.forEach { tab ->
                val selected = selectedTab == tab
                val pillColor by animateColorAsState(
                    targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                    animationSpec = tween(220, easing = FastOutSlowInEasing),
                    label = "pillColor"
                )
                val tint by animateColorAsState(
                    targetValue = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    animationSpec = tween(200),
                    label = "tabTint"
                )
                val interactionSource = remember { MutableInteractionSource() }

                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = pillColor,
                    modifier = Modifier
                        .clip(RoundedCornerShape(22.dp))
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = { onSelectTab(tab) }
                        )
                ) {
                    Row(
                        modifier = Modifier.padding(
                            horizontal = if (selected) 16.dp else 14.dp,
                            vertical = 10.dp
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            painter = painterResource(tab.iconRes),
                            contentDescription = stringResource(tab.labelRes),
                            tint = tint,
                            modifier = Modifier.size(19.dp)
                        )
                        AnimatedVisibility(
                            visible = selected,
                            enter = fadeIn(tween(180)) + expandHorizontally(
                                animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMedium)
                            ),
                            exit = fadeOut(tween(130)) + shrinkHorizontally(
                                animationSpec = tween(160)
                            )
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Spacer(Modifier.width(7.dp))
                                Text(
                                    text = stringResource(tab.labelRes),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = tint,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview(name = "Veloa Dark Ocean", showBackground = true, backgroundColor = 0xFF0C0E12, widthDp = 393, heightDp = 851)
@Composable
fun MainShellPreview() {
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
                    notificationRoadmap = "Tətbiq bildirişləri → Bildirişləri göstər → Aktiv et",
                    batteryOptimizationDisabled = true,
                    batteryRoadmap = "Batareya sorğusu → İcazə ver",
                    autoStartGranted = true,
                    autoStartRoadmap = "Avtomatik başlatma → Veloa → Aktiv et",
                    showXiaomiPopupPermission = false,
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
