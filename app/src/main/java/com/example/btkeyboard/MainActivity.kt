package com.example.btkeyboard

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.btkeyboard.app.BtKeyboardApplication
import com.example.btkeyboard.model.ConnectionState
import com.example.btkeyboard.model.HostDevice
import com.example.btkeyboard.service.BluetoothHidForegroundService
import com.example.btkeyboard.ui.components.ConnectionStatusPill
import com.example.btkeyboard.ui.navigation.AppDestination
import com.example.btkeyboard.ui.screens.DevicesScreen
import com.example.btkeyboard.ui.screens.KeyboardScreen
import com.example.btkeyboard.ui.screens.SettingsScreen
import com.example.btkeyboard.ui.screens.TrackpadScreen
import com.example.btkeyboard.ui.theme.BtKeyboardTheme
import com.example.btkeyboard.ui.theme.UiTokens
import com.example.btkeyboard.ui.viewmodel.AppViewModelFactory
import com.example.btkeyboard.ui.viewmodel.DevicesViewModel
import com.example.btkeyboard.ui.viewmodel.KeyboardViewModel
import com.example.btkeyboard.ui.viewmodel.SettingsViewModel
import com.example.btkeyboard.ui.viewmodel.TrackpadViewModel
import com.example.btkeyboard.util.BluetoothPermissionHelper
import com.example.btkeyboard.util.DiagnosticsExporter

class MainActivity : ComponentActivity() {

