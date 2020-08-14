package com.livsdesign.coroutineble.connect

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattService
import android.util.Log
import androidx.annotation.IntRange
import com.clj.fastble.BleManager
import com.clj.fastble.callback.*
import com.clj.fastble.data.BleDevice
import com.clj.fastble.exception.BleException
import com.livsdesign.coroutineble.connect.model.*
import com.livsdesign.coroutineble.toHexString
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlin.coroutines.resume
import kotlin.properties.Delegates

typealias ConnectionStepChanged = (newStatus: ConnectionStep) -> Unit
typealias OnReceived = (received: ByteArray) -> Unit

class BleConnection internal constructor() {

    /**
     * LiveData是很香，但需要关联LifecycleOwner，如果没有订阅也不会执行
     */
    val mStatus = ConnectionStatus()

    private var internalStatus by Delegates.observable(ConnectionStep.IDLE,
        { _, _, newValue ->
            mStatus.current = newValue
            onStepChanged?.invoke(newValue)
        })

    private var onStepChanged: ConnectionStepChanged? = null


    fun setOnStatusChangedCallback(onChanged: ConnectionStepChanged?) {
        onStepChanged = onChanged
    }


    private var mDevice: BleDevice? = null
    private var mGatt: BluetoothGatt? = null

    suspend fun connect(mac: String): BleResult {
        return suspendCancellableCoroutine {
            val callback = object : BleGattCallback() {
                override fun onStartConnect() {
                    internalStatus = ConnectionStep.CONNECTING
                    mGatt = null
                }

                override fun onDisConnected(
                    isActiveDisConnected: Boolean,
                    device: BleDevice?,
                    gatt: BluetoothGatt?,
                    status: Int
                ) {
                    mGatt = null
                    gatt?.close()
                    mDevice = device
                    internalStatus = if (isActiveDisConnected) {
                        ConnectionStep.DISCONNECTED
                    } else {
                        ConnectionStep.LOST
                    }
                }

                override fun onConnectSuccess(
                    bleDevice: BleDevice?,
                    gatt: BluetoothGatt?,
                    status: Int
                ) {
                    mGatt = gatt
                    mDevice = bleDevice
                    internalStatus = ConnectionStep.CONNECTED
                    if (it.isActive) {
                        it.resume(Success(null))
                    }
                }

                override fun onConnectFail(bleDevice: BleDevice?, exception: BleException?) {
                    mGatt = null
                    internalStatus = ConnectionStep.FAILED
                    mDevice = bleDevice
                    if (it.isActive) {
                        it.resume(exception.toFailed())
                    }
                }
            }
            if (it.isActive) {
                val bleDevices = BleManager.getInstance().allConnectedDevice
                var connectedDevice: BleDevice? = null
                for (bleDevice in bleDevices) {
                    if (bleDevice.mac == mac) {
                        connectedDevice = bleDevice
                        break
                    }
                }
                if (connectedDevice == null) {
                    BleManager.getInstance().connect(mac, callback)
                } else {
                    //这个会导致其他对象对此
                    BleManager.getInstance().getBleBluetooth(connectedDevice)
                        .addConnectGattCallback(callback)
                    internalStatus = ConnectionStep.CONNECTED
                    it.resume(Success(null))
                }
            }
        }
    }


    suspend fun write(uuid_service: String, uuid_write: String, bytes: ByteArray): BleResult {
        return suspendCancellableCoroutine {
            if (it.isActive) {
                if (mDevice == null || internalStatus != ConnectionStep.CONNECTED) {
                    it.resume(Failed(IllegalArgumentException("未连接")))
                }
            }
            val callback = object : BleWriteCallback() {
                override fun onWriteSuccess(current: Int, total: Int, justWrite: ByteArray?) {
                    if (it.isActive) {
                        it.resume(Success(justWrite ?: ByteArray(0)))
                    }
                }

                override fun onWriteFailure(exception: BleException?) {
                    if (it.isActive) {
                        it.resume(exception.toFailed())
                    }
                }
            }
            if (it.isActive) {
                BleManager.getInstance().write(mDevice, uuid_service, uuid_write, bytes, callback)
            }
        }
    }

