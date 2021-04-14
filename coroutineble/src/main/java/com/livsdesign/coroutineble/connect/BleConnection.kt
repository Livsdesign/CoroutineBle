package com.livsdesign.coroutineble.connect

import android.bluetooth.*
import android.content.Context
import android.os.Build
import androidx.annotation.IntRange
import androidx.annotation.RequiresApi
import com.clj.fastble.BleManager
import com.clj.fastble.callback.*
import com.clj.fastble.data.BleDevice
import com.clj.fastble.exception.BleException
import com.livsdesign.coroutineble.model.BaseResult
import com.livsdesign.coroutineble.model.ConnectionStatus
import com.livsdesign.coroutineble.model.ConnectionStep
import com.livsdesign.coroutineble.model.PhyInfo
import kotlinx.coroutines.*
import kotlin.coroutines.resume

typealias ConnectionStepChanged = (newStatus: ConnectionStep) -> Unit
typealias OnReceived = (received: ByteArray) -> Unit

class BleConnection {

    /**
     * LiveData是很香，但需要关联LifecycleOwner，如果没有订阅也不会执行
     */
    val mStatus = ConnectionStatus()
    private var mDevice: BleDevice? = null

    suspend fun connect(mac: String, onChanged: ConnectionStepChanged): BaseResult<String> {
        return suspendCancellableCoroutine {
            val callback = object : BleGattCallback() {
                override fun onStartConnect() {
                    mStatus.current = ConnectionStep.CONNECTING
                    onChanged.invoke(ConnectionStep.CONNECTING)
                }

                override fun onDisConnected(
                    isActiveDisConnected: Boolean,
                    device: BleDevice?,
                    gatt: BluetoothGatt?,
                    status: Int
                ) {
                    gatt?.close()
                    mDevice = null
                    val step = if (isActiveDisConnected) {
                        ConnectionStep.DISCONNECTED
                    } else {
                        ConnectionStep.LOST
                    }
                    mStatus.current = step
                    onChanged.invoke(step)
                }

                override fun onConnectSuccess(
                    bleDevice: BleDevice?,
                    gatt: BluetoothGatt?,
                    status: Int
                ) {
                    mDevice = bleDevice
                    mStatus.current = ConnectionStep.CONNECTED
                    onChanged.invoke(ConnectionStep.CONNECTED)
                    if (it.isActive) {
                        it.resume(BaseResult.Success(mac))
                    }
                }

                override fun onConnectFail(bleDevice: BleDevice?, exception: BleException?) {
                    mStatus.current = ConnectionStep.FAILED
                    onChanged.invoke(ConnectionStep.FAILED)
                    mDevice = null
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
                    mStatus.current = ConnectionStep.CONNECTED
                    onChanged.invoke(ConnectionStep.CONNECTED)
                    it.resume(BaseResult.Success(mac))
                }
            }
        }
    }


