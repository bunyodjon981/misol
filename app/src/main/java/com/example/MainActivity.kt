package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.local.ApkRecordEntity
import com.example.ui.GitApkViewModel
import com.example.ui.screens.BuildApkScreen
import com.example.ui.screens.MyApksScreen
import com.example.ui.theme.CyanSecondary
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.GitApkTheme
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.OutlineDark
import com.example.ui.theme.SlateSurface
import com.example.ui.theme.SlateSurfaceVariant
import com.example.ui.theme.TextSecondaryDark
import kotlinx.coroutines.launch

enum class MainTab {
    CREATE_APK,
    MY_APKS
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GitApkTheme {
                GitApkRootScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitApkRootScreen(
    viewModel: GitApkViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val formState by viewModel.formState.collectAsStateWithLifecycle()
    val pipelineState by viewModel.pipelineState.collectAsStateWithLifecycle()
    val apksList by viewModel.apksList.collectAsStateWithLifecycle()
    val notificationsEnabled by viewModel.notificationsEnabled.collectAsStateWithLifecycle()

    var currentTab by remember { mutableStateOf(MainTab.CREATE_APK) }
    var pendingSafRecord by remember { mutableStateOf<ApkRecordEntity?>(null) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            if (!granted) {
                viewModel.toggleNotifications(false)
                scope.launch {
                    snackbarHostState.showSnackbar("Bildirishnoma ruxsati berilmadi")
                }
            }
        }
    )

    val safCreateDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.android.package-archive"),
        onResult = { destUri ->
            val record = pendingSafRecord
            if (destUri != null && record != null) {
                viewModel.completeSafExport(record, destUri)
            }
            pendingSafRecord = null
        }
    )

    LaunchedEffect(Unit) {
        viewModel.snackbarEvents.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.safExportRequest.collect { record ->
            pendingSafRecord = record
            safCreateDocumentLauncher.launch(record.fileName)
        }
    }

