package com.tomatishe.anothercoffeescale

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresPermission
import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Start
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowDpSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.ehsannarmani.compose_charts.LineChart
import ir.ehsannarmani.compose_charts.models.AnimationMode
import ir.ehsannarmani.compose_charts.models.DrawStyle
import ir.ehsannarmani.compose_charts.models.Line
import com.tomatishe.anothercoffeescale.ui.theme.AnotherCoffeeScaleTheme
import ir.ehsannarmani.compose_charts.models.HorizontalIndicatorProperties
import ir.ehsannarmani.compose_charts.models.LabelHelperProperties
import ir.ehsannarmani.compose_charts.models.LabelProperties
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.UUID
import kotlin.collections.emptyList
import kotlin.math.min

data class WeightPoint(val timeSeconds: Double, val weight: Double)

data class ScaleModel(
    val name: String,                             // Имя устройства в Bluetooth
    val serviceUuid: UUID,                        // UUID сервиса весов
    val weightCharacteristicUuid: UUID,           // UUID характеристики веса
    val commandCharacteristicUuid: UUID,          // Новое поле — характеристика для отправки команд

    val resetCommand: String = "",
    val unitGramCommand: String = ""
)

object ScaleModels {
    val knownScales = listOf(
        ScaleModel(
            name = "LFSmart Scale",
            serviceUuid = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb"),
            weightCharacteristicUuid = UUID.fromString("0000fff4-0000-1000-8000-00805f9b34fb"),
            commandCharacteristicUuid = UUID.fromString("0000fff1-0000-1000-8000-00805f9b34fb"),
            resetCommand = "fd320000000000000000cf",
            unitGramCommand = "fd000400000000000000f9"
        ),
        // Добавляйте сюда другие модели весов
        // ScaleModel(
        //     name = "Another Scale",
        //     serviceUuid = UUID.fromString("..."),
        //     weightCharacteristicUuid = UUID.fromString("...")
        // )
    )

    fun findByName(name: String): ScaleModel? = knownScales.find { it.name == name }
}

class BleScaleManager(
    private val context: Context,
    private val scaleModel: ScaleModel   // Модель весов, с которой работаем
) {
    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter = bluetoothManager.adapter
    private val scanner = bluetoothAdapter.bluetoothLeScanner

    private val handler = Handler(Looper.getMainLooper())

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    interface ScaleEventListener {
        fun onWeightUpdated(deviceAddress: String?, weightFromScale: Double?)
        fun onConnectionStateChanged(deviceAddress: String, state: Int)
    }

    var listener: ScaleEventListener? = null

    private val connections = mutableMapOf<String, ScaleConnection>()
    private var connectedDeviceAddress: String? = null

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun startScan() {
        val filter = ScanFilter.Builder().setDeviceName(scaleModel.name).build()

        val settings =
            ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()

        handler.postDelayed({ stopScan() }, 10000L)
        scanner.startScan(listOf(filter), settings, scanCallback)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun stopScan() {
        scanner.stopScan(scanCallback)
    }

    private val scanCallback = object : ScanCallback() {
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val address = device.address
            val name = device.name
            if (name == scaleModel.name && !connections.containsKey(address)) {
                val connection = ScaleConnection(device)
                connections[address] = connection
                connection.connect()
                connectedDeviceAddress = address
            }
        }
    }

    fun disconnectAll() {
        val ctx = context
        connections.values.forEach { connection ->
            try {
                if (ctx == null || ContextCompat.checkSelfPermission(
                        ctx, Manifest.permission.BLUETOOTH_CONNECT
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    connection.disconnect()
                } else {
                    // Нет разрешения — пропускаем или логируем
                }
            } catch (e: SecurityException) {
                // Обработка ошибки, например логирование
            }
        }
        connectedDeviceAddress = null
        connections.clear()
    }

    fun returnCommandAddressByCode(commandCode: String): String {
        return when (commandCode) {
            "RESET" -> {
                scaleModel.resetCommand
            }

            "UNIT_GRAM" -> {
                scaleModel.unitGramCommand
            }

            else -> {
                ""
            }
        }
    }

    fun sendCommandToScale(commandCode: String) {
        val ctx = context
        val connection = connections[connectedDeviceAddress]

        val commandHex = returnCommandAddressByCode(commandCode)
        if (connection == null) {
            return
        }
        if (commandHex == "") {
            return
        }

        scope.launch {
            try {
                if (ctx == null || ContextCompat.checkSelfPermission(
                        ctx, Manifest.permission.BLUETOOTH_CONNECT
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    connection.sendCommand(commandHex)
                } else {
                    // Нет разрешения — пропускаем или логируем
                }
            } catch (e: SecurityException) {
                // Обработка ошибки, например логирование
            }
        }
    }

    private inner class ScaleConnection(private val device: BluetoothDevice) {

        private var gatt: BluetoothGatt? = null

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        suspend fun sendCommand(commandHex: String) {
            val gattLocal = gatt ?: run {
                return
            }

            val commandChar = gattLocal.getService(scaleModel.serviceUuid)
                ?.getCharacteristic(scaleModel.commandCharacteristicUuid)

            if (commandChar == null) {
                return
            }

            val commandBytes = hexStringToByteArray(commandHex)

            commandChar.value = commandBytes

            val writeResult = CompletableDeferred<Boolean>()

            // Callbacks для writeCharacteristic результата
            val writeCallback = object : BluetoothGattCallback() {
                override fun onCharacteristicWrite(
                    gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int
                ) {
                    if (characteristic.uuid == scaleModel.commandCharacteristicUuid) {
                        writeResult.complete(status == BluetoothGatt.GATT_SUCCESS)
                    }
                }
            }

            // Внимание: BluetoothGattCallback нельзя менять динамически, поэтому
            // для простоты можно обработать write без ожидания ответа.
            // Обычно onCharacteristicWrite вызывается в том же callback, что и соединение.

            val success = gattLocal.writeCharacteristic(commandChar)

            // Если нужно ждать результата write — требуется дополнительная синхронизация.
            // В Android API прямого await нет — можно сделать, но усложнится.

            if (!success) {
                return
            }
        }

        // Утилитный метод для преобразования hex строки в byte array
        private fun hexStringToByteArray(s: String): ByteArray {
            val len = s.length
            val data = ByteArray(len / 2)
            var i = 0
            while (i < len) {
                data[i / 2] =
                    ((Character.digit(s[i], 16) shl 4) + Character.digit(s[i + 1], 16)).toByte()
                i += 2
            }
            return data
        }

        private val gattCallback = object : BluetoothGattCallback() {
            @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                super.onConnectionStateChange(gatt, status, newState)
                val address = device.address
                listener?.onConnectionStateChanged(address, newState)

                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    gatt.discoverServices()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    // Заготовка на отключение — сюда добавляйте свою логику
                    disconnect()
                }
            }

            @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    val weightChar = gatt.getService(scaleModel.serviceUuid)
                        ?.getCharacteristic(scaleModel.weightCharacteristicUuid)

                    if (weightChar != null) {
                        gatt.setCharacteristicNotification(weightChar, true)
                        val descriptor =
                            weightChar.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
                        descriptor?.let {
                            it.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            gatt.writeDescriptor(it)
                        }
                    }
                }
            }

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic
            ) {
                if (characteristic.uuid == scaleModel.weightCharacteristicUuid) {
                    val weightFromScale =
                        parseWeightCharacteristic(characteristic.value, scaleModel.name)
                    listener?.onWeightUpdated(device.address, weightFromScale)
                }
            }
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        fun connect() {
            gatt = device.connectGatt(context, false, gattCallback)
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        fun disconnect() {
            gatt?.close()
            gatt = null
            connectedDeviceAddress = null
        }

        private fun parseWeightCharacteristic(data: ByteArray, scaleModelName: String): Double {
            return try {
                when (scaleModelName) {
                    "LFSmart Scale" -> {
                        if (data.size < 9) return 0.0

                        val weightSign = if (data[5].toInt() > 0) -1.0 else 1.0
                        val weightUnitCode = data[8].toInt()
                        val weightUnitMultiplier = when (weightUnitCode) {
                            4 -> 1.0
                            6 -> 0.035274
                            7 -> 1.0
                            8 -> 0.972
                            else -> 1.0
                        }
                        val rawWeight =
                            (data[3].toInt() and 0xFF) or ((data[4].toInt() and 0xFF) shl 8)
                        (rawWeight / 10.0) * weightSign * weightUnitMultiplier
                    }

                    "OtherScaleModel1" -> {
                        0.0
                    }

                    else -> {
                        0.0
                    }
                }
            } catch (e: Exception) {
                // Log.d("ERROR", "Ошибка парсинга веса для $scaleModelName: ${e.message}")
                0.0
            }
        }
    }
}