    suspend fun write(
        uuid_service: String,
        uuid_write: String,
        bytes: ByteArray
    ): BaseResult<ByteArray> {
        return suspendCancellableCoroutine {
            if (it.isActive) {
                if (mDevice == null || mStatus.current != ConnectionStep.CONNECTED) {
                    it.resume(BaseResult.Failed(IllegalArgumentException("未连接")))
                }
            }
            val callback = object : BleWriteCallback() {
                override fun onWriteSuccess(current: Int, total: Int, justWrite: ByteArray?) {
                    if (it.isActive) {
                        it.resume(BaseResult.Success(justWrite ?: ByteArray(0)))
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
    ): BaseResult<ByteArray> {
        return suspendCancellableCoroutine {
            if (it.isActive) {
                if (mDevice == null || mStatus.current != ConnectionStep.CONNECTED) {
                    it.resume(BaseResult.Failed(IllegalArgumentException("未连接")))
                }
            }
            val callback = object : BleWriteCallback() {
                override fun onWriteSuccess(current: Int, total: Int, justWrite: ByteArray?) {
                    if (it.isActive && current == total) {
                        it.resume(BaseResult.Success(justWrite ?: ByteArray(0)))
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

    suspend fun read(uuid_service: String, uuid_read: String): BaseResult<ByteArray> {
        return suspendCancellableCoroutine {
            if (it.isActive) {
                if (mDevice == null || mStatus.current != ConnectionStep.CONNECTED) {
                    it.resume(BaseResult.Failed(IllegalArgumentException("未连接")))
                }
            }
            val callback = object : BleReadCallback() {

                override fun onReadSuccess(status: ByteArray?) {
                    if (it.isActive) {
                        it.resume(BaseResult.Success(status ?: ByteArray(0)))
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
    ): BaseResult<Boolean> {
        return suspendCancellableCoroutine {
            if (it.isActive) {
                if (mDevice == null || mStatus.current != ConnectionStep.CONNECTED) {
                    it.resume(BaseResult.Failed(IllegalArgumentException("未连接")))
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
                    it.takeIf { it.isActive }?.apply { it.resume(BaseResult.Success(true)) }
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

    fun stopNotification(
        uuid_service: String,
        uuid_notify: String
    ): Boolean {
        return BleManager.getInstance().stopNotify(mDevice, uuid_service, uuid_notify)
    }

    suspend fun setupIndicate(
        uuid_service: String,
        uuid_notify: String,
        useCharacteristicDescriptor: Boolean = true,
        onReceive: OnReceived
    ): BaseResult<Boolean> {
        return suspendCancellableCoroutine {
            if (it.isActive) {
                if (mDevice == null || mStatus.current != ConnectionStep.CONNECTED) {
                    it.resume(BaseResult.Failed(IllegalArgumentException("未连接")))
                }
            }
            val callback = object : BleIndicateCallback() {
                override fun onCharacteristicChanged(data: ByteArray?) {
                    data?.let { bytes ->
                        onReceive.invoke(bytes)
                    }
                }

                override fun onIndicateFailure(exception: BleException?) {
                    it.takeIf { it.isActive }?.apply { it.resume(exception.toFailed()) }
                }

                override fun onIndicateSuccess() {
                    it.takeIf { it.isActive }?.apply { it.resume(BaseResult.Success(true)) }
                }
            }
            if (it.isActive) {
                BleManager.getInstance().indicate(
                    mDevice,
                    uuid_service,
                    uuid_notify,
                    useCharacteristicDescriptor,
                    callback
                )
            }
        }
    }

    fun stopIndicate(
        uuid_service: String,
        uuid_indicate: String
    ): Boolean {
        return BleManager.getInstance().stopIndicate(mDevice, uuid_service, uuid_indicate)
    }

    suspend fun setMtu(size: Int): BaseResult<Int> {
        return suspendCancellableCoroutine {
            if (it.isActive) {
                if (mDevice == null || mStatus.current != ConnectionStep.CONNECTED) {
                    it.resume(BaseResult.Failed(IllegalArgumentException("未连接")))
                }
            }
            val callback = object : BleMtuChangedCallback() {
                override fun onMtuChanged(mtu: Int) {
                    if (it.isActive) {
                        it.resume(BaseResult.Success(mtu))
                    }
                }

                override fun onSetMTUFailure(exception: BleException?) {
                    if (it.isActive) {
                        it.resume(exception.toFailed())
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
     *                           High:(11.25~15ms,0,20s)
     *                           Balanced:(30~50ms,0,20s)
     *                           High:(100~125ms,2,20s)
     * default：{@link BluetoothGatt#CONNECTION_PRIORITY_BALANCED}
     */
    fun requestConnectParam(@IntRange(from = 0, to = 2) connectionPriority: Int): Boolean {
        return if (mDevice != null && mStatus.current == ConnectionStep.CONNECTED) {
            BleManager.getInstance().requestConnectionPriority(mDevice, connectionPriority)
        } else {
            false
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun readPhy(context: Context): BaseResult<PhyInfo> {
        return suspendCancellableCoroutine {
            if (it.isActive) {
                if (mDevice == null || mStatus.current != ConnectionStep.CONNECTED) {
                    it.resume(BaseResult.Failed(IllegalArgumentException("未连接")))
                }
            }
            BleManager.getInstance().bluetoothManager.openGattServer(context,
                object : BluetoothGattServerCallback() {
                    override fun onPhyRead(d: BluetoothDevice?, tx: Int, rx: Int, status: Int) {
                        super.onPhyRead(d, tx, rx, status)
                        val info = PhyInfo(d?.address ?: mDevice?.mac ?: "", tx, rx, status)
                        if (it.isActive) {
                            it.resume(BaseResult.Success(info))
                        }
                    }
                })
            if (it.isActive) {
                BleManager.getInstance().getBluetoothGatt(mDevice).readPhy()
            }
        }
    }


    /**
     * 读一次回一次
     */
    suspend fun readRssi(): BaseResult<Int> {
        return suspendCancellableCoroutine {
            if (it.isActive) {
                if (mDevice == null || mStatus.current != ConnectionStep.CONNECTED) {
                    it.resume(BaseResult.Failed(IllegalArgumentException("未连接")))
                }
            }
            val callback = object : BleRssiCallback() {
                override fun onRssiFailure(exception: BleException?) {
                    if (it.isActive) {
                        it.resume(exception.toFailed())
                    }
                }

                override fun onRssiSuccess(rssi: Int) {
                    if (it.isActive) {
                        it.resume(BaseResult.Success(rssi))
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
        return if (mDevice != null && mStatus.current == ConnectionStep.CONNECTED) {
            BleManager.getInstance().getBluetoothGattServices(mDevice)
        } else {
            emptyList()
        }
    }

    fun <T> BleException?.toFailed(): BaseResult.Failed<T> {
        val code = this?.code ?: -1
        val errorMsg = this?.description ?: "Unknown error"
        return BaseResult.Failed(Exception("code:$code; error:$errorMsg"))
    }

    companion object {

        fun getAllConnectedDevices(): List<String> {
            return BleManager.getInstance().allConnectedDevice.map {
                it.mac
            }
        }

    }

    /**
     * 截至与2020年8月
     * 1. Flow仍是一个实验性的类，使用方法在更新后，接口更改的可能性很大，
     * 2. 且在使用FLow后，需要新建立launch才可以继续使用协程或普通方法，否则后面代码将不执行
     * 3. 需要标注@ExperimentalCoroutinesApi，否则android studio会警告，如果使用Flow需要大量标注此注解，很烦。
     * 以上几点原因，暂时放弃setupNotification（）和setupIndicate（）
     * 请使用以下两个方法
     * @see setNotification
     */

    //某些设备如果不按照SIG规范，writeDescriptor会导致回掉异常,需要设置useCharacteristicDescriptor=false
    /*@ExperimentalCoroutinesApi
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
    }*/

}