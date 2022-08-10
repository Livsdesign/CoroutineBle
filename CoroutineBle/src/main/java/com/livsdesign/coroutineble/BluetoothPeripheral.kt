package com.livsdesign.coroutineble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.util.Log
import com.livsdesign.coroutineble.model.BleRequest
import com.livsdesign.coroutineble.model.BleResponse
import com.livsdesign.coroutineble.model.ConnectionState
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.*

const val W_RSP = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
const val W_NO_RSP = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
const val W_SIGNED = BluetoothGattCharacteristic.WRITE_TYPE_SIGNED

private const val CLIENT_CHARACTERISTIC_CONFIG = "00002902-0000-1000-8000-00805f9b34fb"
private val UUID_CLIENT_CHARACTERISTIC_CONFIG = UUID.fromString(CLIENT_CHARACTERISTIC_CONFIG)

@SuppressLint("MissingPermission")
class BluetoothPeripheral(
    private val context: Context,
    private val device: BluetoothDevice
) {

    init {
        if(device.type == BluetoothDevice.DEVICE_TYPE_CLASSIC){
            throw IllegalStateException("Not support Bluetooth Classic device")
        }
    }

    private val _connectionState = MutableStateFlow(ConnectionState.IDLE)
    private val _requestShare = MutableSharedFlow<BleRequest>()
    private val _responseShare = MutableSharedFlow<BleResponse>()
    private val internalResponseChannel = Channel<BleResponse>()//todo disconnect clear msg

    val connectionState: StateFlow<ConnectionState> get() = _connectionState
    val requestShared: SharedFlow<BleRequest> get() = _requestShare
    val responseShared: SharedFlow<BleResponse> get() = _responseShare

    suspend fun connect(): Boolean {
        if (!coroutineScope.isActive) coroutineScope = CoroutineScope(Dispatchers.Default)
        val request: BleRequest.Connect = BleRequest.Connect(context, device, gattCallback)
        mGatt = request.connectDevice()
        request.report()
        if (mGatt == null) return false
        _connectionState.value = ConnectionState.CONNECTING
        _connectionState.first {
            it == ConnectionState.CONNECTED
        }
        val state = _connectionState.first {
            when (it) {
                ConnectionState.CONNECTED, ConnectionState.DISCONNECTED, ConnectionState.LOST, ConnectionState.FAILED -> true
                else -> false
            }
        }
        return state == ConnectionState.CONNECTED
    }

    suspend fun discoverServices(): List<BluetoothGattService> {
        val rsp = BleRequest.DiscoverServices().handle<BleResponse.OnServicesDiscovered>()
        return rsp?.takeIf { it.isSuccess() }?.run { this.services } ?: emptyList()
    }

    fun getServices(): List<BluetoothGattService> {
        return mGatt?.services ?: emptyList()
    }

    fun findService(uuid: UUID): BluetoothGattService? {
        return mGatt?.getService(uuid)
    }

    fun BluetoothGattService.findCharacteristic(uuid: UUID): BluetoothGattCharacteristic? {
        return getCharacteristic(uuid)
    }

    fun disconnect() {
        BleRequest.Disconnect().apply {
            if (mGatt != null) {
                activeClose = true
                mGatt!!.execute()
            }
            report()
        }
    }

    fun release() {
        mGatt?.disconnect()
        mGatt?.close()
        internalResponseChannel.close()
        coroutineScope.cancel()
        mGatt = null
    }

    suspend fun write(
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray
    ): Boolean {
        val response = BleRequest.Write(characteristic, W_RSP, value)
            .handle<BleResponse.OnCharacteristicWrite>()
        return response?.isSuccess() ?: false
    }

    suspend fun writeWithoutResponse(
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray
    ): Boolean {
        val response = BleRequest.Write(characteristic, W_NO_RSP, value)
            .handle<BleResponse.OnCharacteristicWrite>()
        return response?.isSuccess() ?: false
    }

    suspend fun writeSigned(
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray
    ): Boolean {
        val response = BleRequest.Write(characteristic, W_SIGNED, value)
            .handle<BleResponse.OnCharacteristicWrite>()
        return response?.isSuccess() ?: false
    }

    suspend fun writeDescriptor(
        descriptor: BluetoothGattDescriptor,
        value: ByteArray
    ): Boolean {
        val response = BleRequest.WriteDescriptor(descriptor, value)
            .handle<BleResponse.OnDescriptorWrite>()
        return response?.isSuccess() ?: false
    }

    suspend fun read(characteristic: BluetoothGattCharacteristic): ByteArray? {
        val rsp = BleRequest.Read(characteristic).handle<BleResponse.OnCharacteristicRead>()
        return rsp?.takeIf { it.isSuccess() }?.run { this.characteristic.value }
    }

    suspend fun readDescriptor(descriptor: BluetoothGattDescriptor): ByteArray? {
        val rsp = BleRequest.ReadDescriptor(descriptor).handle<BleResponse.OnDescriptorRead>()
        return rsp?.takeIf { it.isSuccess() }?.run { this.descriptor.value }
    }

    suspend fun toggleNotification(
        characteristic: BluetoothGattCharacteristic,
        enable: Boolean
    ): Boolean {
        val request = BleRequest.SetNotification(characteristic, enable)
        request.apply {
            mGatt?.execute()
            report()
        }
        if (!request.result) return false
        val descriptor: BluetoothGattDescriptor? =
            characteristic.getDescriptor(UUID_CLIENT_CHARACTERISTIC_CONFIG)
        return if (descriptor == null) {
            val errorMsg =
                "Not Found CLIENT_CHARACTERISTIC_CONFIG($CLIENT_CHARACTERISTIC_CONFIG) by characteristic(${characteristic.uuid})"
            BleRequest.Error(errorMsg).report()
            false
        } else {
            val notify = characteristic.hasProperty(BluetoothGattCharacteristic.PROPERTY_NOTIFY)
            val cmd = if (enable) {
                if (notify) BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE else BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
            } else {
                BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
            }
            writeDescriptor(descriptor, cmd)
        }
    }

    fun notifications(
        characteristic: BluetoothGattCharacteristic,
        holdOn: Boolean = false
    ): Flow<ByteArray> = callbackFlow {
        launch {
            try {
                toggleNotification(characteristic, true).apply {
                    if (!this) throw IllegalStateException("Failed")
                }
                awaitCancellation()
            } finally {
                if (!holdOn) {
                    toggleNotification(characteristic, false)
                }
            }
        }
        _responseShare.filter {
            it is BleResponse.OnCharacteristicChanged && it.characteristic.uuid == characteristic.uuid
        }.collect {
            val value = (it as BleResponse.OnCharacteristicChanged).characteristic.value
            send(value)
        }
        //awaitClose {}
    }

    /**
     * 请求更换MTU（Max Transfer Unit）
     * 这是一个协商的过程，以双方均支持的值进行交互（迁就弱者原则）
     * @sample
     * ① 向外设请求512，外设最大支持251，那么外设回应251，双方以最大251交互
     * ② 向外设请求128，外设最大支持512，那么外设回应128，双方以最大128交互
     */
    suspend fun requestMtu(size: Int): Int? {
        val response = BleRequest.SetMTU(size).handle<BleResponse.OnMtuChanged>()
        return response?.takeIf { it.isSuccess() }?.run { this.mtu }
    }

    /**
     * @param priority
     * @see BluetoothGatt.CONNECTION_PRIORITY_BALANCED 0
     * @see BluetoothGatt.CONNECTION_PRIORITY_HIGH 1
     * @see BluetoothGatt.CONNECTION_PRIORITY_LOW_POWER 2
     */
    suspend fun requestConnectionPriority(priority: Int, delayTimeMillis: Long = 5000): Boolean {
        //因为callback不对应用层开放，这里请求成功就默认response成功
        val request = BleRequest.SetConnectionProperty(priority).apply {
            mGatt?.execute()
            report()
        }
        if (request.result) delay(delayTimeMillis)
        return request.result
    }

    ///////////////////////////////////////////////////////////////////////////
    // private Core logic
    ///////////////////////////////////////////////////////////////////////////

    // LOCK 虽然BluetoothGatt内部存在mDeviceBusy lock
    private val bleOperationMutex = Mutex()
    private var coroutineScope = CoroutineScope(Dispatchers.Main)
    private var mGatt: BluetoothGatt? = null
    private fun requireGatt(): BluetoothGatt =
        mGatt ?: throw IllegalStateException("Please Connect device first")

    //主动断开
    private var activeClose = false

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            _connectionState.value = when (newState) {
                BluetoothProfile.STATE_CONNECTED -> ConnectionState.CONNECTED
                BluetoothProfile.STATE_CONNECTING -> ConnectionState.CONNECTING
                BluetoothProfile.STATE_DISCONNECTED -> {
                    if (mGatt == null) {
                        ConnectionState.FAILED
                    } else {
                        if (status == 133) refreshDeviceCache()
                        val result =
                            if (activeClose) ConnectionState.DISCONNECTED else ConnectionState.LOST
                        activeClose = false
                        result
                    }
                }
                BluetoothProfile.STATE_DISCONNECTING -> ConnectionState.DISCONNECTING
                else -> ConnectionState.FAILED
            }
            BleResponse.OnConnectionStateChange(_connectionState.value).report()
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            BleResponse.OnServicesDiscovered(gatt.services, status).report()
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            BleResponse.OnDescriptorWrite(descriptor, status).report()
        }

        override fun onDescriptorRead(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            BleResponse.OnDescriptorRead(descriptor, status).report()
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            BleResponse.OnCharacteristicRead(characteristic, status).report()
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            BleResponse.OnCharacteristicWrite(characteristic, status).report()
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            BleResponse.OnCharacteristicChanged(characteristic).report()
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            BleResponse.OnMtuChanged(mtu, status).report()
        }
    }

    private suspend inline fun <reified T : BleResponse> BleRequest.handle(): T? {
        val request = this
        if (!coroutineScope.isActive) return null
        return try {
            bleOperationMutex.withLock {
                if (!coroutineScope.isActive) throw IllegalStateException("Connection was closed")
                request.apply {
                    requireGatt().execute()
                    report()
                    if (!result) throw IllegalStateException("Request Failed")
                }
                withTimeout(5000) {
                    if (request is BleRequest.SetMTU) {
                        //对于设备主动上报的消息（notify、indicate、mtu、connectState）会打破request-response的平衡，所以需要特殊处理
                        _responseShare.first {
                            it is BleResponse.OnMtuChanged
                        }
                    } else {
                        internalResponseChannel.receive()
                    } as T
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun BleRequest.report() {
        if (!coroutineScope.isActive) return
        coroutineScope.launch {
            _requestShare.emit(this@report)
        }
    }

    private fun BleResponse.report() {
        val rsp = this
        if (!coroutineScope.isActive) return
        coroutineScope.launch {
            //有两个例外是因为它们可以是外设主动发起，会打破request-response的关系，造成顺序混乱
            when (rsp) {
                is BleResponse.OnCharacteristicChanged, is BleResponse.OnMtuChanged, is BleResponse.OnConnectionStateChange -> {}
                else -> internalResponseChannel.send(rsp)
            }
            _responseShare.emit(rsp)
        }
    }

    @Synchronized
    private fun refreshDeviceCache() {
        try {
            val refresh = BluetoothGatt::class.java.getMethod("refresh")
            if (mGatt != null) {
                val success = refresh.invoke(mGatt) as Boolean
                Log.d("TAG", "refreshDeviceCache, is success:  $success")
            }
        } catch (e: java.lang.Exception) {
            Log.d("TAG", "exception occur while refreshing device: " + e.message)
            e.printStackTrace()
        }
    }


}