    suspend fun writeSplit(
        uuid_service: String,
        uuid_write: String,
        bytes: ByteArray
    ): BleResult {
        return suspendCancellableCoroutine {
            if (it.isActive) {
                if (mDevice == null || internalStatus != ConnectionStep.CONNECTED) {
                    it.resume(Failed(IllegalArgumentException("未连接")))
                }
            }
            val callback = object : BleWriteCallback() {
                override fun onWriteSuccess(current: Int, total: Int, justWrite: ByteArray?) {
                    if (it.isActive && current == total) {
                        it.resume(Success(justWrite ?: ByteArray(0)))
                    }
                }

                override fun onWriteFailure(exception: BleException?) {
                    if (it.isActive) {
                        it.resume(exception.toFailed())
                    }
                }
            }
            if (it.isActive) {
                BleManager.getInstance()
                    .write(mDevice, uuid_service, uuid_write, bytes, true, callback)
            }
        }
    }

    suspend fun read(uuid_service: String, uuid_read: String): BleResult {
        return suspendCancellableCoroutine {
            if (it.isActive) {
                if (mDevice == null || internalStatus != ConnectionStep.CONNECTED) {
                    it.resume(Failed(IllegalArgumentException("未连接")))
                }
            }
            val callback = object : BleReadCallback() {

                override fun onReadSuccess(status: ByteArray?) {
                    if (it.isActive) {
                        it.resume(Success(status ?: ByteArray(0)))
                    }
                }

                override fun onReadFailure(exception: BleException?) {
                    if (it.isActive) {
                        it.resume(exception.toFailed())
                    }
                }
            }
            if (it.isActive) {
                BleManager.getInstance().read(mDevice, uuid_service, uuid_read, callback)
            }
        }
    }

    //疑问：当return后callback还有效吗
    suspend fun setNotification(
        uuid_service: String,
        uuid_notify: String,
        useCharacteristicDescriptor: Boolean = true,
        onReceive: OnReceived
    ): BleResult {
        return suspendCancellableCoroutine {
            if (it.isActive) {
                if (mDevice == null || internalStatus != ConnectionStep.CONNECTED) {
                    it.resume(Failed(IllegalArgumentException("未连接")))
                }
            }
            val callback = object : BleNotifyCallback() {
                override fun onCharacteristicChanged(data: ByteArray?) {
                    data?.let { bytes ->
                        onReceive.invoke(bytes)
                    }
                }
                override fun onNotifyFailure(exception: BleException?) {
                    it.takeIf { it.isActive }?.apply { it.resume(exception.toFailed()) }
                }
                override fun onNotifySuccess() {
                    it.takeIf { it.isActive }?.apply { it.resume(Success(null)) }
                }
            }
            if (it.isActive) {
                BleManager.getInstance().notify(
                    mDevice,
                    uuid_service,
                    uuid_notify,
                    useCharacteristicDescriptor,
                    callback
                )
            }
        }
    }


    //某些设备如果不按照SIG规范，writeDescriptor会导致回掉异常,需要设置useCharacteristicDescriptor=false
    @ExperimentalCoroutinesApi
    fun setupNotification(
        uuid_service: String,
        uuid_notify: String,
        useCharacteristicDescriptor: Boolean = true
    ): Flow<BleResult> {
        return callbackFlow {
            if (mDevice == null || internalStatus != ConnectionStep.CONNECTED) {
                if (isActive) {
                    offer(Failed(IllegalArgumentException("未连接")))
                }
            }
            val callback = object : BleNotifyCallback() {
                override fun onCharacteristicChanged(bytes: ByteArray?) {
                    if (isActive) {
                        offer(Success(bytes ?: ByteArray(0)))
                    }
                }

                override fun onNotifyFailure(exception: BleException?) {
                    if (isActive) {
                        offer(exception.toFailed())
                    }
                }

                override fun onNotifySuccess() {
                    if (isActive) {
                        offer(Success(null))
                    }
                }

            }
            if (isActive) {
                BleManager.getInstance().notify(
                    mDevice,
                    uuid_service,
                    uuid_notify,
                    useCharacteristicDescriptor,
                    callback
                )
            }
            awaitClose { Log.e("BleConnection", "notify callbackFlow awaitClose") }
        }
    }

    @ExperimentalCoroutinesApi
    fun setupIndicate(uuid_service: String, uuid_indicate: String): Flow<BleResult> {
        return callbackFlow {
            if (mDevice == null || internalStatus != ConnectionStep.CONNECTED) {
                if (isActive) {
                    offer(Failed(IllegalArgumentException("未连接")))
                }
            }
            val callback = object : BleIndicateCallback() {
                override fun onCharacteristicChanged(bytes: ByteArray?) {
                    if (isActive) {
                        offer(Success(bytes ?: ByteArray(0)))
                    }
                }

                override fun onIndicateSuccess() {
                    if (isActive) {
                        offer(Success(null))
                    }
                }

                override fun onIndicateFailure(exception: BleException?) {
                    if (isActive) {
                        offer(exception.toFailed())
                    }
                }


            }
            if (isActive) {
                BleManager.getInstance().indicate(mDevice, uuid_service, uuid_indicate, callback)
            }
            awaitClose { Log.e("BleConnection", "indicate callbackFlow awaitClose") }
        }
    }