    private var pendingPermissionAction: (() -> Unit)? = null
    private var foregroundPersistenceEnabled: Boolean = true

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        val granted = results.values.all { it }
        val action = pendingPermissionAction
        pendingPermissionAction = null
        if (granted) {
            action?.invoke()
        } else {
            toast("Required permission was denied")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestStartupPermissionsAndStartService()

        setContent {
            val app = application as BtKeyboardApplication
            val factory = remember { AppViewModelFactory(app) }

            val devicesViewModel: DevicesViewModel = viewModel(factory = factory)
            val keyboardViewModel: KeyboardViewModel = viewModel(factory = factory)
            val trackpadViewModel: TrackpadViewModel = viewModel(factory = factory)
            val settingsViewModel: SettingsViewModel = viewModel(factory = factory)

            val connectionState by devicesViewModel.connectionState.collectAsStateWithLifecycle()
            val discoveredDevices by devicesViewModel.discoveredDevices.collectAsStateWithLifecycle()
            val bondedDevices by devicesViewModel.bondedDevices.collectAsStateWithLifecycle()
            val trustedDevices by devicesViewModel.trustedDevices.collectAsStateWithLifecycle()
            val requiresHostRepair by devicesViewModel.requiresHostRepair.collectAsStateWithLifecycle()
            val activeModifiers by keyboardViewModel.activeModifiers.collectAsStateWithLifecycle()
            val unsupportedCount by keyboardViewModel.unsupportedCharCount.collectAsStateWithLifecycle()
            val pressedButtons by trackpadViewModel.pressedMouseButtons.collectAsStateWithLifecycle()
            val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
            val diagnostics by settingsViewModel.diagnostics.collectAsStateWithLifecycle()
            val keyboardError by keyboardViewModel.lastError.collectAsStateWithLifecycle()
            val trackpadError by trackpadViewModel.lastError.collectAsStateWithLifecycle()

            foregroundPersistenceEnabled = settings.foregroundPersistence

            val navController = rememberNavController()
            val navEntry by navController.currentBackStackEntryAsState()
            val currentRoute = navEntry?.destination?.route ?: AppDestination.Devices.route
            val currentDestination = AppDestination.entries.firstOrNull { it.route == currentRoute } ?: AppDestination.Devices
            var selectedRoute by rememberSaveable { mutableStateOf(currentRoute) }
            var pendingRepairConnect by remember { mutableStateOf<HostDevice?>(null) }
            var showRepairDialog by rememberSaveable { mutableStateOf(false) }

            LaunchedEffect(currentRoute) {
                selectedRoute = currentRoute
            }

            LaunchedEffect(keyboardError) {
                val message = keyboardError ?: return@LaunchedEffect
                toast(message)
                keyboardViewModel.clearError()
            }

            LaunchedEffect(trackpadError) {
                val message = trackpadError ?: return@LaunchedEffect
                toast(message)
                trackpadViewModel.clearError()
            }

            BtKeyboardTheme {
                if (showRepairDialog) {
                    RepairRequiredDialog(
                        onDismiss = { showRepairDialog = false },
                        onOpenBluetoothSettings = {
                            showRepairDialog = false
                            startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                        },
                        onConfirmRepaired = {
                            showRepairDialog = false
                            devicesViewModel.acknowledgeHostRepair()
                            pendingRepairConnect?.let { device ->
                                withPermissions(BluetoothPermissionHelper.connectPermissions()) {
                                    devicesViewModel.connect(device)
                                        .exceptionOrNull()
                                        ?.message
                                        ?.let(::toast)
                                }
                            }
                            pendingRepairConnect = null
                        },
                    )
                }

                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                    topBar = {
                        AppTopBar(
                            title = currentDestination.title,
                            connectionState = connectionState,
                            showSettingsAction = currentDestination != AppDestination.Settings,
                            onSettingsClick = {
                                selectedRoute = AppDestination.Settings.route
                                navController.navigate(AppDestination.Settings.route) {
                                    launchSingleTop = true
                                }
                            },
                        )
                    },
                    bottomBar = {
                        AppBottomBar(
                            selectedRoute = selectedRoute,
                            destinations = AppDestination.entries,
                            onSelect = { destination ->
                                selectedRoute = destination.route
                                navController.navigate(destination.route) {
                                    launchSingleTop = true
                                    restoreState = true
                                    popUpTo(navController.graph.startDestinationId) {
                                        saveState = true
                                    }
                                }
                            },
                        )
                    },
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = AppDestination.Devices.route,
                        modifier = Modifier.padding(innerPadding),
                    ) {
                        composable(AppDestination.Devices.route) {
                            LaunchedEffect(Unit) {
                                devicesViewModel.refreshKnownDevices()
                            }
                            DevicesScreen(
                                bluetoothEnabled = devicesViewModel.bluetoothEnabled(),
                                hidSupported = devicesViewModel.hidSupported(),
                                connectionState = connectionState,
                                trustedDevices = trustedDevices,
                                bondedDevices = bondedDevices,
                                discoveredDevices = discoveredDevices,
                                requiresHostRepair = requiresHostRepair,
                                onStartDiscovery = {
                                    withPermissions(BluetoothPermissionHelper.scanPermissions()) {
                                        devicesViewModel.startDiscovery()
                                            .exceptionOrNull()
                                            ?.message
                                            ?.let(::toast)
                                    }
                                },
                                onStopDiscovery = devicesViewModel::stopDiscovery,
                                onOpenBluetoothSettings = {
                                    startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                                },
                                onRefresh = devicesViewModel::refreshKnownDevices,
                                onPair = { device ->
                                    withPermissions(BluetoothPermissionHelper.connectPermissions()) {
                                        devicesViewModel.pair(device)
                                            .exceptionOrNull()
                                            ?.message
                                            ?.let(::toast)
                                    }
                                },
                                onConnect = { device ->
                                    withPermissions(BluetoothPermissionHelper.connectPermissions()) {
                                        if (requiresHostRepair) {
                                            pendingRepairConnect = device
                                            showRepairDialog = true
                                        } else {
                                            devicesViewModel.connect(device)
                                                .exceptionOrNull()
                                                ?.message
                                                ?.let(::toast)
                                        }
                                    }
                                },
                                onDisconnect = {
                                    withPermissions(BluetoothPermissionHelper.connectPermissions()) {
                                        devicesViewModel.disconnect()
                                            .exceptionOrNull()
                                            ?.message
                                            ?.let(::toast)
                                    }
                                },
                                onForgetTrusted = devicesViewModel::forgetTrusted,
                                onAcknowledgeRepair = devicesViewModel::acknowledgeHostRepair,
                            )
                        }

                        composable(AppDestination.Keyboard.route) {
                            KeyboardScreen(
                                connectionState = connectionState,
                                inputText = keyboardViewModel.inputText,
                                unsupportedCharCount = unsupportedCount,
                                activeModifiers = activeModifiers,
                                onInputChanged = keyboardViewModel::onInputTextChanged,
                                onSendSpecial = keyboardViewModel::sendSpecial,
                                onModifierToggle = keyboardViewModel::toggleModifier,
                                onClearUnsupportedCount = keyboardViewModel::clearUnsupportedCount,
                                onClearInput = keyboardViewModel::clearInput,
                            )
                        }

                        composable(AppDestination.Trackpad.route) {
                            TrackpadScreen(
                                connectionState = connectionState,
                                pressedButtons = pressedButtons,
                                sensitivity = settings.pointerSensitivity,
                                onMove = { dx, dy ->
                                    trackpadViewModel.movePointer(dx, dy, settings.pointerSensitivity)
                                },
                                onVerticalScrollSteps = trackpadViewModel::scrollBySteps,
                                onHorizontalScrollSteps = trackpadViewModel::scrollHorizontallyBySteps,
                                onTap = trackpadViewModel::tapToClick,
                                onDoubleTap = trackpadViewModel::doubleTapToDoubleClick,
                                onTwoFingerTap = trackpadViewModel::twoFingerTapRightClick,
                                onPinchZoom = trackpadViewModel::pinchZoom,
                                onThreeFingerSwipeUp = trackpadViewModel::threeFingerSwipeUp,
                                onThreeFingerSwipeDown = trackpadViewModel::threeFingerSwipeDown,
                                onThreeFingerSwipeLeft = trackpadViewModel::threeFingerSwipeLeft,
                                onThreeFingerSwipeRight = trackpadViewModel::threeFingerSwipeRight,
                                onThreeFingerTap = trackpadViewModel::threeFingerTapLookup,
                                onButtonPressed = trackpadViewModel::setButtonPressed,
                            )
                        }

                        composable(AppDestination.Settings.route) {
                            SettingsScreen(
                                settings = settings,
                                diagnostics = diagnostics,
                                onAutoReconnectChange = settingsViewModel::setAutoReconnect,
                                onForegroundPersistenceChange = settingsViewModel::setForegroundPersistence,
                                onPointerSensitivityChange = settingsViewModel::setPointerSensitivity,
                                onClearTrustedDevices = settingsViewModel::clearTrustedDevices,
                                onExportDiagnostics = {
                                    val uri = DiagnosticsExporter.export(this@MainActivity, diagnostics)
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    startActivity(Intent.createChooser(intent, "Export diagnostics"))
                                },
                                onClearDiagnostics = settingsViewModel::clearDiagnostics,
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        maybeStartKeyboardService()
    }

    override fun onStop() {
        if (!foregroundPersistenceEnabled) {
            stopKeyboardService()
        }
        super.onStop()
    }

    private fun requestStartupPermissionsAndStartService() {
        withPermissions(startupPermissions()) {
            startKeyboardService()
        }
    }

    private fun startupPermissions(): Array<String> {
        return (BluetoothPermissionHelper.connectPermissions().toList() +
            BluetoothPermissionHelper.notificationPermissions().toList())
            .distinct()
            .toTypedArray()
    }

    private fun maybeStartKeyboardService() {
        val permissions = BluetoothPermissionHelper.connectPermissions()
        if (permissions.isNotEmpty() && !BluetoothPermissionHelper.hasAll(this, permissions)) {
            return
        }
        startKeyboardService()
    }

    private fun withPermissions(permissions: Array<String>, onGranted: () -> Unit) {
        if (permissions.isEmpty() || BluetoothPermissionHelper.hasAll(this, permissions)) {
            onGranted()
            return
        }
        pendingPermissionAction = onGranted
        permissionLauncher.launch(permissions)
    }

    private fun startKeyboardService() {
        val intent = Intent(this, BluetoothHidForegroundService::class.java)
        runCatching {
            ContextCompat.startForegroundService(this, intent)
        }.onFailure {
            toast("Unable to start Bluetooth service: ${it.message}")
        }
    }

    private fun stopKeyboardService() {
        val intent = Intent(this, BluetoothHidForegroundService::class.java).apply {
            action = BluetoothHidForegroundService.ACTION_STOP
        }
        runCatching {
            startService(intent)
        }.onFailure {
            toast("Unable to stop Bluetooth service: ${it.message}")
        }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun AppTopBar(
    title: String,
    connectionState: ConnectionState,
    showSettingsAction: Boolean,
    onSettingsClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(UiTokens.BorderWidth, MaterialTheme.colorScheme.outline),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = UiTokens.ScreenPadding, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.align(Alignment.Center),
                )
                if (showSettingsAction) {
                    IconButton(
                        onClick = onSettingsClick,
                        modifier = Modifier.align(Alignment.CenterEnd),
                    ) {
                        Icon(
                            imageVector = AppDestination.Settings.icon,
                            contentDescription = "Settings",
                        )
                    }
                }
            }
            ConnectionStatusPill(connectionState = connectionState)
        }
    }
}

@Composable
private fun AppBottomBar(
    selectedRoute: String,
    destinations: List<AppDestination>,
    onSelect: (AppDestination) -> Unit,
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
    ) {
        destinations.forEach { destination ->
            NavigationBarItem(
                selected = selectedRoute == destination.route,
                onClick = { onSelect(destination) },
                icon = {
                    Icon(
                        imageVector = destination.icon,
                        contentDescription = destination.title,
                    )
                },
                label = null,
                alwaysShowLabel = false,
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    indicatorColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            )
        }
    }
}

@Composable
private fun RepairRequiredDialog(
    onDismiss: () -> Unit,
    onOpenBluetoothSettings: () -> Unit,
    onConfirmRepaired: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Re-pair required") },
        text = {
            Text("Touchpad support was added. Forget this phone from the host Bluetooth list, then pair again.")
        },
        dismissButton = {
            TextButton(onClick = onOpenBluetoothSettings) {
                Text("Open BT Settings")
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirmRepaired) {
                Text("I Re-paired")
            }
        },
    )
}