    // Request POST_NOTIFICATIONS on first launch if enabled on Android 13+
    LaunchedEffect(notificationsEnabled) {
        if (notificationsEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    BackHandler(enabled = currentTab != MainTab.CREATE_APK) {
        currentTab = MainTab.CREATE_APK
    }

    val handleNotificationToggle: (Boolean) -> Unit = { checked ->
        if (checked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                viewModel.toggleNotifications(true)
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                viewModel.toggleNotifications(true)
            }
        } else {
            viewModel.toggleNotifications(checked)
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 700.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = ObsidianBg,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = ObsidianBg,
                        titleContentColor = MaterialTheme.colorScheme.onBackground
                    ),
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_gitapk_foreground_src),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .border(1.dp, OutlineDark, RoundedCornerShape(10.dp))
                            )
                            Column {
                                Text(
                                    text = stringResource(R.string.app_name),
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }
                    },
                    actions = {
                        NotificationTogglePill(
                            enabled = notificationsEnabled,
                            onCheckedChange = handleNotificationToggle,
                            modifier = Modifier.padding(end = 12.dp)
                        )
                    }
                )
            },
            bottomBar = {
                if (!useNavigationRail) {
                    NavigationBar(
                        containerColor = SlateSurface,
                        tonalElevation = 6.dp
                    ) {
                        NavigationBarItem(
                            selected = currentTab == MainTab.CREATE_APK,
                            onClick = { currentTab = MainTab.CREATE_APK },
                            icon = {
                                Icon(
                                    imageVector = if (currentTab == MainTab.CREATE_APK) {
                                        Icons.Rounded.Build
                                    } else {
                                        Icons.Outlined.Build
                                    },
                                    contentDescription = stringResource(R.string.tab_create_apk)
                                )
                            },
                            label = { Text(stringResource(R.string.tab_create_apk)) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = EmeraldPrimary,
                                selectedTextColor = EmeraldPrimary,
                                indicatorColor = EmeraldContainer,
                                unselectedIconColor = TextSecondaryDark,
                                unselectedTextColor = TextSecondaryDark
                            ),
                            modifier = Modifier.testTag("nav_create_apk")
                        )

                        NavigationBarItem(
                            selected = currentTab == MainTab.MY_APKS,
                            onClick = { currentTab = MainTab.MY_APKS },
                            icon = {
                                BadgedBox(
                                    badge = {
                                        if (apksList.isNotEmpty()) {
                                            Badge(
                                                containerColor = CyanSecondary,
                                                contentColor = ObsidianBg
                                            ) {
                                                Text(apksList.size.toString())
                                            }
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (currentTab == MainTab.MY_APKS) {
                                            Icons.Rounded.Inventory2
                                        } else {
                                            Icons.Outlined.Inventory2
                                        },
                                        contentDescription = stringResource(R.string.tab_my_apks)
                                    )
                                }
                            },
                            label = { Text(stringResource(R.string.tab_my_apks)) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = EmeraldPrimary,
                                selectedTextColor = EmeraldPrimary,
                                indicatorColor = EmeraldContainer,
                                unselectedIconColor = TextSecondaryDark,
                                unselectedTextColor = TextSecondaryDark
                            ),
                            modifier = Modifier.testTag("nav_my_apks")
                        )
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavigationRail) {
                    NavigationRail(
                        containerColor = SlateSurface,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        Spacer(modifier = Modifier.size(12.dp))
                        NavigationRailItem(
                            selected = currentTab == MainTab.CREATE_APK,
                            onClick = { currentTab = MainTab.CREATE_APK },
                            icon = {
                                Icon(
                                    imageVector = Icons.Rounded.Build,
                                    contentDescription = stringResource(R.string.tab_create_apk)
                                )
                            },
                            label = { Text(stringResource(R.string.tab_create_apk)) },
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = EmeraldPrimary,
                                selectedTextColor = EmeraldPrimary,
                                indicatorColor = EmeraldContainer
                            )
                        )
                        NavigationRailItem(
                            selected = currentTab == MainTab.MY_APKS,
                            onClick = { currentTab = MainTab.MY_APKS },
                            icon = {
                                BadgedBox(
                                    badge = {
                                        if (apksList.isNotEmpty()) {
                                            Badge { Text(apksList.size.toString()) }
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Inventory2,
                                        contentDescription = stringResource(R.string.tab_my_apks)
                                    )
                                }
                            },
                            label = { Text(stringResource(R.string.tab_my_apks)) },
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = EmeraldPrimary,
                                selectedTextColor = EmeraldPrimary,
                                indicatorColor = EmeraldContainer
                            )
                        )
                    }
                }

                when (currentTab) {
                    MainTab.CREATE_APK -> {
                        BuildApkScreen(
                            formState = formState,
                            pipelineState = pipelineState,
                            onUsernameChanged = viewModel::onUsernameChanged,
                            onTokenChanged = viewModel::onTokenChanged,
                            onRepoNameChanged = viewModel::onRepoNameChanged,
                            onPrivateRepoChanged = viewModel::onPrivateRepoChanged,
                            onZipSelected = viewModel::onZipUriSelected,
                            onStartBuild = viewModel::startBuild,
                            onCancelBuild = viewModel::cancelBuild,
                            onClearLogs = viewModel::clearLogs,
                            onLogsCopied = {
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        context.getString(R.string.logs_copied)
                                    )
                                }
                            },
                            onNavigateToMyApks = { currentTab = MainTab.MY_APKS },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    MainTab.MY_APKS -> {
                        MyApksScreen(
                            apks = apksList,
                            onDownloadApk = viewModel::downloadApk,
                            onShareApk = { apk ->
                                val shareIntent = viewModel.createShareIntent(apk)
                                if (shareIntent != null) {
                                    context.startActivity(
                                        Intent.createChooser(shareIntent, apk.fileName)
                                    )
                                } else {
                                    scope.launch {
                                        snackbarHostState.showSnackbar("APK fayli topilmadi")
                                    }
                                }
                            },
                            onDeleteApk = viewModel::deleteApk,
                            onNavigateToBuild = { currentTab = MainTab.CREATE_APK },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationTogglePill(
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .border(
                1.dp,
                if (enabled) EmeraldPrimary.copy(alpha = 0.45f) else OutlineDark,
                RoundedCornerShape(99.dp)
            ),
        shape = RoundedCornerShape(99.dp),
        color = if (enabled) EmeraldContainer.copy(alpha = 0.4f) else SlateSurfaceVariant
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 10.dp, end = 6.dp, top = 2.dp, bottom = 2.dp)
        ) {
            Icon(
                imageVector = if (enabled) {
                    Icons.Rounded.NotificationsActive
                } else {
                    Icons.Rounded.NotificationsOff
                },
                contentDescription = stringResource(R.string.cd_toggle_notifications),
                tint = if (enabled) EmeraldPrimary else TextSecondaryDark,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.notifications_label),
                style = MaterialTheme.typography.labelMedium,
                color = if (enabled) EmeraldPrimary else TextSecondaryDark
            )
            Spacer(modifier = Modifier.width(4.dp))
            Switch(
                checked = enabled,
                onCheckedChange = onCheckedChange,
                modifier = Modifier
                    .scale(0.78f)
                    .testTag("notifications_toggle"),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = ObsidianBg,
                    checkedTrackColor = EmeraldPrimary,
                    uncheckedThumbColor = TextSecondaryDark,
                    uncheckedTrackColor = ObsidianBg
                )
            )
        }
    }
}