    suspend fun setMtu(size: Int): Int {
        return suspendCancellableCoroutine {
            if (it.isActive) {
                if (mDevice == null || internalStatus != ConnectionStep.CONNECTED) {
                    it.resume(23)
                }
            }
            val callback = object : BleMtuChangedCallback() {
                override fun onMtuChanged(mtu: Int) {
                    if (it.isActive) {
                        it.resume(mtu)
                    }
                }

                override fun onSetMTUFailure(exception: BleException?) {
                    if (it.isActive) {
                        it.resume(23)
                    }
                }
            }
            if (it.isActive) {
                BleManager.getInstance().setMtu(mDevice, size, callback)
            }
        }
    }

    /**
     * desc 体现蓝牙操作的响应速度
     *
     * @param connectionPriority Request a specific connection priority. Must be one of
     *                           {@link BluetoothGatt#CONNECTION_PRIORITY_BALANCED},
     *                           {@link BluetoothGatt#CONNECTION_PRIORITY_HIGH}
     *                           or {@link BluetoothGatt#CONNECTION_PRIORITY_LOW_POWER}.
     * default：{@link BluetoothGatt#CONNECTION_PRIORITY_BALANCED}
     */

    /**
     * desc 体现蓝牙操作的响应速度
     *
     * @param connectionPriority Request a specific connection priority. Must be one of
     *                           {@link BluetoothGatt#CONNECTION_PRIORITY_BALANCED},
     *                           {@link BluetoothGatt#CONNECTION_PRIORITY_HIGH}
     *                           or {@link BluetoothGatt#CONNECTION_PRIORITY_LOW_POWER}.
     * default：{@link BluetoothGatt#CONNECTION_PRIORITY_BALANCED}
     */
    /**
     * desc 体现蓝牙操作的响应速度
     *
     * @param connectionPriority Request a specific connection priority. Must be one of
     *                           {@link BluetoothGatt#CONNECTION_PRIORITY_BALANCED},
     *                           {@link BluetoothGatt#CONNECTION_PRIORITY_HIGH}
     *                           or {@link BluetoothGatt#CONNECTION_PRIORITY_LOW_POWER}.
     * default：{@link BluetoothGatt#CONNECTION_PRIORITY_BALANCED}
     */
    /**
     * desc 体现蓝牙操作的响应速度
     *
     * @param connectionPriority Request a specific connection priority. Must be one of
     *                           {@link BluetoothGatt#CONNECTION_PRIORITY_BALANCED},
     *                           {@link BluetoothGatt#CONNECTION_PRIORITY_HIGH}
     *                           or {@link BluetoothGatt#CONNECTION_PRIORITY_LOW_POWER}.
     * default：{@link BluetoothGatt#CONNECTION_PRIORITY_BALANCED}
     */
    fun requestConnectParam(@IntRange(from = 0, to = 2) connectionPriority: Int) {
        BleManager.getInstance().requestConnectionPriority(mDevice, connectionPriority)
    }

    /**
     * 读一次回一次
     */
    suspend fun readRssi(): Int {
        return suspendCancellableCoroutine {
            if (it.isActive) {
                if (mDevice == null || internalStatus != ConnectionStep.CONNECTED) {
                    it.resume(-127)
                }
            }
            val callback = object : BleRssiCallback() {
                override fun onRssiFailure(exception: BleException?) {
                    if (it.isActive) {
                        it.resume(-127)
                    }
                }

                override fun onRssiSuccess(rssi: Int) {
                    if (it.isActive) {
                        it.resume(rssi)
                    }
                }
            }
            if (it.isActive) {
                BleManager.getInstance().readRssi(mDevice, callback)
            }
        }
    }

    fun disconnect() {
        mDevice?.run {
            BleManager.getInstance().disconnect(this)
        }
    }

    fun getServices(): List<BluetoothGattService> {
        return if (mDevice != null && internalStatus == ConnectionStep.CONNECTED) {
            BleManager.getInstance().getBluetoothGattServices(mDevice)
        } else {
            emptyList()
        }
    }

    fun BleException?.toFailed(): Failed {
        val code = this?.code ?: -1
        val errorMsg = this?.description ?: "Unknown error"
        return Failed(Exception("code:$code; error:$errorMsg"))
    }

}