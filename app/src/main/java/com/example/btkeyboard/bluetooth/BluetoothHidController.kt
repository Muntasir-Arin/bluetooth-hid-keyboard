package com.example.btkeyboard.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import android.os.Build
import android.os.SystemClock
import com.example.btkeyboard.data.AppSettingsStore
import com.example.btkeyboard.data.AppThemeMode
import com.example.btkeyboard.data.DeviceStore
import com.example.btkeyboard.model.ConnectionState
import com.example.btkeyboard.model.ErrorCode
import com.example.btkeyboard.model.HidCapability
import com.example.btkeyboard.model.HostDevice
import com.example.btkeyboard.model.KeyAction
import com.example.btkeyboard.model.ModifierKey
import com.example.btkeyboard.model.MouseButton
import com.example.btkeyboard.util.BluetoothPermissionHelper
import com.example.btkeyboard.util.DiagnosticsLogger
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

class BluetoothHidController(
    private val context: Context,
    private val deviceStore: DeviceStore,
    private val settingsStore: AppSettingsStore,
    private val logger: DiagnosticsLogger,
    private val scope: CoroutineScope,
    private val encoder: HidReportEncoder = HidReportEncoder(),
) : HidTransport {

    private val commandDispatcher = Dispatchers.IO.limitedParallelism(1)
    private val profileProxyMutex = Mutex()
    private val profileCircuitBreaker = ProfileProxyCircuitBreaker(
        cooldownMs = PROFILE_FAILURE_COOLDOWN_MS,
        failureThreshold = PROFILE_FAILURE_THRESHOLD,
        nowProvider = { SystemClock.elapsedRealtime() },
    )
    private val registrationReconciler = RegistrationSubmissionReconciler()
    private val discoveryStartReconciler = DiscoveryStartReconciler()
    private val discoveryFailureLimiter = DiscoveryFailureLogLimiter(
        dedupeWindowMs = DISCOVERY_FAILURE_DEDUPE_WINDOW_MS,
        nowProvider = { SystemClock.elapsedRealtime() },
    )
    private val discoveryOwnershipTracker = DiscoveryOwnershipTracker()

    private val bluetoothAdapter: BluetoothAdapter? =
        context.getSystemService(BluetoothManager::class.java)?.adapter

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    private val _discoveredDevices = MutableStateFlow<List<HostDevice>>(emptyList())
    private val _bondedDevices = MutableStateFlow<List<HostDevice>>(emptyList())
    private val _trustedDevices = MutableStateFlow<List<HostDevice>>(emptyList())
    private val _activeModifiers = MutableStateFlow<Set<ModifierKey>>(emptySet())
    private val _pressedMouseButtons = MutableStateFlow<Set<MouseButton>>(emptySet())
    private val _unsupportedCharCount = MutableStateFlow(0)
    private val _acknowledgedDescriptorVersionOverride = MutableStateFlow<Int?>(null)
    private val _hidCapability = MutableStateFlow(HidCapability.UNKNOWN)
    private val _isHostConnected = MutableStateFlow(false)

    @Volatile
    private var hidDevice: BluetoothHidDevice? = null

    @Volatile
    private var connectedDevice: BluetoothDevice? = null

    @Volatile
    private var lastDiscoveryStartedSignalAtMs: Long = 0L

    private var pendingPairingAddress: String? = null
    private var pairingTimeoutJob: Job? = null

    private val appRegistered = AtomicBoolean(false)
    private var lastProfileProxyErrorCode: ErrorCode = ErrorCode.PROFILE_UNAVAILABLE
    private var lastProfileProxyErrorMessage: String = "Unable to acquire HID profile proxy."

    private var pointerSampleCount: Long = 0
    private var pointerBatchCount: Long = 0
    private var pointerReportCount: Long = 0

    val discoveredDevices: StateFlow<List<HostDevice>> = _discoveredDevices.asStateFlow()
    val bondedDevices: StateFlow<List<HostDevice>> = _bondedDevices.asStateFlow()
    val trustedDevices: StateFlow<List<HostDevice>> = _trustedDevices.asStateFlow()
    val activeModifiers: StateFlow<Set<ModifierKey>> = _activeModifiers.asStateFlow()
    val pressedMouseButtons: StateFlow<Set<MouseButton>> = _pressedMouseButtons.asStateFlow()
    val unsupportedCharCount: StateFlow<Int> = _unsupportedCharCount.asStateFlow()
    val hidCapability: StateFlow<HidCapability> = _hidCapability.asStateFlow()
    val isHostConnected: StateFlow<Boolean> = _isHostConnected.asStateFlow()
    val settings = settingsStore.settings.stateIn(
        scope,
        started = SharingStarted.Eagerly,
        initialValue = com.example.btkeyboard.data.AppSettings(),
    )
    val requiresHostRepair = combine(
        settings,
        trustedDevices,
        _acknowledgedDescriptorVersionOverride,
    ) { appSettings, trusted, override ->
        val acknowledged = maxOf(
            appSettings.acknowledgedHidDescriptorVersion,
            override ?: 0,
        )
        acknowledged < HidDescriptorVersion.CURRENT && trusted.isNotEmpty()
    }.stateIn(scope, started = SharingStarted.Eagerly, initialValue = false)

    override val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private val hidCallback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            appRegistered.set(registered)
            logger.log("HID app status changed: registered=$registered")
            if (!registered) {
                clearPendingPairing()
                connectedDevice = null
                _isHostConnected.value = false
                _activeModifiers.value = emptySet()
                _pressedMouseButtons.value = emptySet()
                _state.value = ConnectionState.Idle
            }
        }

        override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
            when (state) {
                BluetoothProfile.STATE_CONNECTED -> {
                    clearPendingPairing()
                    connectedDevice = device
                    _isHostConnected.value = true
                    val host = device.toHostDevice(connected = true)
                    _state.value = ConnectionState.Connected(host, latencyMs = 0)
                    logger.log("Connected to ${host.name} (${host.address})")
                    scope.launch(commandDispatcher) {
                        deviceStore.saveTrusted(host)
                        deviceStore.saveLastConnected(host.address)
                        refreshTrustedDevices()
                    }
                }

                BluetoothProfile.STATE_CONNECTING -> {
                    _state.value = ConnectionState.Connecting(device.toHostDevice(connected = false))
                }

                BluetoothProfile.STATE_DISCONNECTED -> {
                    logger.log("Disconnected from ${device.address}")
                    clearPendingPairing()
                    connectedDevice = null
                    _isHostConnected.value = false
                    _activeModifiers.value = emptySet()
                    _pressedMouseButtons.value = emptySet()
                    _state.value = ConnectionState.Idle
                }

                BluetoothProfile.STATE_DISCONNECTING -> {
                    logger.log("Disconnecting from ${device.address}")
                }
            }
            refreshBondedDevicesAsync()
        }

        override fun onSetProtocol(device: BluetoothDevice, protocol: Byte) {
            logger.log("Host set protocol=$protocol for ${device.address}")
        }

        override fun onInterruptData(device: BluetoothDevice, reportId: Byte, data: ByteArray) {
            logger.log("Interrupt data from ${device.address} reportId=$reportId size=${data.size}")
        }

        override fun onVirtualCableUnplug(device: BluetoothDevice) {
            logger.log("Virtual cable unplugged by ${device.address}")
            clearPendingPairing()
            connectedDevice = null
            _isHostConnected.value = false
            _activeModifiers.value = emptySet()
            _pressedMouseButtons.value = emptySet()
            _state.value = ConnectionState.Idle
        }
    }

    private val discoveryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    if (device != null) {
                        addDiscoveredDevice(device)
                    }
                }

                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    val appOwned = discoveryOwnershipTracker.onDiscoveryFinishedBroadcast()
                    if (appOwned) {
                        logger.log("Bluetooth discovery finished (app-owned)")
                        if (_state.value is ConnectionState.Discovering) {
                            _state.value = ConnectionState.Idle
                        }
                    } else {
                        logger.log("Bluetooth discovery finished externally; app state unchanged")
                    }
                }

                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                    lastDiscoveryStartedSignalAtMs = SystemClock.elapsedRealtime()
                    val appOwned = discoveryOwnershipTracker.onDiscoveryStartedBroadcast()
                    if (appOwned) {
                        logger.log("Bluetooth discovery started (app-owned)")
                        _state.value = ConnectionState.Discovering
                    } else {
                        logger.log("Bluetooth discovery started externally; app state unchanged")
                    }
                }

                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    if (device != null) {
                        val bondState = intent.getIntExtra(
                            BluetoothDevice.EXTRA_BOND_STATE,
                            BluetoothDevice.ERROR,
                        )
                        val previousBondState = intent.getIntExtra(
                            BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE,
                            BluetoothDevice.ERROR,
                        )
                        onBondStateChanged(device, bondState, previousBondState)
                    } else {
                        refreshBondedDevicesAsync()
                    }
                }

                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    when (state) {
                        BluetoothAdapter.STATE_OFF -> {
                            onBluetoothTurnedOff()
                        }

                        BluetoothAdapter.STATE_ON -> {
                            onBluetoothTurnedOn()
                        }
                    }
                }
            }
        }
    }

    init {
        scope.launch(commandDispatcher) { refreshBondedDevicesInternal() }
        scope.launch(commandDispatcher) { refreshTrustedDevices() }
        registerReceivers()
    }

    fun isBluetoothAvailable(): Boolean = bluetoothAdapter != null

    @SuppressLint("MissingPermission")
    fun isBluetoothEnabled(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !hasConnectPermission()) {
            return false
        }
        return runCatching { bluetoothAdapter?.isEnabled == true }.getOrDefault(false)
    }

    @SuppressLint("MissingPermission")
    suspend fun startDiscovery(): Result<Unit> = withContext(commandDispatcher) {
        val adapter = bluetoothAdapter ?: return@withContext errorResult(
            ErrorCode.BLUETOOTH_UNAVAILABLE,
            "Bluetooth adapter is unavailable.",
        )
        if (!hasScanPermission()) {
            return@withContext discoveryFailure("Scan permission is required.", adapter)
        }
        if (!adapter.isEnabled) {
            return@withContext errorResult(ErrorCode.BLUETOOTH_DISABLED, "Enable Bluetooth first.")
        }
        DiscoveryPolicy.blockedReason(_state.value)?.let { blockedReason ->
            return@withContext discoveryFailure(blockedReason, adapter)
        }
        if (!isLocationReadyForClassicDiscovery()) {
            return@withContext discoveryFailure(
                "Location services must be enabled for discovery on this Android version. " +
                    "Or pair in system settings and use Bonded devices.",
                adapter,
            )
        }

        if (adapter.isDiscovering) {
            if (discoveryOwnershipTracker.isAppOwnedActive()) {
                logger.log("Bluetooth discovery already running (app-owned)")
                _state.value = ConnectionState.Discovering
                return@withContext Result.success(Unit)
            }
            return@withContext discoveryFailure(
                "Bluetooth discovery is already running from another app/system flow. Stop it and retry.",
                adapter,
            )
        }

        discoveryOwnershipTracker.markStartRequested()
        val started = runCatching {
            _discoveredDevices.value = emptyList()
            adapter.startDiscovery()
        }.getOrElse {
            discoveryOwnershipTracker.clear()
            return@withContext discoveryFailure(
                "Unable to start discovery: ${it.message}. " +
                    "You can still pair via system Bluetooth settings.",
                adapter,
            )
        }

        if (!started) {
            val requestStartedAt = SystemClock.elapsedRealtime()
            val reconciled = discoveryStartReconciler.awaitStartConfirmation(
                graceMs = DISCOVERY_START_CALLBACK_GRACE_MS,
            ) {
                safeIsDiscovering(adapter) ||
                    lastDiscoveryStartedSignalAtMs >= requestStartedAt
            }
            logger.log(
                "Discovery start reconciliation: submitted=false " +
                    "callback-confirmed=${reconciled.started} waitMs=${reconciled.waitMs}",
            )
            if (reconciled.started) {
                discoveryOwnershipTracker.markStartedBySubmission()
                _state.value = ConnectionState.Discovering
                return@withContext Result.success(Unit)
            }
            discoveryOwnershipTracker.clear()
            val hint = when (adapter.state) {
                BluetoothAdapter.STATE_OFF -> "Bluetooth is off. Enable Bluetooth first."
                BluetoothAdapter.STATE_TURNING_ON -> "Bluetooth is turning on. Try again in a moment."
                BluetoothAdapter.STATE_TURNING_OFF -> "Bluetooth is turning off. Wait and retry."
                else -> {
                    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.R) {
                        "Unable to start discovery. Ensure Location is ON and no other Bluetooth scan is active. " +
                            "Or pair in system Bluetooth settings."
                    } else {
                        "Unable to start discovery. Stop any ongoing scan from other apps and retry. " +
                            "Or pair in system Bluetooth settings."
                    }
                }
            }
            return@withContext discoveryFailure(hint, adapter)
        }

        discoveryOwnershipTracker.markStartedBySubmission()
        logger.log("Bluetooth discovery started (app-owned)")
        _state.value = ConnectionState.Discovering
        Result.success(Unit)
    }

    @SuppressLint("MissingPermission")
    suspend fun stopDiscovery() = withContext(commandDispatcher) {
        val adapter = bluetoothAdapter ?: return@withContext
        if (!hasScanPermission()) return@withContext
        if (!discoveryOwnershipTracker.isAppOwnedActive()) {
            logger.log("Ignoring stopDiscovery: app does not own active discovery")
            return@withContext
        }
        if (adapter.isDiscovering && hasScanPermission()) {
            @SuppressLint("MissingPermission")
            val cancelled = runCatching { adapter.cancelDiscovery() }.getOrDefault(false)
            if (cancelled) {
                logger.log("Bluetooth discovery cancelled (app-owned)")
            }
        }
        discoveryOwnershipTracker.clear()
        if (_state.value is ConnectionState.Discovering) {
            _state.value = ConnectionState.Idle
        }
    }

    suspend fun pair(device: HostDevice): Result<Unit> = withContext(commandDispatcher) {
        val adapter = bluetoothAdapter ?: return@withContext errorResult(
            ErrorCode.BLUETOOTH_UNAVAILABLE,
            "Bluetooth adapter is unavailable.",
        )
        if (!hasConnectPermission()) {
            return@withContext errorResult(ErrorCode.PERMISSION_DENIED, "Connect permission is required.")
        }

        runCatching {
            val remote = adapter.getRemoteDevice(device.address)
            _state.value = ConnectionState.Pairing(device)
            pendingPairingAddress = remote.address
            armPairingTimeout(remote.address)
            @SuppressLint("MissingPermission")
            val initiated = remote.createBond()
            if (!initiated) {
                clearPendingPairing()
                throw IllegalStateException("Bonding request failed to start")
            }
            logger.log("Pairing initiated for ${device.address}")
            Unit
        }.onFailure {
            clearPendingPairing()
            _state.value = ConnectionState.Error(
                code = ErrorCode.CONNECTION_FAILED,
                message = "Pairing failed: ${it.message}",
            )
        }
    }

    override suspend fun registerApp(): Result<Unit> = withContext(commandDispatcher) {
        val startedAt = SystemClock.elapsedRealtime()
        val result = registerAppInternal()
        val elapsedMs = SystemClock.elapsedRealtime() - startedAt
        if (result.isSuccess) {
            logger.log("HID register app completed in ${elapsedMs}ms")
        } else {
            logger.log("HID register app failed in ${elapsedMs}ms")
        }
        result
    }

    override suspend fun unregisterApp(): Result<Unit> = withContext(commandDispatcher) {
        unregisterAppInternal()
    }

    override suspend fun connect(device: HostDevice): Result<Unit> = withContext(commandDispatcher) {
        connectInternal(device)
    }

    override suspend fun disconnect(): Result<Unit> = withContext(commandDispatcher) {
        if (!hasConnectPermission()) {
            return@withContext errorResult(ErrorCode.PERMISSION_DENIED, "Connect permission is required.")
        }

        val profile = hidDevice ?: return@withContext Result.success(Unit)
        val device = connectedDevice ?: return@withContext Result.success(Unit)

        @SuppressLint("MissingPermission")
        val submitted = profile.disconnect(device)
        if (!submitted) {
            return@withContext errorResult(ErrorCode.CONNECTION_FAILED, "Disconnect request failed.")
        }

        logger.log("Disconnect requested for ${device.address}")
        _activeModifiers.value = emptySet()
        _pressedMouseButtons.value = emptySet()
        Result.success(Unit)
    }

    override suspend fun send(action: KeyAction): Result<Unit> = withContext(commandDispatcher) {
        if (!hasConnectPermission()) {
            return@withContext nonStateFailure(ErrorCode.PERMISSION_DENIED, "Connect permission is required.")
        }
        if (connectedDevice == null) {
            return@withContext nonStateFailure(
                ErrorCode.CONNECTION_FAILED,
                "No connected host device. Connect a device first.",
            )
        }

        if (action is KeyAction.ModifierToggle) {
            val updated = _activeModifiers.value.toMutableSet()
            if (action.enabled) {
                updated += action.key
            } else {
                updated -= action.key
            }
            _activeModifiers.value = updated
            return@withContext sendModifierOnlyReport()
        }

        val encoded = encoder.encode(action, _activeModifiers.value)
        if (encoded.unmappedCount > 0) {
            _unsupportedCharCount.value += encoded.unmappedCount
            logger.log("Skipped ${encoded.unmappedCount} unmapped characters")
        }

        sendReportsNonState(encoded.reports)
    }

    suspend fun sendPointerMove(
        dxPx: Float,
        dyPx: Float,
        sensitivity: Float,
        sourceSamples: Int = 1,
    ): Result<Unit> = withContext(commandDispatcher) {
        val dx = (dxPx * sensitivity).roundToInt()
        val dy = (dyPx * sensitivity).roundToInt()
        if (dx == 0 && dy == 0) {
            return@withContext Result.success(Unit)
        }
        val reports = encoder.encodeMouseMove(
            dx = dx,
            dy = dy,
            buttonsMask = mouseButtonsMask(),
        )
        updatePointerQueueStats(sourceSamples = sourceSamples, reportsSent = reports.size)
        sendReportsNonState(reports)
    }

    suspend fun sendVerticalScroll(steps: Int): Result<Unit> = withContext(commandDispatcher) {
        if (steps == 0) {
            return@withContext Result.success(Unit)
        }
        val reports = encoder.encodeMouseScroll(
            wheel = steps,
            buttonsMask = mouseButtonsMask(),
        )
        sendReportsNonState(reports)
    }

    suspend fun sendHorizontalScroll(steps: Int): Result<Unit> = withContext(commandDispatcher) {
        if (steps == 0) {
            return@withContext Result.success(Unit)
        }
        sendMouseScrollWithTemporaryModifiers(
            steps = steps,
            requiredModifiers = setOf(ModifierKey.SHIFT),
        )
    }

    suspend fun sendZoom(zoomIn: Boolean): Result<Unit> = withContext(commandDispatcher) {
        val wheelSteps = if (zoomIn) 1 else -1
        sendMouseScrollWithTemporaryModifiers(
            steps = wheelSteps,
            requiredModifiers = setOf(ModifierKey.CTRL),
        )
    }

    suspend fun setMouseButton(button: MouseButton, pressed: Boolean): Result<Unit> = withContext(commandDispatcher) {
        val current = _pressedMouseButtons.value
        if ((button in current) == pressed) {
            return@withContext Result.success(Unit)
        }
        val updated = current.toMutableSet().apply {
            if (pressed) {
                add(button)
            } else {
                remove(button)
            }
        }.toSet()
        _pressedMouseButtons.value = updated
        val sendResult = sendReportsNonState(listOf(encoder.mouseStateReport(mouseButtonsMask(updated))))
        if (sendResult.isFailure) {
            _pressedMouseButtons.value = current
        }
        sendResult
    }

    suspend fun clickMouseButton(button: MouseButton): Result<Unit> = withContext(commandDispatcher) {
        if (button in _pressedMouseButtons.value) {
            return@withContext Result.success(Unit)
        }
        setMouseButton(button, pressed = true).getOrElse { return@withContext Result.failure(it) }
        setMouseButton(button, pressed = false)
    }

    suspend fun doubleClickMouseButton(button: MouseButton): Result<Unit> = withContext(commandDispatcher) {
        clickMouseButton(button).getOrElse { return@withContext Result.failure(it) }
        clickMouseButton(button)
    }

    suspend fun shortcutTaskView(): Result<Unit> = withContext(commandDispatcher) {
        sendKeyboardShortcut(
            usage = KEY_USAGE_TAB,
            modifiers = setOf(ModifierKey.META),
        )
    }

    suspend fun shortcutShowDesktop(): Result<Unit> = withContext(commandDispatcher) {
        sendKeyboardShortcut(
            usage = KEY_USAGE_D,
            modifiers = setOf(ModifierKey.META),
        )
    }

    suspend fun shortcutSwitchApp(next: Boolean): Result<Unit> = withContext(commandDispatcher) {
        val modifiers = if (next) {
            setOf(ModifierKey.ALT)
        } else {
            setOf(ModifierKey.ALT, ModifierKey.SHIFT)
        }
        sendKeyboardShortcut(
            usage = KEY_USAGE_TAB,
            modifiers = modifiers,
        )
    }

    suspend fun shortcutLookup(): Result<Unit> = withContext(commandDispatcher) {
        sendKeyboardShortcut(
            usage = KEY_USAGE_F,
            modifiers = setOf(ModifierKey.CTRL),
        )
    }

    fun acknowledgeHidDescriptorMigration() {
        _acknowledgedDescriptorVersionOverride.value = HidDescriptorVersion.CURRENT
        scope.launch(commandDispatcher) {
            settingsStore.updateAcknowledgedHidDescriptorVersion(HidDescriptorVersion.CURRENT)
            logger.log("HID descriptor migration acknowledged")
        }
    }

    fun markNotificationPermissionPrompted() {
        if (settings.value.notificationPermissionPrompted) {
            return
        }
        scope.launch(commandDispatcher) {
            settingsStore.updateNotificationPermissionPrompted(prompted = true)
        }
    }

    fun forgetTrustedDevice(address: String) {
        scope.launch(commandDispatcher) {
            deviceStore.removeTrusted(address)
            refreshTrustedDevices()
            logger.log("Trusted device removed: $address")
        }
    }

    fun clearTrustedDevices() {
        scope.launch(commandDispatcher) {
            deviceStore.clearAll()
            refreshTrustedDevices()
            logger.log("Cleared all trusted devices")
        }
    }

    fun updateAutoReconnect(enabled: Boolean) {
        scope.launch(commandDispatcher) {
            settingsStore.updateAutoReconnect(enabled)
            logger.log("Auto reconnect set to $enabled")
        }
    }

    fun updatePointerSensitivity(value: Float) {
        val clamped = value.coerceIn(0.5f, 2.0f)
        scope.launch(commandDispatcher) {
            settingsStore.updatePointerSensitivity(clamped)
            logger.log("Pointer sensitivity set to $clamped")
        }
    }

    fun updateThemeMode(mode: AppThemeMode) {
        scope.launch(commandDispatcher) {
            settingsStore.updateThemeMode(mode)
            logger.log("Theme mode set to ${mode.name}")
        }
    }

    suspend fun attemptAutoReconnect() = withContext(commandDispatcher) {
        if (requiresHostRepairNow()) {
            logger.log("Auto reconnect skipped: host re-pair required after descriptor update")
            return@withContext
        }
        val appSettings = settings.value
        if (!appSettings.autoReconnect) {
            logger.log("Auto reconnect disabled")
            return@withContext
        }
        val last = deviceStore.getLastConnected() ?: return@withContext
        val adapter = bluetoothAdapter ?: return@withContext
        if (!hasConnectPermission()) {
            logger.log("Auto reconnect skipped: missing connect permission")
            return@withContext
        }
        runCatching {
            val remote = adapter.getRemoteDevice(last)
            @SuppressLint("MissingPermission")
            val bonded = remote.bondState == BluetoothDevice.BOND_BONDED
            if (!bonded) {
                logger.log("Auto reconnect skipped: last device is not bonded")
                return@runCatching
            }
            connectInternal(remote.toHostDevice(connected = false))
                .exceptionOrNull()
                ?.let { throw it }
        }.onFailure {
            logger.log("Auto reconnect failed: ${it.message}")
        }
    }

    fun clearUnsupportedCount() {
        _unsupportedCharCount.value = 0
    }

    fun refreshKnownDevices() {
        scope.launch(commandDispatcher) {
            refreshBondedDevicesInternal()
            refreshTrustedDevices()
        }
    }

    private suspend fun registerAppInternal(): Result<Unit> {
        val adapter = bluetoothAdapter ?: return errorResult(
            ErrorCode.BLUETOOTH_UNAVAILABLE,
            "Bluetooth adapter is unavailable.",
        )
        if (!adapter.isEnabled) {
            return errorResult(ErrorCode.BLUETOOTH_DISABLED, "Enable Bluetooth first.")
        }
        if (!hasConnectPermission()) {
            return errorResult(ErrorCode.PERMISSION_DENIED, "Connect permission is required.")
        }

        val profile = ensureProfileProxy() ?: return errorResult(
            lastProfileProxyErrorCode,
            lastProfileProxyErrorMessage,
        )

        if (appRegistered.get()) {
            return Result.success(Unit)
        }

        val sdp = BluetoothHidDeviceAppSdpSettings(
            "BT Keyboard",
            "Bluetooth HID Keyboard + Touchpad",
            "BT Keyboard",
            0xC0.toByte(),
            HidReportEncoder.HID_DESCRIPTOR,
        )

        @SuppressLint("MissingPermission")
        val submitted = profile.registerApp(
            sdp,
            null,
            null,
            context.mainExecutor,
            hidCallback,
        )

        if (!submitted) {
            val reconciled = registrationReconciler.awaitCallbackConfirmation(
                graceMs = REGISTRATION_CALLBACK_GRACE_MS,
                isRegistered = { appRegistered.get() },
            )
            logger.log(
                "HID register reconciliation: submitted=false " +
                    "callback-confirmed=${reconciled.callbackConfirmed} waitMs=${reconciled.waitMs}",
            )
            if (reconciled.callbackConfirmed) {
                logger.log(BluetoothDiagnosticsTags.registrationReconciledWarning())
                return Result.success(Unit)
            }
            return errorResult(
                ErrorCode.REGISTRATION_FAILED,
                "HID app registration command was rejected.",
            )
        }

        logger.log("HID app registration requested")
        return Result.success(Unit)
    }

    private suspend fun unregisterAppInternal(): Result<Unit> {
        val profile = hidDevice ?: return Result.success(Unit)
        if (!hasConnectPermission()) {
            return errorResult(ErrorCode.PERMISSION_DENIED, "Connect permission is required.")
        }

        @SuppressLint("MissingPermission")
        val ok = profile.unregisterApp()
        if (!ok) {
            return errorResult(ErrorCode.REGISTRATION_FAILED, "Failed to unregister HID app.")
        }

        appRegistered.set(false)
        clearPendingPairing()
        connectedDevice = null
        _isHostConnected.value = false
        _state.value = ConnectionState.Idle
        logger.log("HID app unregistered")
        return Result.success(Unit)
    }

    @SuppressLint("MissingPermission")
    private suspend fun connectInternal(device: HostDevice): Result<Unit> {
        if (requiresHostRepairNow()) {
            return errorResult(
                ErrorCode.REPAIR_REQUIRED,
                REPAIR_REQUIRED_MESSAGE,
            )
        }
        if (!hasConnectPermission()) {
            return errorResult(ErrorCode.PERMISSION_DENIED, "Connect permission is required.")
        }

        val adapter = bluetoothAdapter ?: return errorResult(
            ErrorCode.BLUETOOTH_UNAVAILABLE,
            "Bluetooth adapter unavailable.",
        )
        if (!adapter.isEnabled) {
            return errorResult(ErrorCode.BLUETOOTH_DISABLED, "Enable Bluetooth first.")
        }

        val profile = ensureProfileProxy() ?: return errorResult(
            lastProfileProxyErrorCode,
            lastProfileProxyErrorMessage,
        )

        if (!appRegistered.get()) {
            registerAppInternal().getOrElse {
                return Result.failure(it)
            }
        }

        val startedAt = SystemClock.elapsedRealtime()
        return runCatching {
            val remote = adapter.getRemoteDevice(device.address)
            @SuppressLint("MissingPermission")
            val bonded = remote.bondState == BluetoothDevice.BOND_BONDED
            if (!bonded) {
                return errorResult(
                    ErrorCode.DEVICE_NOT_BONDED,
                    "Device must be paired (bonded) before connecting.",
                )
            }

            connectedDevice?.let {
                if (it.address != remote.address) {
                    profile.disconnect(it)
                }
            }

            _state.value = ConnectionState.Connecting(device.copy(connected = false))
            @SuppressLint("MissingPermission")
            val submitted = profile.connect(remote)
            if (!submitted) {
                throw IllegalStateException("Connect command rejected")
            }
            logger.log("Connect requested for ${device.address}")
            Unit
        }.onFailure {
            _state.value = ConnectionState.Error(
                code = ErrorCode.CONNECTION_FAILED,
                message = "Connect failed: ${it.message}",
            )
        }.also {
            val elapsedMs = SystemClock.elapsedRealtime() - startedAt
            if (it.isSuccess) {
                logger.log("Connect command submitted in ${elapsedMs}ms")
            } else {
                logger.log("Connect command failed in ${elapsedMs}ms")
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun refreshBondedDevicesInternal() {
        if (!hasConnectPermission()) {
            _bondedDevices.value = emptyList()
            return
        }

        val adapter = bluetoothAdapter ?: return
        val activeAddress = connectedDevice?.address
        val list = adapter.bondedDevices.orEmpty().map { device ->
            device.toHostDevice(connected = device.address == activeAddress)
        }.sortedBy { it.name.lowercase() }

        _bondedDevices.value = list
    }

    private suspend fun refreshTrustedDevices() {
        val trusted = deviceStore.listTrusted().map { stored ->
            val connectedAddress = connectedDevice?.address
            stored.copy(connected = stored.address == connectedAddress)
        }
        _trustedDevices.value = trusted.sortedBy { it.name.lowercase() }
    }

    @SuppressLint("MissingPermission")
    private fun addDiscoveredDevice(device: BluetoothDevice) {
        val host = device.toHostDevice(connected = false)
        val current = _discoveredDevices.value
        if (current.any { it.address == host.address }) {
            return
        }
        _discoveredDevices.value = (current + host).sortedBy { it.name.lowercase() }
    }

    private suspend fun ensureProfileProxy(timeoutMs: Long = PROFILE_PROXY_TIMEOUT_MS): BluetoothHidDevice? {
        hidDevice?.let { return it }

        val adapter = bluetoothAdapter ?: run {
            lastProfileProxyErrorCode = ErrorCode.BLUETOOTH_UNAVAILABLE
            lastProfileProxyErrorMessage = "Bluetooth adapter is unavailable."
            return null
        }
        if (!hasConnectPermission()) {
            lastProfileProxyErrorCode = ErrorCode.PERMISSION_DENIED
            lastProfileProxyErrorMessage = "Connect permission is required."
            return null
        }
        if (!adapter.isEnabled) {
            lastProfileProxyErrorCode = ErrorCode.BLUETOOTH_DISABLED
            lastProfileProxyErrorMessage = "Enable Bluetooth first."
            return null
        }

        profileCircuitBreaker.failFastMessage()?.let { message ->
            lastProfileProxyErrorCode = ErrorCode.PROFILE_UNAVAILABLE
            lastProfileProxyErrorMessage = message
            _hidCapability.value = HidCapability.UNAVAILABLE
            logger.log("Warn(${ErrorCode.PROFILE_UNAVAILABLE}): $message")
            return null
        }

        return profileProxyMutex.withLock {
            hidDevice?.let { return@withLock it }

            val startedAt = SystemClock.elapsedRealtime()
            val proxy = try {
                withTimeout(timeoutMs) {
                    requestProfileProxy(adapter)
                }
            } catch (_: TimeoutCancellationException) {
                val message = "Timed out waiting for HID profile service. Retry once after toggling Bluetooth."
                handleProfileProxyFailure(message = message, elapsedMs = SystemClock.elapsedRealtime() - startedAt)
                return@withLock null
            } catch (_: ProfileProxyUnsupportedException) {
                val message = "This phone does not expose Bluetooth HID Device profile. Try a different device/ROM."
                lastProfileProxyErrorCode = ErrorCode.HID_UNSUPPORTED
                lastProfileProxyErrorMessage = message
                _hidCapability.value = HidCapability.UNAVAILABLE
                profileCircuitBreaker.recordFailure()
                logger.log("HID profile unsupported: $message")
                return@withLock null
            } catch (throwable: Throwable) {
                val message = "Unable to acquire HID profile proxy: ${throwable.message}"
                handleProfileProxyFailure(message = message, elapsedMs = SystemClock.elapsedRealtime() - startedAt)
                return@withLock null
            }

            if (proxy == null) {
                val message = "Unable to acquire HID profile proxy. If this persists, your device likely does not support HID Device mode."
                handleProfileProxyFailure(message = message, elapsedMs = SystemClock.elapsedRealtime() - startedAt)
                return@withLock null
            }

            hidDevice = proxy
            _hidCapability.value = HidCapability.AVAILABLE
            profileCircuitBreaker.reset()
            logger.log("HID profile proxy acquired in ${SystemClock.elapsedRealtime() - startedAt}ms")
            proxy
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestProfileProxy(adapter: BluetoothAdapter): BluetoothHidDevice? {
        return suspendCancellableCoroutine { continuation ->
            val resumed = AtomicBoolean(false)

            fun resumeOnce(proxy: BluetoothHidDevice?) {
                if (resumed.compareAndSet(false, true) && continuation.isActive) {
                    continuation.resume(proxy)
                }
            }

            fun failOnce(error: Throwable) {
                if (resumed.compareAndSet(false, true) && continuation.isActive) {
                    continuation.resumeWithException(error)
                }
            }

            val listener = object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(profile: Int, service: BluetoothProfile) {
                    if (profile != BluetoothProfile.HID_DEVICE) {
                        return
                    }
                    val proxy = service as? BluetoothHidDevice
                    hidDevice = proxy
                    resumeOnce(proxy)
                }

                override fun onServiceDisconnected(profile: Int) {
                    if (profile != BluetoothProfile.HID_DEVICE) {
                        return
                    }
                    handleHidServiceDisconnected("Bluetooth HID profile disconnected unexpectedly.")
                    resumeOnce(null)
                }
            }

            val requested = runCatching {
                adapter.getProfileProxy(
                    context,
                    listener,
                    BluetoothProfile.HID_DEVICE,
                )
            }.getOrElse {
                failOnce(it)
                return@suspendCancellableCoroutine
            }

            if (!requested) {
                failOnce(ProfileProxyUnsupportedException())
            }
        }
    }

    private fun registerReceivers() {
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(discoveryReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(discoveryReceiver, filter)
            }
        }.onFailure {
            logger.log("Failed to register Bluetooth receivers: ${it.message}")
            _state.value = ConnectionState.Error(
                code = ErrorCode.UNKNOWN,
                message = "Receiver registration failed: ${it.message}",
            )
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun sendReportsNonState(reports: List<HidReportEncoder.EncodedReport>): Result<Unit> {
        if (reports.isEmpty()) {
            return Result.success(Unit)
        }
        if (!hasConnectPermission()) {
            return nonStateFailure(ErrorCode.PERMISSION_DENIED, "Connect permission is required.")
        }
        val profile = hidDevice ?: return nonStateFailure(
            ErrorCode.PROFILE_UNAVAILABLE,
            "HID profile unavailable.",
        )
        val device = connectedDevice ?: return nonStateFailure(
            ErrorCode.CONNECTION_FAILED,
            "No connected host device. Connect a device first.",
        )
        reports.forEach { report ->
            val ok = profile.sendReport(device, report.reportId, report.payload)
            if (!ok) {
                return nonStateFailure(
                    ErrorCode.SEND_FAILED,
                    "Failed to send HID report ${report.reportId}",
                )
            }
        }
        return Result.success(Unit)
    }

    private fun mouseButtonsMask(buttons: Set<MouseButton> = _pressedMouseButtons.value): Int {
        var mask = 0
        if (MouseButton.LEFT in buttons) {
            mask = mask or MouseButton.LEFT.mask
        }
        if (MouseButton.RIGHT in buttons) {
            mask = mask or MouseButton.RIGHT.mask
        }
        return mask
    }

    private fun requiresHostRepairNow(): Boolean {
        val acknowledged = maxOf(
            settings.value.acknowledgedHidDescriptorVersion,
            _acknowledgedDescriptorVersionOverride.value ?: 0,
        )
        return acknowledged < HidDescriptorVersion.CURRENT && _trustedDevices.value.isNotEmpty()
    }

    private suspend fun sendModifierOnlyReport(): Result<Unit> {
        val profile = hidDevice ?: return errorResult(ErrorCode.PROFILE_UNAVAILABLE, "HID profile unavailable.")
        val device = connectedDevice ?: return errorResult(ErrorCode.CONNECTION_FAILED, "No connected host device.")
        val report = byteArrayOf(
            encoder.modifierMask(_activeModifiers.value).toByte(),
            0x00,
            0x00,
            0x00,
            0x00,
            0x00,
            0x00,
            0x00,
        )

        @SuppressLint("MissingPermission")
        val sent = profile.sendReport(device, HidReportEncoder.REPORT_ID_KEYBOARD, report)
        return if (sent) {
            Result.success(Unit)
        } else {
            errorResult(ErrorCode.SEND_FAILED, "Failed to update modifier state")
        }
    }

    private suspend fun sendMouseScrollWithTemporaryModifiers(
        steps: Int,
        requiredModifiers: Set<ModifierKey>,
    ): Result<Unit> {
        if (steps == 0) {
            return Result.success(Unit)
        }
        val baseModifiers = _activeModifiers.value
        val mergedModifiers = (baseModifiers + requiredModifiers).toSet()
        val baseMask = encoder.modifierMask(baseModifiers)
        val mergedMask = encoder.modifierMask(mergedModifiers)

        val reports = mutableListOf<HidReportEncoder.EncodedReport>()
        if (mergedMask != baseMask) {
            reports += encoder.keyboardModifierStateReport(mergedMask)
        }
        reports += encoder.encodeMouseScroll(
            wheel = steps,
            buttonsMask = mouseButtonsMask(),
        )
        if (mergedMask != baseMask) {
            reports += encoder.keyboardModifierStateReport(baseMask)
        }
        return sendReportsNonState(reports)
    }

    private suspend fun sendKeyboardShortcut(
        usage: Int,
        modifiers: Set<ModifierKey>,
    ): Result<Unit> {
        val mask = encoder.modifierMask(modifiers)
        return sendReportsNonState(
            encoder.keyboardShortcutReports(
                usage = usage,
                modifierMask = mask,
            ),
        )
    }

    private fun errorResult(code: ErrorCode, message: String): Result<Unit> {
        logger.log("Error($code): $message")
        _state.value = ConnectionState.Error(code = code, message = message)
        return Result.failure(IllegalStateException(message))
    }

    private fun nonStateFailure(code: ErrorCode, message: String): Result<Unit> {
        logger.log("Warn($code): $message")
        return Result.failure(IllegalStateException(message))
    }

    private fun discoveryFailure(
        message: String,
        adapter: BluetoothAdapter?,
    ): Result<Unit> {
        discoveryOwnershipTracker.clear()
        val snapshot = buildDiscoveryFailureSnapshot(adapter)
        val fingerprint = "$message|$snapshot"
        if (discoveryFailureLimiter.shouldLog(fingerprint)) {
            logger.log("Warn(${ErrorCode.DISCOVERY_FAILED}): $message")
            logger.log("Discovery failure snapshot: $snapshot")
        }
        if (_state.value is ConnectionState.Discovering) {
            _state.value = ConnectionState.Idle
        }
        return Result.failure(IllegalStateException(message))
    }

    private fun buildDiscoveryFailureSnapshot(adapter: BluetoothAdapter?): String {
        val adapterState = adapter?.let { bluetoothAdapterStateName(it.state) } ?: "NULL"
        val adapterDiscovering = if (adapter == null || !hasScanPermission()) {
            false
        } else {
            safeIsDiscovering(adapter)
        }
        return "adapterState=$adapterState " +
            "isDiscovering=$adapterDiscovering " +
            "scanPerm=${hasScanPermission()} " +
            "connectPerm=${hasConnectPermission()} " +
            "locationReady=${isLocationReadyForClassicDiscovery()} " +
            "connectionState=${_state.value.diagnosticLabel()} " +
            "appRegistered=${appRegistered.get()} " +
            "hidCapability=${_hidCapability.value}"
    }

    @SuppressLint("MissingPermission")
    private fun safeIsDiscovering(adapter: BluetoothAdapter): Boolean {
        return runCatching { adapter.isDiscovering }.getOrDefault(false)
    }

    private fun bluetoothAdapterStateName(state: Int): String {
        return when (state) {
            BluetoothAdapter.STATE_ON -> "ON"
            BluetoothAdapter.STATE_OFF -> "OFF"
            BluetoothAdapter.STATE_TURNING_ON -> "TURNING_ON"
            BluetoothAdapter.STATE_TURNING_OFF -> "TURNING_OFF"
            else -> "UNKNOWN($state)"
        }
    }

    private fun ConnectionState.diagnosticLabel(): String {
        return when (this) {
            is ConnectionState.Connected -> "Connected(${device.address})"
            is ConnectionState.Connecting -> "Connecting(${device.address})"
            is ConnectionState.Discovering -> "Discovering"
            is ConnectionState.Pairing -> "Pairing(${device.address})"
            is ConnectionState.Error -> "Error($code)"
            ConnectionState.Idle -> "Idle"
        }
    }

    private fun refreshBondedDevicesAsync() {
        scope.launch(commandDispatcher) {
            refreshBondedDevicesInternal()
        }
    }

    private fun onBondStateChanged(
        device: BluetoothDevice,
        bondState: Int,
        previousBondState: Int,
    ) {
        scope.launch(commandDispatcher) {
            when (
                PairingReconciler.resolve(
                    pendingAddress = pendingPairingAddress,
                    deviceAddress = device.address,
                    bondState = bondState,
                    previousBondState = previousBondState,
                )
            ) {
                PairingResolution.SUCCESS -> {
                    clearPendingPairing()
                    logger.log("Pairing completed for ${device.address}")
                    val currentState = _state.value
                    if (currentState is ConnectionState.Pairing && currentState.device.address == device.address) {
                        if (connectedDevice?.address == device.address) {
                            _state.value = ConnectionState.Connected(
                                device = device.toHostDevice(connected = true),
                                latencyMs = 0L,
                            )
                        } else {
                            _state.value = ConnectionState.Idle
                        }
                    }
                }

                PairingResolution.FAILED_OR_CANCELLED -> {
                    clearPendingPairing()
                    logger.log("Pairing failed or was cancelled for ${device.address}")
                    val currentState = _state.value
                    if (currentState is ConnectionState.Pairing && currentState.device.address == device.address) {
                        _state.value = ConnectionState.Error(
                            code = ErrorCode.CONNECTION_FAILED,
                            message = "Pairing failed or was cancelled. Retry from devices list.",
                        )
                    }
                }

                PairingResolution.NONE -> Unit
            }
            refreshBondedDevicesInternal()
        }
    }

    private fun armPairingTimeout(address: String) {
        pairingTimeoutJob?.cancel()
        pairingTimeoutJob = scope.launch(commandDispatcher) {
            delay(PAIRING_TIMEOUT_MS)
            if (pendingPairingAddress != address) {
                return@launch
            }
            pendingPairingAddress = null
            pairingTimeoutJob = null
            val currentState = _state.value
            if (currentState is ConnectionState.Pairing && currentState.device.address == address) {
                _state.value = ConnectionState.Error(
                    code = ErrorCode.CONNECTION_FAILED,
                    message = "Pairing timed out. Retry and confirm pairing on both devices.",
                )
            }
            logger.log("Pairing timed out for $address")
        }
    }

    private fun clearPendingPairing() {
        pendingPairingAddress = null
        pairingTimeoutJob?.cancel()
        pairingTimeoutJob = null
    }

    private fun onBluetoothTurnedOff() {
        appRegistered.set(false)
        clearPendingPairing()
        discoveryOwnershipTracker.clear()
        connectedDevice = null
        _isHostConnected.value = false
        hidDevice = null
        _hidCapability.value = HidCapability.UNKNOWN
        profileCircuitBreaker.reset()
        _state.value = ConnectionState.Error(
            code = ErrorCode.BLUETOOTH_DISABLED,
            message = "Bluetooth is off. Enable Bluetooth and retry.",
        )
    }

    private fun onBluetoothTurnedOn() {
        clearPendingPairing()
        discoveryOwnershipTracker.clear()
        hidDevice = null
        _hidCapability.value = HidCapability.UNKNOWN
        profileCircuitBreaker.reset()
        logger.log("Bluetooth enabled, reset HID capability cache")
    }

    private fun handleProfileProxyFailure(
        message: String,
        elapsedMs: Long,
    ) {
        lastProfileProxyErrorCode = ErrorCode.PROFILE_UNAVAILABLE
        lastProfileProxyErrorMessage = message
        _hidCapability.value = HidCapability.UNAVAILABLE
        val breakerActivated = profileCircuitBreaker.recordFailure()
        logger.log("HID profile acquisition failed in ${elapsedMs}ms: $message")
        if (breakerActivated) {
            logger.log("HID profile circuit breaker active for ${PROFILE_FAILURE_COOLDOWN_MS}ms")
        }
    }

    private fun handleHidServiceDisconnected(message: String) {
        clearPendingPairing()
        discoveryOwnershipTracker.clear()
        hidDevice = null
        appRegistered.set(false)
        connectedDevice = null
        _isHostConnected.value = false
        _activeModifiers.value = emptySet()
        _pressedMouseButtons.value = emptySet()
        _hidCapability.value = HidCapability.UNAVAILABLE
        _state.value = ConnectionState.Error(
            code = ErrorCode.PROFILE_UNAVAILABLE,
            message = message,
        )
    }

    private fun updatePointerQueueStats(
        sourceSamples: Int,
        reportsSent: Int,
    ) {
        pointerSampleCount += sourceSamples.toLong().coerceAtLeast(1)
        pointerBatchCount += 1
        pointerReportCount += reportsSent.toLong().coerceAtLeast(0)

        if (pointerBatchCount % POINTER_STATS_LOG_EVERY == 0L) {
            val avgSamplesPerBatch = if (pointerBatchCount == 0L) {
                0.0
            } else {
                pointerSampleCount.toDouble() / pointerBatchCount.toDouble()
            }
            logger.log(
                "Pointer queue stats: batches=$pointerBatchCount samples=$pointerSampleCount " +
                    "reports=$pointerReportCount avgSamplesPerBatch=${"%.2f".format(avgSamplesPerBatch)}",
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun BluetoothDevice.toHostDevice(connected: Boolean): HostDevice {
        val safeName = if (hasConnectPermission()) {
            name?.takeIf { it.isNotBlank() } ?: "Unknown Device"
        } else {
            "Unknown Device"
        }
        val bonded = if (hasConnectPermission()) {
            bondState == BluetoothDevice.BOND_BONDED
        } else {
            false
        }
        return HostDevice(
            name = safeName,
            address = address,
            bonded = bonded,
            connected = connected,
        )
    }

    private fun hasScanPermission(): Boolean {
        return BluetoothPermissionHelper.hasAll(context, BluetoothPermissionHelper.scanPermissions())
    }

    private fun hasConnectPermission(): Boolean {
        return BluetoothPermissionHelper.hasAll(context, BluetoothPermissionHelper.connectPermissions())
    }

    private fun isLocationReadyForClassicDiscovery(): Boolean {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.R) {
            return true
        }
        val manager = context.getSystemService(LocationManager::class.java) ?: return true
        return runCatching { manager.isLocationEnabled }.getOrDefault(true)
    }

    private class ProfileProxyUnsupportedException : IllegalStateException()

    companion object {
        const val REPAIR_REQUIRED_MESSAGE =
            "Bluetooth HID descriptor changed in this version. Forget this phone from your host and pair again before connecting."
        private const val KEY_USAGE_TAB = 0x2B
        private const val KEY_USAGE_D = 0x07
        private const val KEY_USAGE_F = 0x09

        private const val PROFILE_PROXY_TIMEOUT_MS = 8_000L
        private const val PROFILE_FAILURE_THRESHOLD = 2
        private const val PROFILE_FAILURE_COOLDOWN_MS = 30_000L
        private const val REGISTRATION_CALLBACK_GRACE_MS = 750L
        private const val DISCOVERY_START_CALLBACK_GRACE_MS = 400L
        private const val DISCOVERY_FAILURE_DEDUPE_WINDOW_MS = 10_000L
        private const val PAIRING_TIMEOUT_MS = 45_000L
        private const val POINTER_STATS_LOG_EVERY = 1200L
    }
}
