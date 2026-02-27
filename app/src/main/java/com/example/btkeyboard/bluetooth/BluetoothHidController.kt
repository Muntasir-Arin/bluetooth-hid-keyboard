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
import com.example.btkeyboard.data.AppSettingsStore
import com.example.btkeyboard.data.DeviceStore
import com.example.btkeyboard.model.ConnectionState
import com.example.btkeyboard.model.ErrorCode
import com.example.btkeyboard.model.HostDevice
import com.example.btkeyboard.model.KeyAction
import com.example.btkeyboard.model.ModifierKey
import com.example.btkeyboard.model.MouseButton
import com.example.btkeyboard.util.BluetoothPermissionHelper
import com.example.btkeyboard.util.DiagnosticsLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class BluetoothHidController(
    private val context: Context,
    private val deviceStore: DeviceStore,
    private val settingsStore: AppSettingsStore,
    private val logger: DiagnosticsLogger,
    private val scope: CoroutineScope,
    private val encoder: HidReportEncoder = HidReportEncoder(),
) : HidTransport {

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

    private var hidDevice: BluetoothHidDevice? = null
    private var connectedDevice: BluetoothDevice? = null
    private val appRegistered = AtomicBoolean(false)
    private var lastProfileProxyErrorCode: ErrorCode = ErrorCode.PROFILE_UNAVAILABLE
    private var lastProfileProxyErrorMessage: String = "Unable to acquire HID profile proxy."

    val discoveredDevices: StateFlow<List<HostDevice>> = _discoveredDevices.asStateFlow()
    val bondedDevices: StateFlow<List<HostDevice>> = _bondedDevices.asStateFlow()
    val trustedDevices: StateFlow<List<HostDevice>> = _trustedDevices.asStateFlow()
    val activeModifiers: StateFlow<Set<ModifierKey>> = _activeModifiers.asStateFlow()
    val pressedMouseButtons: StateFlow<Set<MouseButton>> = _pressedMouseButtons.asStateFlow()
    val unsupportedCharCount: StateFlow<Int> = _unsupportedCharCount.asStateFlow()
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
        acknowledged < CURRENT_HID_DESCRIPTOR_VERSION && trusted.isNotEmpty()
    }.stateIn(scope, started = SharingStarted.Eagerly, initialValue = false)

    override val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private val hidCallback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            appRegistered.set(registered)
            logger.log("HID app status changed: registered=$registered")
            if (!registered) {
                connectedDevice = null
                _activeModifiers.value = emptySet()
                _pressedMouseButtons.value = emptySet()
                _state.value = ConnectionState.Idle
            }
        }

        override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
            when (state) {
                BluetoothProfile.STATE_CONNECTED -> {
                    connectedDevice = device
                    val host = device.toHostDevice(connected = true)
                    _state.value = ConnectionState.Connected(host, latencyMs = 0)
                    logger.log("Connected to ${host.name} (${host.address})")
                    scope.launch {
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
                    connectedDevice = null
                    _activeModifiers.value = emptySet()
                    _pressedMouseButtons.value = emptySet()
                    if (_state.value !is ConnectionState.Discovering) {
                        _state.value = ConnectionState.Idle
                    }
                }

                BluetoothProfile.STATE_DISCONNECTING -> {
                    logger.log("Disconnecting from ${device.address}")
                }
            }
            refreshBondedDevices()
        }

        override fun onSetProtocol(device: BluetoothDevice, protocol: Byte) {
            logger.log("Host set protocol=$protocol for ${device.address}")
        }

        override fun onInterruptData(device: BluetoothDevice, reportId: Byte, data: ByteArray) {
            logger.log("Interrupt data from ${device.address} reportId=$reportId size=${data.size}")
        }

        override fun onVirtualCableUnplug(device: BluetoothDevice) {
            logger.log("Virtual cable unplugged by ${device.address}")
            connectedDevice = null
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
                    logger.log("Bluetooth discovery finished")
                    if (_state.value is ConnectionState.Discovering) {
                        _state.value = ConnectionState.Idle
                    }
                }

                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    refreshBondedDevices()
                }

                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    if (state == BluetoothAdapter.STATE_OFF) {
                        appRegistered.set(false)
                        connectedDevice = null
                        _state.value = ConnectionState.Error(
                            code = ErrorCode.BLUETOOTH_DISABLED,
                            message = "Bluetooth is off. Enable Bluetooth and retry.",
                        )
                    }
                }
            }
        }
    }

    init {
        refreshBondedDevices()
        scope.launch { refreshTrustedDevices() }
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

    fun isHidDeviceSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

    @SuppressLint("MissingPermission")
    fun startDiscovery(): Result<Unit> {
        val adapter = bluetoothAdapter ?: return errorResult(
            ErrorCode.BLUETOOTH_UNAVAILABLE,
            "Bluetooth adapter is unavailable.",
        )
        if (!hasScanPermission()) {
            return discoveryFailure("Scan permission is required.")
        }
        if (!adapter.isEnabled) {
            return errorResult(ErrorCode.BLUETOOTH_DISABLED, "Enable Bluetooth first.")
        }
        if (!isLocationReadyForClassicDiscovery()) {
            return discoveryFailure(
                "Location services must be enabled for discovery on this Android version. " +
                    "Or pair in system settings and use Bonded devices.",
            )
        }

        if (adapter.isDiscovering) {
            logger.log("Bluetooth discovery already running")
            _state.value = ConnectionState.Discovering
            return Result.success(Unit)
        }

        val started = runCatching {
            _discoveredDevices.value = emptyList()
            adapter.startDiscovery()
        }.getOrElse {
            return discoveryFailure(
                "Unable to start discovery: ${it.message}. " +
                    "You can still pair via system Bluetooth settings.",
            )
        }

        if (!started) {
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
            return discoveryFailure(hint)
        }

        logger.log("Bluetooth discovery started")
        _state.value = ConnectionState.Discovering
        return Result.success(Unit)
    }

    @SuppressLint("MissingPermission")
    fun stopDiscovery() {
        val adapter = bluetoothAdapter ?: return
        if (!hasScanPermission()) return
        if (adapter.isDiscovering) {
            adapter.cancelDiscovery()
            logger.log("Bluetooth discovery cancelled")
        }
        if (_state.value is ConnectionState.Discovering) {
            _state.value = ConnectionState.Idle
        }
    }

    fun pair(device: HostDevice): Result<Unit> {
        val adapter = bluetoothAdapter ?: return errorResult(
            ErrorCode.BLUETOOTH_UNAVAILABLE,
            "Bluetooth adapter is unavailable.",
        )
        if (!hasConnectPermission()) {
            return errorResult(ErrorCode.PERMISSION_DENIED, "Connect permission is required.")
        }

        return runCatching {
            val remote = adapter.getRemoteDevice(device.address)
            _state.value = ConnectionState.Pairing(device)
            @SuppressLint("MissingPermission")
            val initiated = remote.createBond()
            if (!initiated) {
                throw IllegalStateException("Bonding request failed to start")
            }
            logger.log("Pairing initiated for ${device.address}")
            Unit
        }.onFailure {
            _state.value = ConnectionState.Error(
                code = ErrorCode.CONNECTION_FAILED,
                message = "Pairing failed: ${it.message}",
            )
        }
    }

    override fun registerApp(): Result<Unit> {
        if (!isHidDeviceSupported()) {
            return errorResult(
                ErrorCode.HID_UNSUPPORTED,
                "HID device profile is not supported on this Android version.",
            )
        }

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
            return errorResult(
                ErrorCode.REGISTRATION_FAILED,
                "HID app registration command was rejected.",
            )
        }

        logger.log("HID app registration requested")
        return Result.success(Unit)
    }

    override fun unregisterApp(): Result<Unit> {
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
        connectedDevice = null
        _state.value = ConnectionState.Idle
        logger.log("HID app unregistered")
        return Result.success(Unit)
    }

    @SuppressLint("MissingPermission")
    override fun connect(device: HostDevice): Result<Unit> {
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
            registerApp().getOrElse {
                return Result.failure(it)
            }
        }

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
        }
    }

    override fun disconnect(): Result<Unit> {
        if (!hasConnectPermission()) {
            return errorResult(ErrorCode.PERMISSION_DENIED, "Connect permission is required.")
        }

        val profile = hidDevice ?: return Result.success(Unit)
        val device = connectedDevice ?: return Result.success(Unit)

        @SuppressLint("MissingPermission")
        val submitted = profile.disconnect(device)
        if (!submitted) {
            return errorResult(ErrorCode.CONNECTION_FAILED, "Disconnect request failed.")
        }

        logger.log("Disconnect requested for ${device.address}")
        _activeModifiers.value = emptySet()
        _pressedMouseButtons.value = emptySet()
        return Result.success(Unit)
    }

    @SuppressLint("MissingPermission")
    override fun send(action: KeyAction): Result<Unit> {
        if (!hasConnectPermission()) {
            return nonStateFailure(ErrorCode.PERMISSION_DENIED, "Connect permission is required.")
        }
        if (connectedDevice == null) {
            return nonStateFailure(
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
            return sendModifierOnlyReport()
        }

        val encoded = encoder.encode(action, _activeModifiers.value)
        if (encoded.unmappedCount > 0) {
            _unsupportedCharCount.value += encoded.unmappedCount
            logger.log("Skipped ${encoded.unmappedCount} unmapped characters")
        }

        return sendReportsNonState(encoded.reports)
    }

    fun sendPointerMove(
        dxPx: Float,
        dyPx: Float,
        sensitivity: Float,
    ): Result<Unit> {
        val dx = (dxPx * sensitivity).roundToInt()
        val dy = (dyPx * sensitivity).roundToInt()
        if (dx == 0 && dy == 0) {
            return Result.success(Unit)
        }
        val reports = encoder.encodeMouseMove(
            dx = dx,
            dy = dy,
            buttonsMask = mouseButtonsMask(),
        )
        return sendReportsNonState(reports)
    }

    fun sendVerticalScroll(steps: Int): Result<Unit> {
        if (steps == 0) {
            return Result.success(Unit)
        }
        val reports = encoder.encodeMouseScroll(
            wheel = steps,
            buttonsMask = mouseButtonsMask(),
        )
        return sendReportsNonState(reports)
    }

    fun sendHorizontalScroll(steps: Int): Result<Unit> {
        if (steps == 0) {
            return Result.success(Unit)
        }
        return sendMouseScrollWithTemporaryModifiers(
            steps = steps,
            requiredModifiers = setOf(ModifierKey.SHIFT),
        )
    }

    fun sendZoom(zoomIn: Boolean): Result<Unit> {
        val wheelSteps = if (zoomIn) 1 else -1
        return sendMouseScrollWithTemporaryModifiers(
            steps = wheelSteps,
            requiredModifiers = setOf(ModifierKey.CTRL),
        )
    }

    fun setMouseButton(button: MouseButton, pressed: Boolean): Result<Unit> {
        val current = _pressedMouseButtons.value
        if ((button in current) == pressed) {
            return Result.success(Unit)
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
        return sendResult
    }

    fun clickMouseButton(button: MouseButton): Result<Unit> {
        if (button in _pressedMouseButtons.value) {
            return Result.success(Unit)
        }
        setMouseButton(button, pressed = true).getOrElse { return Result.failure(it) }
        return setMouseButton(button, pressed = false)
    }

    fun doubleClickMouseButton(button: MouseButton): Result<Unit> {
        clickMouseButton(button).getOrElse { return Result.failure(it) }
        return clickMouseButton(button)
    }

    fun shortcutTaskView(): Result<Unit> {
        return sendKeyboardShortcut(
            usage = KEY_USAGE_TAB,
            modifiers = setOf(ModifierKey.META),
        )
    }

    fun shortcutShowDesktop(): Result<Unit> {
        return sendKeyboardShortcut(
            usage = KEY_USAGE_D,
            modifiers = setOf(ModifierKey.META),
        )
    }

    fun shortcutSwitchApp(next: Boolean): Result<Unit> {
        val modifiers = if (next) {
            setOf(ModifierKey.ALT)
        } else {
            setOf(ModifierKey.ALT, ModifierKey.SHIFT)
        }
        return sendKeyboardShortcut(
            usage = KEY_USAGE_TAB,
            modifiers = modifiers,
        )
    }

    fun shortcutLookup(): Result<Unit> {
        return sendKeyboardShortcut(
            usage = KEY_USAGE_F,
            modifiers = setOf(ModifierKey.CTRL),
        )
    }

    fun acknowledgeHidDescriptorMigration() {
        _acknowledgedDescriptorVersionOverride.value = CURRENT_HID_DESCRIPTOR_VERSION
        scope.launch {
            settingsStore.updateAcknowledgedHidDescriptorVersion(CURRENT_HID_DESCRIPTOR_VERSION)
            logger.log("HID descriptor migration acknowledged")
        }
    }

    fun forgetTrustedDevice(address: String) {
        scope.launch {
            deviceStore.removeTrusted(address)
            refreshTrustedDevices()
            logger.log("Trusted device removed: $address")
        }
    }

    fun clearTrustedDevices() {
        scope.launch {
            deviceStore.clearAll()
            refreshTrustedDevices()
            logger.log("Cleared all trusted devices")
        }
    }

    fun updateAutoReconnect(enabled: Boolean) {
        scope.launch {
            settingsStore.updateAutoReconnect(enabled)
            logger.log("Auto reconnect set to $enabled")
        }
    }

    fun updateForegroundPersistence(enabled: Boolean) {
        scope.launch {
            settingsStore.updateForegroundPersistence(enabled)
            logger.log("Foreground persistence set to $enabled")
        }
    }

    fun updatePointerSensitivity(value: Float) {
        val clamped = value.coerceIn(0.5f, 2.0f)
        scope.launch {
            settingsStore.updatePointerSensitivity(clamped)
            logger.log("Pointer sensitivity set to $clamped")
        }
    }

    fun attemptAutoReconnect() {
        scope.launch(Dispatchers.IO) {
            if (requiresHostRepairNow()) {
                logger.log("Auto reconnect skipped: host re-pair required after descriptor update")
                return@launch
            }
            val settings = settings.value
            if (!settings.autoReconnect) {
                logger.log("Auto reconnect disabled")
                return@launch
            }
            val last = deviceStore.getLastConnected() ?: return@launch
            val adapter = bluetoothAdapter ?: return@launch
            if (!hasConnectPermission()) {
                logger.log("Auto reconnect skipped: missing connect permission")
                return@launch
            }
            runCatching {
                val remote = adapter.getRemoteDevice(last)
                @SuppressLint("MissingPermission")
                val bonded = remote.bondState == BluetoothDevice.BOND_BONDED
                if (!bonded) {
                    logger.log("Auto reconnect skipped: last device is not bonded")
                    return@runCatching
                }
                connect(remote.toHostDevice(connected = false))
            }.onFailure {
                logger.log("Auto reconnect failed: ${it.message}")
            }
        }
    }

    fun clearUnsupportedCount() {
        _unsupportedCharCount.value = 0
    }

    fun refreshKnownDevices() {
        refreshBondedDevices()
        scope.launch { refreshTrustedDevices() }
    }

    @SuppressLint("MissingPermission")
    private fun refreshBondedDevices() {
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

    private fun ensureProfileProxy(timeoutSeconds: Long = 8): BluetoothHidDevice? {
        if (hidDevice != null) {
            return hidDevice
        }

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

        var proxy: BluetoothHidDevice? = null
        val latch = CountDownLatch(1)

        @SuppressLint("MissingPermission")
        val requested = adapter.getProfileProxy(
            context,
            object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(profile: Int, service: BluetoothProfile) {
                    if (profile == BluetoothProfile.HID_DEVICE) {
                        proxy = service as? BluetoothHidDevice
                        hidDevice = proxy
                    }
                    latch.countDown()
                }

                override fun onServiceDisconnected(profile: Int) {
                    if (profile == BluetoothProfile.HID_DEVICE) {
                        hidDevice = null
                        appRegistered.set(false)
                        connectedDevice = null
                        _activeModifiers.value = emptySet()
                        _pressedMouseButtons.value = emptySet()
                        _state.value = ConnectionState.Error(
                            code = ErrorCode.PROFILE_UNAVAILABLE,
                            message = "Bluetooth HID profile disconnected unexpectedly.",
                        )
                    }
                }
            },
            BluetoothProfile.HID_DEVICE,
        )

        if (!requested) {
            lastProfileProxyErrorCode = ErrorCode.HID_UNSUPPORTED
            lastProfileProxyErrorMessage =
                "This phone does not expose Bluetooth HID Device profile. Try a different device/ROM."
            return null
        }

        val callbackArrived = latch.await(timeoutSeconds, TimeUnit.SECONDS)
        if (proxy == null) {
            lastProfileProxyErrorCode = ErrorCode.PROFILE_UNAVAILABLE
            lastProfileProxyErrorMessage =
                if (!callbackArrived) {
                    "Timed out waiting for HID profile service. Retry once after toggling Bluetooth."
                } else {
                    "Unable to acquire HID profile proxy. If this persists, your device likely does not support HID Device mode."
                }
        }
        return proxy
    }

    private fun registerReceivers() {
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
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
    private fun sendReportsNonState(reports: List<HidReportEncoder.EncodedReport>): Result<Unit> {
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
        return acknowledged < CURRENT_HID_DESCRIPTOR_VERSION && _trustedDevices.value.isNotEmpty()
    }

    private fun sendModifierOnlyReport(): Result<Unit> {
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

    private fun sendMouseScrollWithTemporaryModifiers(
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

    private fun sendKeyboardShortcut(
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

    private fun discoveryFailure(message: String): Result<Unit> {
        logger.log("Warn(${ErrorCode.DISCOVERY_FAILED}): $message")
        if (_state.value is ConnectionState.Discovering) {
            _state.value = ConnectionState.Idle
        }
        return Result.failure(IllegalStateException(message))
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

    companion object {
        const val CURRENT_HID_DESCRIPTOR_VERSION = 2
        const val REPAIR_REQUIRED_MESSAGE =
            "Bluetooth HID descriptor changed in this version. Forget this phone from your host and pair again before connecting."
        private const val KEY_USAGE_TAB = 0x2B
        private const val KEY_USAGE_D = 0x07
        private const val KEY_USAGE_F = 0x09
    }
}
