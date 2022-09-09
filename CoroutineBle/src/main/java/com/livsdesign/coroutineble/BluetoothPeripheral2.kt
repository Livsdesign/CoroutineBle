package com.livsdesign.coroutineble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.IntRange
import com.livsdesign.coroutineble.model.ConnectionState
import com.livsdesign.coroutineble.usecase.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.*


private const val CLIENT_CHARACTERISTIC_CONFIG = "00002902-0000-1000-8000-00805f9b34fb"
private val UUID_CLIENT_CHARACTERISTIC_CONFIG = UUID.fromString(CLIENT_CHARACTERISTIC_CONFIG)

@SuppressLint("MissingPermission")
class BluetoothPeripheral2(
    private val context: Context,
    private val device: BluetoothDevice
) {

    private var callbackScope = CoroutineScope(Dispatchers.Default)

    private val _connectionState = MutableStateFlow(ConnectionState.IDLE)
    private val _mtuState = MutableStateFlow(23)
    private val _notifyDataState = MutableSharedFlow<BluetoothData>()
    private val _services = MutableStateFlow<List<BluetoothGattService>>(emptyList())

    ///////////////////////////////////////////////////////////////////////////
    // private
    ///////////////////////////////////////////////////////////////////////////
    private val writeUseCase = WriteUseCase()
    private val batchWriteTasksUseCase = BatchWriteTasksUseCase()
    private val readUseCase = ReadUseCase()
    private val readDescriptorUseCase = ReadDescriptorUseCase()
    private val writeDescriptorUseCase = WriteDescriptorUseCase()
    private val requestMtuUseCase = RequestMtuUseCase()

    private var mGatt: BluetoothGatt? = null
    private var activeClose = false
    private val gattCallback: BluetoothGattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            _connectionState.value = when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    if (!gatt.discoverServices()) {
                        Log.e("BluetoothPeripheral", "discoverServices failed!")
                        activeClose = true
                        gatt.disconnect()
                    }
                    ConnectionState.CONNECTING
                }
                BluetoothProfile.STATE_CONNECTING -> ConnectionState.CONNECTING
                BluetoothProfile.STATE_DISCONNECTED -> {
                    gatt.close()
                    if (mGatt == null) {
                        ConnectionState.FAILED
                    } else {
//                        if (status == 133) refreshDeviceCache()
                        val result =
                            if (activeClose) ConnectionState.DISCONNECTED else ConnectionState.LOST
                        activeClose = false
                        result
                    }
                }
                BluetoothProfile.STATE_DISCONNECTING -> ConnectionState.DISCONNECTING
                else -> ConnectionState.FAILED
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                _services.value = gatt.services
                _connectionState.value = ConnectionState.CONNECTED
            } else {
                _services.value = emptyList()
                Log.e("BluetoothPeripheral", "discoverServices failed!")
                activeClose = true
                mGatt?.disconnect()
            }
        }

        override fun onServiceChanged(gatt: BluetoothGatt) {
            _services.value = gatt.services
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            writeDescriptorUseCase.onDescriptorWrite(gatt, descriptor, status)
        }

        override fun onDescriptorRead(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            readDescriptorUseCase.onDescriptorRead(gatt, descriptor, status)
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            readUseCase.onCharacteristicRead(gatt, characteristic, status)
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (batchWriteTasksUseCase.isActive()) {
                batchWriteTasksUseCase.onCharacteristicWrite(gatt, characteristic, status)
            } else if (writeUseCase.isActive()) {
                writeUseCase.onCharacteristicWrite(gatt, characteristic, status)
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (callbackScope.isActive) {
                callbackScope.launch {
                    _notifyDataState.emit(BluetoothData(characteristic.uuid, characteristic.value))
                }
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            _mtuState.value = mtu
            requestMtuUseCase.onMtuChanged(gatt, mtu, status)
        }

        @Suppress("UNUSED_PARAMETER")
        fun onConnectionUpdated(
            gatt: BluetoothGatt,
            interval: Int,
            latency: Int,
            timeout: Int,
            status: Int
        ) {
            //todo
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // request
    ///////////////////////////////////////////////////////////////////////////
    // LOCK 虽然BluetoothGatt内部存在mDeviceBusy lock
    private val bleOperationMutex = Mutex()

    suspend fun connect(phy: Int? = null): Boolean {
        bleOperationMutex.withLock {
            if (_connectionState.value == ConnectionState.CONNECTED) {
                return true
            }
            mGatt = requestConnect(false, phy, gattCallback)
            _connectionState.value = ConnectionState.CONNECTING
            val state = _connectionState.first {
                when (it) {
                    ConnectionState.CONNECTED, ConnectionState.DISCONNECTED, ConnectionState.LOST, ConnectionState.FAILED -> true
                    else -> false
                }
            }
            return state == ConnectionState.CONNECTED
        }
    }

    /**
     * 1. 如果连接期间用户关闭了蓝牙，那么system不会自动连接
     * 2. 如果用户主动断开连接，也不会自动连接
     */
    fun autoConnect(phy: Int? = null): Boolean {
        synchronized(this) {
            if (mGatt != null && _connectionState.value == ConnectionState.CONNECTED) {
                //todo 如果上一次为连接为自动连接，这里忽略这个请求，等待系统自动连接
                return true
            }
            mGatt = requestConnect(true, phy, gattCallback)
            return mGatt != null
        }
    }

    fun disconnect() {
        mGatt?.apply {
            activeClose = true
            disconnect()
        }
    }

    /**
     * 有些手机配置phy连接会连接不上
     */
    private fun requestConnect(
        autoConnect: Boolean,
        phy: Int?,
        gattCallback: BluetoothGattCallback
    ): BluetoothGatt? {
        if (!callbackScope.isActive) callbackScope = CoroutineScope(Dispatchers.Default)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && phy != null) {
            device.connectGatt(
                context,
                true,
                gattCallback,
                BluetoothDevice.TRANSPORT_LE,
                phy
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            device.connectGatt(
                context,
                autoConnect,
                gattCallback,
                BluetoothDevice.TRANSPORT_LE
            )
        } else {
            device.connectGatt(context, autoConnect, gattCallback)
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    //
    ///////////////////////////////////////////////////////////////////////////

    private fun requireGatt(): BluetoothGatt {
        return requireNotNull(mGatt) {
            "Device not connected, Please call connect first"
        }
    }

    suspend fun write(
        characteristic: BluetoothGattCharacteristic,
        type: WriteType,
        value: ByteArray
    ): Boolean {
        return bleOperationMutex.withLock {
            if (!writeUseCase.isActive() && !batchWriteTasksUseCase.isActive()) {
                writeUseCase.execute(requireGatt(), characteristic, type, value)
            } else {
                false
            }
        }
    }

    suspend fun writeTasks(
        characteristic: BluetoothGattCharacteristic,
        tasks: List<ByteArray>
    ): Boolean {
        return bleOperationMutex.withLock {
            if (!batchWriteTasksUseCase.isActive() && !writeUseCase.isActive()) {
                batchWriteTasksUseCase.execute(requireGatt(), characteristic, tasks)
            } else {
                false
            }
        }
    }

    suspend fun writeDescriptor(descriptor: BluetoothGattDescriptor, value: ByteArray): Boolean {
        return bleOperationMutex.withLock {
            if (!writeDescriptorUseCase.isActive()) {
                writeDescriptorUseCase.execute(requireGatt(), descriptor, value)
            } else {
                false
            }
        }
    }

    suspend fun read(characteristic: BluetoothGattCharacteristic): ByteArray? {
        return bleOperationMutex.withLock {
            if (!readUseCase.isActive()) {
                readUseCase.execute(requireGatt(), characteristic)
            } else {
                null
            }
        }
    }

    suspend fun readDescriptor(descriptor: BluetoothGattDescriptor): ByteArray? {
        return bleOperationMutex.withLock {
            if (!readDescriptorUseCase.isActive()) {
                readDescriptorUseCase.execute(requireGatt(), descriptor)
            } else {
                null
            }
        }
    }

    suspend fun requestMtu(@IntRange(from = 23, to = 517) size: Int): Int? {
        return bleOperationMutex.withLock {
            if (!requestMtuUseCase.isActive()) {
                requestMtuUseCase.execute(requireGatt(), size)
            } else {
                null
            }
        }
    }

    fun requestConnectionPriority(connectionPriority: Int): Boolean {
        return mGatt?.requestConnectionPriority(connectionPriority) ?: false
    }

    suspend fun setNotification(
        characteristic: BluetoothGattCharacteristic,
        enable: Boolean
    ): Boolean {
        return bleOperationMutex.withLock {
            val descriptor =
                characteristic.getDescriptor(UUID_CLIENT_CHARACTERISTIC_CONFIG) ?: return false
            val res = requireGatt().setCharacteristicNotification(characteristic, enable)
            if (res) {
                if (!writeDescriptorUseCase.isActive()) {
                    val notify =
                        characteristic.hasProperty(BluetoothGattCharacteristic.PROPERTY_NOTIFY)
                    val value = if (enable) {
                        if (notify) BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE else BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                    } else {
                        BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
                    }
                    writeDescriptorUseCase.execute(requireGatt(), descriptor, value)
                } else {
                    false
                }
            } else {
                false
            }
        }
    }

    val connectionState: StateFlow<ConnectionState> = _connectionState
    val mtuState: StateFlow<Int> = _mtuState
    val notifyDataState: SharedFlow<BluetoothData> = _notifyDataState
    val services: StateFlow<List<BluetoothGattService>> = _services

    init {
        if (device.type == BluetoothDevice.DEVICE_TYPE_CLASSIC) {
            throw IllegalStateException("Not support Bluetooth Classic device")
        }
    }

}

class BluetoothData(
    val uuid: UUID,
    val data: ByteArray
) {

    private fun test() {}

}

enum class WriteType(val value: Int) {
    WithResponse(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT),
    WithoutResponse(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE),
    Signed(BluetoothGattCharacteristic.WRITE_TYPE_SIGNED)
}