class MainViewModelFactory(
    private val context: Context
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            // SavedStateHandle можно получить, если нужно — через SavedStateVMFactory
            return MainViewModel(SavedStateHandle(), context.applicationContext) as T
            // лучше использовать applicationContext, чтобы избежать утечки!
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

open class MainViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val context: Context? = null // Передаём контекст для Toast и BLE
) : ViewModel() {

    // Отложенная инициализация менеджера
    private val bleScaleManager: BleScaleManager? by lazy {
        context?.let { ctx ->
            BleScaleManager(
                ctx, ScaleModels.findByName("LFSmart Scale") ?: error("Scale model not found")
            ).apply {
                listener = object : BleScaleManager.ScaleEventListener {
                    override fun onConnectionStateChanged(deviceAddress: String, state: Int) {
                        when (state) {
                            BluetoothProfile.STATE_CONNECTED -> {
                                _globalIsConnecting.value = false
                                _globalIsConnected.value = true
                            }

                            BluetoothProfile.STATE_CONNECTING -> {
                                _globalIsConnecting.value = true
                            }

                            BluetoothProfile.STATE_DISCONNECTED -> {
                                _globalIsConnecting.value = false
                                _globalIsConnected.value = false
                            }
                        }
                    }

                    override fun onWeightUpdated(deviceAddress: String?, weightFromScale: Double?) {
                        if (weightFromScale != null) {
                            _globalWeight.doubleValue = weightFromScale
                            _globalWeightFlow.value = weightFromScale
                            manualUpdateGlobalRatio()
                        }
                    }
                }
            }
        }
    }

    // Для preview
    constructor() : this(SavedStateHandle(), null)

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    fun startScanningAndConnect(timeoutMs: Long = 15000L) {
        _globalIsConnecting.value = true
        viewModelScope.launch {
            try {
                if (context != null && ContextCompat.checkSelfPermission(
                        context, Manifest.permission.BLUETOOTH_SCAN
                    ) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(
                        context, Manifest.permission.BLUETOOTH_CONNECT
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    _globalIsConnecting.value = true
                    bleScaleManager?.startScan()
                } else {
                    _errorMessage.value = "Нет разрешения на сканирование Bluetooth"
                    _globalIsConnecting.value = false
                    return@launch
                }

                // Будет ждать, пока _globalIsConnected.value станет true или timeout
                withTimeout(timeoutMs) {
                    snapshotFlow { _globalIsConnected.value }.filter { it }.first()
                }
            } catch (e: TimeoutCancellationException) {
                withContext(Dispatchers.Main) {
                    context?.let {
                        Toast.makeText(it, "Поиск весов не удался", Toast.LENGTH_SHORT).show()
                    }
                }
            } finally {
                bleScaleManager?.stopScan()
                _globalIsConnecting.value = false
            }
        }
    }

    private fun handleWeightChange(newWeight: Double) {
        // Игнорируем многократные нулевые или очень маленькие веса
        if (newWeight <= WEIGHT_THRESHOLD && lastWeight <= WEIGHT_THRESHOLD) {
            return
        }

        viewModelScope.launch {
            if (isAutoTare.value) {
                // Ограничение - сработает только если вес 'значимо' больше 0
                if (newWeight > WEIGHT_THRESHOLD) {
                    autoDoseJob?.cancel()
                    autoTareJob?.cancel()
                    autoTareJob = launch {
                        delay(1000)
                        if (!_isRunning.value) {
                            updateAutoTare(false)
                            resetScale()
                            // Обновляем lastWeight после resetScale, чтобы игнорировать сброс веса на 0
                            lastWeight = 0.0
                        }
                    }
                }
            } else if (isAutoDose.value) {
                if (newWeight > WEIGHT_THRESHOLD) {
                    autoDoseJob?.cancel()
                    autoDoseJob = launch {
                        delay(1000)
                        if (!_isRunning.value) {
                            updateAutoDose(false)
                            updateGlobalDoze(newWeight)
                            resetScale()
                            // Обновляем lastWeight после сброса весов
                            lastWeight = 0.0
                        }
                    }
                }
            } else if (isAutoStart.value) {
                // Автостарт - условие увеличения веса >0.2 и прошлый вес незначителен
                if (newWeight - lastWeight > 0.2 && lastWeight <= WEIGHT_THRESHOLD) {
                    if (!_isRunning.value) {
                        startTimer()
                    }
                }
            }
        }
        // Обновляем lastWeight только если вес "значимый"
        if (newWeight > WEIGHT_THRESHOLD) {
            lastWeight = newWeight
        }
    }


    companion object {
        private const val KEY_GLOBAL_WEIGHT = "globalWeight"
        private const val KEY_GLOBAL_DOSE = "globalDose"
        private const val KEY_GLOBAL_FLOW_RATE = "globalFlowRate"
        private const val KEY_GLOBAL_TIME = "globalTime"
        private const val KEY_ELAPSED_BEFORE_PAUSE = "elapsedBeforePause"
        private const val KEY_IS_RUNNING = "timerIsRunning"
        private const val KEY_START_TIME = "timerStartTime"
        private const val KEY_GLOBAL_RATIO = "globalRatio"
        private const val KEY_IS_CONNECTING = "isConnecting"
        private const val KEY_IS_CONNECTED = "isConnected"
        private const val KEY_WEIGHT_POINTS = "weightPoints"
        private const val KEY_FLOW_RATE_POINTS = "flowRatePoints"
        private val KEY_AUTO_START = booleanPreferencesKey("autoStartEnabled")
        private val KEY_AUTO_DOSE = booleanPreferencesKey("autoDoseEnabled")
        private val KEY_AUTO_TARE = booleanPreferencesKey("autoTareEnabled")
    }

    val isAutoStart: MutableState<Boolean> = mutableStateOf(false)
    val isAutoDose: MutableState<Boolean> = mutableStateOf(false)
    val isAutoTare: MutableState<Boolean> = mutableStateOf(false)

    private val dataStore = context?.dataStore

    private val _globalWeightFlow = MutableStateFlow(0.0)
    val globalWeightFlow: StateFlow<Double> = _globalWeightFlow.asStateFlow()

    private var lastWeight = 0.0
    private val WEIGHT_THRESHOLD = 0.1

    // Храним состояния приватно с mutableStateOf
    private var _globalWeight = mutableDoubleStateOf(
        savedStateHandle.get<Double>(KEY_GLOBAL_WEIGHT) ?: 0.0
    )
    open val globalWeight: State<Double> = _globalWeight

    private val _globalWeightPoints = mutableStateListOf<WeightPoint>().apply {
        val savedPoints = savedStateHandle.get<List<WeightPoint>>(KEY_WEIGHT_POINTS) ?: emptyList()
        addAll(savedPoints)
    }
    open val globalWeightPoints: State<List<WeightPoint>> =
        derivedStateOf { _globalWeightPoints.toList() }

    private var _globalDoze = mutableDoubleStateOf(
        savedStateHandle.get<Double>(KEY_GLOBAL_DOSE) ?: 0.0
    )
    open val globalDoze: State<Double> = _globalDoze

    private var _globalFlowRate = mutableDoubleStateOf(
        savedStateHandle.get<Double>(KEY_GLOBAL_FLOW_RATE) ?: 0.0
    )
    open val globalFlowRate: State<Double> = _globalFlowRate

    private val _globalFlowRatePoints = mutableStateListOf<WeightPoint>().apply {
        val savedPoints =
            savedStateHandle.get<List<WeightPoint>>(KEY_FLOW_RATE_POINTS) ?: emptyList()
        addAll(savedPoints)
    }
    open val globalFlowRatePoints: State<List<WeightPoint>> =
        derivedStateOf { _globalFlowRatePoints.toList() }

    private var _globalRatio = mutableDoubleStateOf(
        savedStateHandle.get<Double>(KEY_GLOBAL_RATIO) ?: 0.0
    )
    open val globalRatio: State<Double> = _globalRatio

    private var _globalIsConnecting = mutableStateOf(
        savedStateHandle.get<Boolean>(KEY_IS_CONNECTING) ?: false
    )
    open val globalIsConnecting: State<Boolean> = _globalIsConnecting

    private var _globalIsConnected = mutableStateOf(
        savedStateHandle.get<Boolean>(KEY_IS_CONNECTED) ?: false
    )
    open val globalIsConnected: State<Boolean> = _globalIsConnected

    private var _globalTime = mutableDoubleStateOf(
        savedStateHandle.get<Double>(KEY_GLOBAL_TIME) ?: 0.0
    )
    open val globalTime: State<Double> = _globalTime

    private var elapsedBeforePauseMillis: Long
        get() = savedStateHandle.get<Long>(KEY_ELAPSED_BEFORE_PAUSE) ?: 0L
        set(value) = savedStateHandle.set(KEY_ELAPSED_BEFORE_PAUSE, value)

    private val _isRunning = mutableStateOf(
        savedStateHandle.get<Boolean>(KEY_IS_RUNNING) ?: false
    )
    open val isRunning: State<Boolean> = _isRunning

    private var internalIsRunning: Boolean
        get() = _isRunning.value
        set(value) {
            _isRunning.value = value
            savedStateHandle[KEY_IS_RUNNING] = value
        }

    private var startTimeMillis: Long
        get() = savedStateHandle.get<Long>(KEY_START_TIME) ?: 0L
        set(value) = savedStateHandle.set(KEY_START_TIME, value)

    private var timerJob: Job? = null
    private var autoTareJob: Job? = null
    private var autoDoseJob: Job? = null

    init {
        // Загружаем значение из DataStore при старте
        viewModelScope.launch {
            val prefs = dataStore?.data?.first()
            isAutoStart.value = prefs?.get(KEY_AUTO_START) ?: false
            isAutoDose.value = prefs?.get(KEY_AUTO_DOSE) ?: false
            isAutoTare.value = prefs?.get(KEY_AUTO_TARE) ?: false

            // Здесь собираем поток веса
            globalWeightFlow.collectLatest { newWeight ->
                handleWeightChange(newWeight)
            }
        }

        // Если мы после пересоздания ViewModel были в состоянии “запущен”
        // и в SavedStateHandle уже есть какое-то время —
        // продолжим без сброса elapsedBeforePauseMillis
        internalIsRunning = savedStateHandle[KEY_IS_RUNNING] ?: false
        elapsedBeforePauseMillis = savedStateHandle[KEY_ELAPSED_BEFORE_PAUSE] ?: 0L
        _globalTime.doubleValue = savedStateHandle[KEY_GLOBAL_TIME] ?: 0.0

        if (internalIsRunning) {
            startTimer()  // resuming = true по умолчанию
        }
    }

    open fun toggleTimer() {
        if (internalIsRunning) {
            pauseTimer()
        } else {
            startTimer()  // само определит, новый старт или продолжение
        }
    }

    open fun setDoze() {
        if (_globalIsConnected.value) {
            if (_globalWeight.doubleValue > 0) {
                _globalDoze.doubleValue = _globalWeight.doubleValue
                resetScale()
            } else {
                _globalDoze.doubleValue = 0.0
            }
        }
    }

    open fun updateAutoStart(value: Boolean) {
        isAutoStart.value = value
        viewModelScope.launch {
            dataStore?.edit { prefs ->
                prefs[KEY_AUTO_START] = value
            }
        }
    }

    open fun updateAutoDose(value: Boolean) {
        isAutoDose.value = value
        viewModelScope.launch {
            dataStore?.edit { prefs ->
                prefs[KEY_AUTO_DOSE] = value
            }
        }
    }

    open fun updateAutoTare(value: Boolean) {
        isAutoTare.value = value
        viewModelScope.launch {
            dataStore?.edit { prefs ->
                prefs[KEY_AUTO_TARE] = value
            }
        }
    }

    private fun getPositiveWeightChangeLastSecond(): Double {
        val points = _globalWeightPoints
        val size = points.size
        if (size == 0) return 0.0

        val pointsPerSecond = 10
        val count = min(pointsPerSecond, size)

        val recentPoints = points.takeLast(count).map { it.weight }

        val currentWeight = points.last().weight
        val pastWeight = points[size - count].weight

        val maxWeight = recentPoints.maxOrNull() ?: 0.0
        val minWeight = recentPoints.minOrNull() ?: 0.0

        val delta = currentWeight - pastWeight
        val minMaxDelta = maxWeight - minWeight

        return if (delta > 0) minMaxDelta else 0.0
    }

    private fun startTimer() {
        if (internalIsRunning) return

        internalIsRunning = true
        savedStateHandle[KEY_IS_RUNNING] = true

        // При новом старте (если ранее время было 0) — обнулим накопленное
        if (_globalTime.doubleValue == 0.0) {
            elapsedBeforePauseMillis = 0L
            savedStateHandle[KEY_ELAPSED_BEFORE_PAUSE] = 0L
        }
        // Никаких вычитаний из now!
        startTimeMillis = System.currentTimeMillis()
        savedStateHandle[KEY_START_TIME] = startTimeMillis

        timerJob = viewModelScope.launch {
            while (internalIsRunning) {
                val nowLoop = System.currentTimeMillis()
                val elapsedThisSession = nowLoop - startTimeMillis
                val totalElapsed = elapsedBeforePauseMillis + elapsedThisSession

                val timeSec = totalElapsed / 1000.0
                _globalTime.doubleValue = timeSec
                savedStateHandle[KEY_GLOBAL_TIME] = timeSec

                _globalWeightPoints.add(WeightPoint(timeSec, _globalWeight.doubleValue))
                _globalFlowRate.doubleValue = getPositiveWeightChangeLastSecond()
                _globalFlowRatePoints.add((WeightPoint(timeSec, _globalFlowRate.doubleValue)))

                delay(100L)
            }
        }
    }

    private fun pauseTimer() {
        if (!internalIsRunning) return

        internalIsRunning = false
        savedStateHandle[KEY_IS_RUNNING] = false

        timerJob?.cancel()
        timerJob = null

        val now = System.currentTimeMillis()
        // накапливаем время этой сессии
        val elapsedThisSession = now - startTimeMillis
        elapsedBeforePauseMillis += elapsedThisSession

        // сбрасываем стартовую метку
        startTimeMillis = 0L

        // сохраняем накопленное
        savedStateHandle[KEY_ELAPSED_BEFORE_PAUSE] = elapsedBeforePauseMillis
        savedStateHandle[KEY_START_TIME] = 0L
    }

    open fun resetTimer() {
        if (internalIsRunning) {
            pauseTimer()
        }

        // Сбрасываем отображаемые данные
        _globalWeightPoints.clear()
        _globalFlowRatePoints.clear()
        _globalWeight.doubleValue = 0.0
        _globalDoze.doubleValue = 0.0
        _globalFlowRate.doubleValue = 0.0
        _globalTime.doubleValue = 0.0
        _globalRatio.doubleValue = 0.0

        // Сбрасываем внутренние счётчики
        internalIsRunning = false
        elapsedBeforePauseMillis = 0L
        startTimeMillis = 0L

        // Сбрасываем сохранённое состояние
        savedStateHandle[KEY_IS_RUNNING] = false
        savedStateHandle[KEY_ELAPSED_BEFORE_PAUSE] = 0L
        savedStateHandle[KEY_START_TIME] = 0L
        savedStateHandle[KEY_GLOBAL_TIME] = 0.0
    }

    open fun resetScale() {
        bleScaleManager?.sendCommandToScale("RESET")
    }

    // Методы обновления, изменяют внутренние состояния
    open fun updateGlobalWeight(value: Double) {
        _globalWeight.doubleValue = value
        savedStateHandle[KEY_GLOBAL_WEIGHT] = value
    }

    open fun updateGlobalDoze(value: Double) {
        _globalDoze.doubleValue = value
        savedStateHandle[KEY_GLOBAL_DOSE] = value
    }

    open fun updateGlobalFlowRate(value: Double) {
        _globalFlowRate.doubleValue = value
        savedStateHandle[KEY_GLOBAL_FLOW_RATE] = value
    }

    open fun updateGlobalRatio(value: Double) {
        _globalRatio.doubleValue = value
        savedStateHandle[KEY_GLOBAL_RATIO] = value
    }

    open fun manualUpdateGlobalRatio() {
        val ratio = if (_globalDoze.doubleValue > 0 && _globalWeight.doubleValue > 0) {
            _globalWeight.doubleValue / _globalDoze.doubleValue
        } else {
            0.0
        }
        _globalRatio.doubleValue = ratio
        savedStateHandle[KEY_GLOBAL_RATIO] = ratio
    }

    open fun updateGlobalIsConnecting(value: Boolean) {
        _globalIsConnecting.value = value
        savedStateHandle[KEY_IS_CONNECTING] = value
    }

    open fun updateGlobalIsConnected(value: Boolean) {
        _globalIsConnected.value = value
        savedStateHandle[KEY_IS_CONNECTED] = value
    }

    open fun getGlobalWeightFormatted(): String {
        return String.format("%.1f", _globalWeight.doubleValue)
    }

    open fun getGlobalDozeFormatted(): String {
        return String.format("%.1f", _globalDoze.doubleValue)
    }

    open fun getGlobalFlowRateFormatted(): String {
        return String.format("%.1f", _globalFlowRate.doubleValue)
    }

    open fun getGlobalRatioFormatted(): String {
        return "1:" + String.format("%.1f", _globalRatio.doubleValue)
    }

    open fun getIsConnecting(): Boolean {
        return _globalIsConnecting.value
    }

    open fun getIsConnected(): Boolean {
        return _globalIsConnected.value
    }

    open fun getTimeFormatted(): String {
        val totalTenthSeconds = (_globalTime.doubleValue * 10).toInt()
        val minutes = totalTenthSeconds / 600
        val seconds = (totalTenthSeconds / 10) % 60
        val tenths = totalTenthSeconds % 10
        return String.format("%02d:%02d.%d", minutes, seconds, tenths)
    }

    open fun connectionButtonHandler() {
        if (_globalIsConnected.value) {
            toggleTimer()
        } else if (!_globalIsConnecting.value) {
            startScanningAndConnect()
        }
    }
}

// Fake ViewModel для Preview
class FakeMainViewModel : MainViewModel(SavedStateHandle()) {

    // вместо var globalWeight: State<Double> — сразу override val
    override val globalWeight: State<Double> = mutableDoubleStateOf(210.4)
    override val globalDoze: State<Double> = mutableDoubleStateOf(15.0)
    override val globalFlowRate: State<Double> = mutableDoubleStateOf(0.0)
    override val globalTime: State<Double> = mutableDoubleStateOf(180.3)
    override val globalRatio: State<Double> = mutableDoubleStateOf(14.02)
    override val globalIsConnecting: State<Boolean> = mutableStateOf(false)
    override val globalIsConnected: State<Boolean> = mutableStateOf(false)
    override val isRunning: State<Boolean> = mutableStateOf(false)

    private val _globalWeightPoints = mutableStateListOf<WeightPoint>().apply {
        add(WeightPoint(0.0, 0.0))
        add(WeightPoint(14.0, 75.0))
        add(WeightPoint(28.0, 75.0))
        add(WeightPoint(42.0, 110.0))
        add(WeightPoint(70.0, 110.0))
        add(WeightPoint(80.0, 160.0))
        add(WeightPoint(98.0, 160.0))
        add(WeightPoint(100.0, 200.0))
        add(WeightPoint(120.0, 200.0))
        add(WeightPoint(130.0, 250.0))
        add(WeightPoint(180.0, 250.0))
    }

    override val globalWeightPoints: State<List<WeightPoint>> =
        derivedStateOf { _globalWeightPoints.toList() }

    private val _globalFlowRatePoints = mutableStateListOf<WeightPoint>().apply {
        add(WeightPoint(0.0, 0.0))
        add(WeightPoint(14.0, 4.0))
        add(WeightPoint(28.0, 0.0))
        add(WeightPoint(42.0, 3.8))
        add(WeightPoint(70.0, 0.0))
        add(WeightPoint(80.0, 4.3))
        add(WeightPoint(98.0, 0.0))
        add(WeightPoint(100.0, 4.0))
        add(WeightPoint(120.0, 0.0))
        add(WeightPoint(130.0, 4.1))
        add(WeightPoint(180.0, 0.0))
    }

    override val globalFlowRatePoints: State<List<WeightPoint>> =
        derivedStateOf { _globalFlowRatePoints.toList() }

    override fun getGlobalWeightFormatted(): String {
        return String.format("%.1f", globalWeight.value)
    }

    override fun getGlobalDozeFormatted(): String {
        return String.format("%.1f", globalDoze.value)
    }

    override fun getGlobalFlowRateFormatted(): String {
        return String.format("%.1f", globalFlowRate.value)
    }

    override fun getGlobalRatioFormatted(): String {
        return "1:" + String.format("%.1f", globalRatio.value)
    }

    override fun getIsConnecting(): Boolean {
        return globalIsConnecting.value
    }

    override fun getIsConnected(): Boolean {
        return globalIsConnected.value
    }

    override fun getTimeFormatted(): String {
        val totalTenthSeconds = (globalTime.value * 10).toInt()
        val minutes = totalTenthSeconds / 600
        val seconds = (totalTenthSeconds / 10) % 60
        val tenths = totalTenthSeconds % 10
        return String.format("%02d:%02d.%d", minutes, seconds, tenths)
    }

    override fun updateGlobalIsConnected(value: Boolean) {
        // Чтобы менять состояние в preview
        (globalIsConnected as MutableState<Boolean>).value = value
    }

    override fun resetTimer() {
        _globalWeightPoints.clear()
        _globalFlowRatePoints.clear()
        (globalWeight as MutableState<Double>).value = 0.0
        (globalDoze as MutableState<Double>).value = 0.0
        (globalFlowRate as MutableState<Double>).value = 0.0
        (globalTime as MutableState<Double>).value = 0.0
        (globalRatio as MutableState<Double>).value = 0.0
        (isRunning as MutableState<Boolean>).value = false
    }

    override fun toggleTimer() {
        if ((isRunning as MutableState<Boolean>).value) {
            isRunning.value = false
        } else {
            isRunning.value = true
        }
    }

    override fun connectionButtonHandler() {
        when {
            !globalIsConnected.value -> {
                (globalIsConnected as MutableState<Boolean>).value = true
            }

            globalIsConnected.value -> {
                toggleTimer()
            }
        }
    }

}

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AnotherCoffeeScaleTheme {
                MainScreen(
                    toolBarText = "Заваривание"
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmallInfoCard(
    label: String = "Вес",
    cardValue: String,
    modifier: Modifier,
    onClick: (() -> Unit)? = null  // Опциональный обработчик клика
) {
    Card(
        modifier = modifier // чтобы карточка занимала равную ширину в Row
            .padding(1.dp) // небольшой отступ внутри Row
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(1.dp)
                .clickable(enabled = onClick != null) { onClick?.invoke() }, // если onClick не null — делаем кликабельным,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            Text(
                text = cardValue,
                fontSize = 32.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                // можно добавить стиль, размер шрифта и т.п.
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChartCard(
    label: String = "Вес",
    modifier: Modifier,
    weightPoints: List<WeightPoint>,
) {
    val accumulatedTimes = remember { mutableStateListOf<String>() }
    val accumulatedWeights = remember { mutableStateListOf<Double>() }

    fun formatTime(seconds: Double): String {
        val totalTenthSeconds = (seconds * 10).toInt()
        val minutes = totalTenthSeconds / 600
        val secondsPart = (totalTenthSeconds / 10) % 60
        return String.format("%02d:%02d", minutes, secondsPart)
    }

    // Уникальные времена из данных, отсортированные
    val uniqueTimes = remember(weightPoints) {
        weightPoints.map { it.timeSeconds }.distinct().sorted()
    }

    val timeLabels = remember(uniqueTimes) {
        if (uniqueTimes.size <= 5) {
            // Показываем все уникальные метки, если их мало
            uniqueTimes
        } else {
            // Делим диапазон от 0 до последнего значения в uniqueTimes
            val start = 0.0
            val end = if (uniqueTimes.isNotEmpty()) uniqueTimes.last() else 0.0

            val countLabels = 5
            val interval = if (countLabels > 1) (end - start) / (countLabels - 1) else 0.0

            (0 until countLabels).map { i ->
                start + i * interval
            }
        }
    }

    val timeLabelsStrings = remember(timeLabels) {
        timeLabels.map { formatTime(it) }
    }

    val weights = remember(weightPoints) {
        if (weightPoints.isEmpty()) listOf(0.0) else weightPoints.map { it.weight }
    }

    val newLabels = timeLabelsStrings.drop(accumulatedTimes.size)
    val newWeights = weights.drop(accumulatedWeights.size)

    if (newLabels.isNotEmpty()) {
        accumulatedTimes.addAll(newLabels)
    }

    if (newWeights.isNotEmpty()) {
        accumulatedWeights.addAll(newWeights)
    }

    if (accumulatedWeights.size > weights.size) {
        accumulatedWeights.clear()
    }

    if (accumulatedTimes.size > timeLabelsStrings.size) {
        accumulatedTimes.clear()
    }

    Card(
        modifier = modifier.padding(1.dp)
    ) {
        LineChart(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            animationDelay = 0,
            data = remember(weights) {
                listOf(
                    Line(
                        label = label,
                        values = accumulatedWeights,
                        color = SolidColor(Color(0xFF23af92)),
                        // firstGradientFillColor = Color(0xFF2BC0A1).copy(alpha = .5f),
                        // secondGradientFillColor = Color.Transparent,
                        // strokeAnimationSpec = tween(2000, easing = EaseInOutCubic),
                        // gradientAnimationDelay = 1000,
                        // drawStyle = DrawStyle.Stroke(width = 2.dp),
                        strokeAnimationSpec = snap(),
                        gradientAnimationSpec = snap(),
                        gradientAnimationDelay = 0,
                        drawStyle = DrawStyle.Fill,
                    )
                )
            },
            labelProperties = LabelProperties(
                enabled = true,
                textStyle = TextStyle(
                    color = if (isSystemInDarkTheme()) {
                        Color.White
                    } else {
                        Color.Black
                    }, fontSize = 12.sp
                ),
                labels = accumulatedTimes,
            ),
            labelHelperProperties = LabelHelperProperties(
                enabled = true,
                textStyle = TextStyle(
                    color = if (isSystemInDarkTheme()) {
                        Color.White
                    } else {
                        Color.Black
                    }, fontSize = 12.sp
                )
            ),
            indicatorProperties = HorizontalIndicatorProperties(
                enabled = true,
                textStyle = TextStyle(
                    color = if (isSystemInDarkTheme()) {
                        Color.White
                    } else {
                        Color.Black
                    }, fontSize = 12.sp
                ),
            ),
            // animationMode = AnimationMode.Together(delayBuilder = {
            //     it * 500L
            // }),
            animationMode = AnimationMode.OneByOne
        )
    }
}

@Composable
fun DoseEditDialog(
    initialDose: Double,
    onDismissRequest: () -> Unit,
    onConfirm: (Double) -> Unit
) {
    var editedDozeText by remember { mutableStateOf(String.format("%.1f", initialDose)) }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Изменить дозу") },
        text = {
            OutlinedTextField(
                value = editedDozeText,
                onValueChange = { newText ->
                    if (newText.isEmpty() || newText.matches(Regex("""\d*([.,]\d*)?"""))) {
                        editedDozeText = newText
                    }
                },
                label = { Text("Доза") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(onClick = {
                val newDose = editedDozeText.replace(',', '.').toDoubleOrNull()
                if (newDose != null) {
                    onConfirm(newDose)
                } else {
                    // Можно добавить обработку ошибки (например Snackbar)
                }
            }) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Отмена")
            }
        }
    )
}

@Composable
fun AutomationMenu(
    isAutoStart: Boolean,
    isAutoDose: Boolean,
    isAutoTare: Boolean,
    onAutoStartChanged: (Boolean) -> Unit,
    onAutoDoseChanged: (Boolean) -> Unit,
    onAutoTareChanged: (Boolean) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val enabledCount = listOf(isAutoStart, isAutoDose, isAutoTare).count { it }

    BadgedBox(
        badge = {
            if (enabledCount > 0) {
                Badge {
                    Text(enabledCount.toString())
                }
            }
        }
    ) {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = "Автоматизация")
        }
    }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false }
    ) {
        DropdownMenuItem(
            onClick = {
                onAutoStartChanged(!isAutoStart)
                // expanded = false // чтобы меню закрывалось после выбора
            },
            text = { Text("Автостарт") },
            leadingIcon = {
                if (isAutoStart) {
                    Icon(Icons.Filled.CheckBox, contentDescription = null)
                } else {
                    Icon(Icons.Filled.CheckBoxOutlineBlank, contentDescription = null)
                }
            },
        )
        DropdownMenuItem(
            onClick = {
                onAutoDoseChanged(!isAutoDose)
                // expanded = false
            },
            text = { Text("Автодоза") },
            leadingIcon = {
                if (isAutoDose) {
                    Icon(Icons.Filled.CheckBox, contentDescription = null)
                } else {
                    Icon(Icons.Filled.CheckBoxOutlineBlank, contentDescription = null)
                }
            },
        )
        DropdownMenuItem(
            onClick = {
                onAutoTareChanged(!isAutoTare)
                // expanded = false
            },
            text = { Text("Автотара") },
            leadingIcon = {
                if (isAutoTare) {
                    Icon(Icons.Filled.CheckBox, contentDescription = null)
                } else {
                    Icon(Icons.Filled.CheckBoxOutlineBlank, contentDescription = null)
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    toolBarText: String, mainViewModel: MainViewModel = viewModel(
        factory = MainViewModelFactory(LocalContext.current)
    ),
    onConnectionButtonClick: (() -> Unit)? = null,
    onDozeButtonClick: (() -> Unit)? = null
) {
    val context = LocalContext.current

    val isAutoStart by mainViewModel.isAutoStart
    val isAutoDose by mainViewModel.isAutoDose
    val isAutoTare by mainViewModel.isAutoTare

    val bluetoothPermissions = arrayOf(
        Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT
    )

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(), onResult = { permissions ->
            val granted = permissions.values.all { it == true }
            if (granted) {
                // Если разрешения даны — запускаем сканирование
                mainViewModel.startScanningAndConnect()
            } else {
                Toast.makeText(context, "Нужны Bluetooth разрешения", Toast.LENGTH_SHORT).show()
            }
        })

    fun checkAndRequestPermissions() {
        val hasAllPermissions = bluetoothPermissions.all { permission ->
            ContextCompat.checkSelfPermission(
                context, permission
            ) == PackageManager.PERMISSION_GRANTED
        }
        if (hasAllPermissions) {
            mainViewModel.startScanningAndConnect()
        } else {
            permissionLauncher.launch(bluetoothPermissions)
        }
    }

    val weight by mainViewModel.globalWeight
    val doze by mainViewModel.globalDoze
    val flowRate by mainViewModel.globalFlowRate
    val time by mainViewModel.globalTime
    val ratio by mainViewModel.globalRatio

    val weightPoints by mainViewModel.globalWeightPoints
    val flowRatePoints by mainViewModel.globalFlowRatePoints

    val isConnecting by mainViewModel.globalIsConnecting
    val isConnected by mainViewModel.globalIsConnected
    val isRunning by mainViewModel.isRunning

    val weightText = String.format("%.1f", weight)
    val dozeText = String.format("%.1f", doze)
    val flowText = String.format("%.1f", flowRate)
    val ratioText = "1:" + String.format("%.1f", ratio)

    var showDozeDialog by remember { mutableStateOf(false) }

    val totalTenthSeconds = (time * 10).toInt()
    val minutes = totalTenthSeconds / 600
    val seconds = (totalTenthSeconds / 10) % 60
    val tenths = totalTenthSeconds % 10
    val timeText = String.format("%02d:%02d.%d", minutes, seconds, tenths)

    val configuration = LocalConfiguration.current
    val isPortrait =
        configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT
    val screenWidthDp = currentWindowDpSize().width
    val isMediumBreakpoint = screenWidthDp > 599.dp

    if (showDozeDialog) {
        DoseEditDialog(
            initialDose = doze,
            onDismissRequest = { showDozeDialog = false },
            onConfirm = { newDose ->
                mainViewModel.updateGlobalDoze(newDose)
                mainViewModel.manualUpdateGlobalRatio()
                showDozeDialog = false
            }
        )
    }

    @Composable
    fun NavigationContent() {
        NavigationBar {
            NavigationBarItem(
                icon = { Icon(Icons.Filled.LocalCafe, contentDescription = "Доза") },
                label = {
                    Text(
                        "Доза", overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                },
                selected = false,
                enabled = isConnected && !isRunning,
                onClick = {
                    if (onDozeButtonClick != null) {
                        // При переданном лямбда-обработчике используем ее (для Preview)
                        onDozeButtonClick()
                    } else {
                        mainViewModel.setDoze()
                    }
                })
            NavigationBarItem(icon = {
                if (isConnected) {
                    if (isRunning) {
                        Icon(Icons.Filled.PauseCircle, contentDescription = "Пауза")
                    } else {
                        Icon(Icons.Filled.PlayCircle, contentDescription = "Старт")
                    }
                } else {
                    if (isConnecting) {
                        Icon(Icons.Filled.Bluetooth, contentDescription = "Подключение...")
                    } else {
                        Icon(Icons.Filled.BluetoothDisabled, contentDescription = "Подключиться")
                    }
                }
            }, label = {
                Text(
                    if (isConnected) {
                        if (isRunning) {
                            "Пауза"
                        } else {
                            "Старт"
                        }
                    } else {
                        if (isConnecting) {
                            "Подключение"
                        } else {
                            "Подключиться"
                        }
                    },
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }, selected = isConnected, onClick = {
                if (onConnectionButtonClick != null) {
                    // При переданном лямбда-обработчике используем ее (для Preview)
                    onConnectionButtonClick()
                } else {
                    // Оригинальная логика для запуска в продакшене
                    if (mainViewModel.getIsConnected()) {
                        mainViewModel.toggleTimer()
                    } else if (!mainViewModel.getIsConnecting()) {
                        checkAndRequestPermissions()
                    }
                }
            })
            NavigationBarItem(
                icon = { Icon(Icons.Filled.Refresh, contentDescription = "Сброс") },
                label = {
                    Text(
                        "Сброс", overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                },
                selected = false,
                onClick = {
                    mainViewModel.resetTimer()
                    if (isConnected) {
                        mainViewModel.resetScale()
                    }
                })
        }
    }

    @Composable
    fun NavigationRailContent() {
        NavigationRail(
            modifier = Modifier
                .width(64.dp)
                .fillMaxHeight()
                .padding(end = 4.dp)
        ) {
            NavigationRailItem(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                icon = { Icon(Icons.Filled.LocalCafe, contentDescription = "Доза") },
                label = { Text("Доза", overflow = TextOverflow.Ellipsis) },
                selected = false,
                enabled = isConnected,
                onClick = { /* ... */ })
            NavigationRailItem(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f), icon = {
                    if (isConnected) {
                        if (isRunning) {
                            Icon(Icons.Filled.PauseCircle, contentDescription = "Пауза")
                        } else {
                            Icon(Icons.Filled.PlayCircle, contentDescription = "Старт")
                        }
                    } else {
                        if (isConnecting) {
                            Icon(Icons.Filled.Bluetooth, contentDescription = "Подключение...")
                        } else {
                            Icon(
                                Icons.Filled.BluetoothDisabled, contentDescription = "Подключиться"
                            )
                        }
                    }
                }, label = {
                    Text(
                        if (isConnected) {
                            if (isRunning) {
                                "Пауза"
                            } else {
                                "Старт"
                            }
                        } else {
                            if (isConnecting) {
                                "Подключение"
                            } else {
                                "Подключиться"
                            }
                        },
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }, selected = isConnected, onClick = {
                    if (onConnectionButtonClick != null) {
                        // При переданном лямбда-обработчике используем ее (для Preview)
                        onConnectionButtonClick()
                    } else {
                        // Оригинальная логика для запуска в продакшене
                        if (mainViewModel.getIsConnected()) {
                            mainViewModel.toggleTimer()
                        } else if (!mainViewModel.getIsConnecting()) {
                            checkAndRequestPermissions()
                        }
                    }
                })
            NavigationRailItem(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                icon = { Icon(Icons.Filled.Refresh, contentDescription = "Сброс") },
                label = {
                    Text(
                        "Сброс", overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                },
                selected = false,
                onClick = {
                    mainViewModel.resetTimer()
                    if (isConnected) {
                        mainViewModel.resetScale()
                    }
                })
        }
    }

    Scaffold(topBar = {
        TopAppBar(
            title = {
                Text(
                    toolBarText, overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            },
            navigationIcon = {
                IconButton(onClick = { /* "Open nav drawer" */ }) {
                    Icon(Icons.Filled.Menu, contentDescription = "Меню")
                }
            },
            actions = {
                IconButton(onClick = {
                    mainViewModel.resetTimer()
                    if (isConnected) {
                        mainViewModel.resetScale()
                    }
                }) {
                    Icon(
                        imageVector = Icons.Filled.Refresh, contentDescription = "Сброс"
                    )
                }
                IconButton(onClick = { /* do something */ }) {
                    Icon(
                        imageVector = Icons.Filled.Save, contentDescription = "Сохранить"
                    )
                }
                AutomationMenu(
                    isAutoStart = isAutoStart,
                    isAutoDose = isAutoDose,
                    isAutoTare = isAutoTare,
                    onAutoStartChanged = { mainViewModel.updateAutoStart(it) },
                    onAutoDoseChanged = { mainViewModel.updateAutoDose(it) },
                    onAutoTareChanged = { mainViewModel.updateAutoTare(it) }
                )
            },
        )
    }, bottomBar = {
        if (isPortrait && !isMediumBreakpoint) {
            NavigationContent()
        } else null
    }, content = { innerPadding ->
        if (!isPortrait || isMediumBreakpoint) {
            Row(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(end = 4.dp),
                    verticalArrangement = Arrangement.Top
                ) {
                    if (isPortrait) {
                        PortraitContent(
                            globalWeight = weightText,
                            globalDoze = dozeText,
                            globalFlow = flowText,
                            globalTime = timeText,
                            globalRatio = ratioText,
                            weightPoints = weightPoints,
                            flowRatePoints = flowRatePoints,
                            innerPadding = innerPadding,
                            onDozeClick = {
                                showDozeDialog = true
                            }
                        )
                    } else {
                        LandscapeContent(
                            globalWeight = weightText,
                            globalDoze = dozeText,
                            globalFlow = flowText,
                            globalTime = timeText,
                            globalRatio = ratioText,
                            weightPoints = weightPoints,
                            flowRatePoints = flowRatePoints,
                            innerPadding = innerPadding,
                            onDozeClick = {
                                showDozeDialog = true
                            }
                        )
                    }
                }
                Column(
                    modifier = Modifier.fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    NavigationRailContent()
                }
            }
        } else {
            if (isPortrait) {
                PortraitContent(
                    globalWeight = weightText,
                    globalDoze = dozeText,
                    globalFlow = flowText,
                    globalTime = timeText,
                    globalRatio = ratioText,
                    weightPoints = weightPoints,
                    flowRatePoints = flowRatePoints,
                    innerPadding = innerPadding,
                    onDozeClick = {
                        showDozeDialog = true
                    }
                )
            } else {
                LandscapeContent(
                    globalWeight = weightText,
                    globalDoze = dozeText,
                    globalFlow = flowText,
                    globalTime = timeText,
                    globalRatio = ratioText,
                    weightPoints = weightPoints,
                    flowRatePoints = flowRatePoints,
                    innerPadding = innerPadding,
                    onDozeClick = {
                        showDozeDialog = true
                    }
                )
            }
        }
    })
}

@Composable
fun PortraitContent(
    globalWeight: String,
    globalDoze: String,
    globalFlow: String,
    globalTime: String,
    globalRatio: String,
    weightPoints: List<WeightPoint> = emptyList(),
    flowRatePoints: List<WeightPoint> = emptyList(),
    innerPadding: androidx.compose.foundation.layout.PaddingValues,
    onDozeClick: (() -> Unit)? = null  // Опциональный callback для нажатия на "Доза"
) {
    Column(
        modifier = Modifier
            .padding(innerPadding)
            .fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            SmallInfoCard(
                label = "Вес", cardValue = globalWeight, modifier = Modifier.weight(1f)
            )
            SmallInfoCard(
                label = "Доза",
                cardValue = globalDoze,
                modifier = Modifier.weight(1f),
                onClick = onDozeClick
            )
            SmallInfoCard(
                label = "Скорость пролива", cardValue = globalFlow, modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            SmallInfoCard(
                label = "Время", cardValue = globalTime, modifier = Modifier.weight(1f)
            )
            SmallInfoCard(
                label = "Пропорция", cardValue = globalRatio, modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                ChartCard(
                    label = "Вес", modifier = Modifier.weight(2f), weightPoints = weightPoints
                )
                ChartCard(
                    label = "Скорость пролива",
                    modifier = Modifier.weight(1f),
                    weightPoints = flowRatePoints
                )
            }
        }
    }
}

@Composable
fun LandscapeContent(
    globalWeight: String,
    globalDoze: String,
    globalFlow: String,
    globalTime: String,
    globalRatio: String,
    weightPoints: List<WeightPoint> = emptyList(),
    flowRatePoints: List<WeightPoint> = emptyList(),
    innerPadding: androidx.compose.foundation.layout.PaddingValues,
    onDozeClick: (() -> Unit)? = null  // Опциональный callback для нажатия на "Доза"
) {
    Column(
        modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Row(
                modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                SmallInfoCard(
                    label = "Вес", cardValue = globalWeight, modifier = Modifier.weight(1f)
                )
                SmallInfoCard(
                    label = "Доза",
                    cardValue = globalDoze,
                    modifier = Modifier.weight(1f),
                    onClick = onDozeClick
                )
                SmallInfoCard(
                    label = "Скорость пролива",
                    cardValue = globalFlow,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                SmallInfoCard(
                    label = "Время", cardValue = globalTime, modifier = Modifier.weight(1f)
                )
                SmallInfoCard(
                    label = "Пропорция", cardValue = globalRatio, modifier = Modifier.weight(1f)
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                ChartCard(
                    label = "Вес",
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    weightPoints = weightPoints
                )
                ChartCard(
                    label = "Скорость пролива",
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    weightPoints = flowRatePoints
                )
            }
        }
    }
}

@Preview(showBackground = true, fontScale = 1f)
@Composable
fun MainScreenPreview() {
    val fakeViewModel = remember { FakeMainViewModel() }
    AnotherCoffeeScaleTheme {
        MainScreen(
            toolBarText = "Заваривание", mainViewModel = fakeViewModel, onConnectionButtonClick = {
                val connectedState = fakeViewModel.globalIsConnected as MutableState<Boolean>
                connectedState.value = !connectedState.value
            }, onDozeButtonClick = {
                val dozeValue = fakeViewModel.globalDoze as MutableState<Double>
                dozeValue.value = 42.0
            })
    }
}