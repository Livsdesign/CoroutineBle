package com.livsdesign.coroutineble.connect

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.util.Log
import androidx.annotation.IntRange
import com.clj.fastble.BleManager
import com.clj.fastble.callback.*
import com.clj.fastble.data.BleDevice
import com.clj.fastble.exception.BleException
import com.livsdesign.coroutineble.connect.model.BleResult
import com.livsdesign.coroutineble.connect.model.ConnectionStatus
import com.livsdesign.coroutineble.connect.model.ConnectionStep
import com.livsdesign.coroutineble.toHexString
import com.livsdesign.coroutineble.toUUID
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlin.coroutines.resume


@ExperimentalCoroutinesApi
class BleConnection internal constructor() {

    val mStatus = ConnectionStatus()
    private var mDevice: BleDevice? = null
    private var mGatt: BluetoothGatt? = null

    suspend fun connect(mac: String): BleResult {
        return suspendCancellableCoroutine {
            val callback = object : BleGattCallback() {
                override fun onStartConnect() {
                    mStatus.current = ConnectionStep.CONNECTING
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
                    mStatus.current = if (isActiveDisConnected) {
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
                    mStatus.current = ConnectionStep.CONNECTED
                    if (it.isActive) {
                        it.resume(BleResult(true, null, "${bleDevice?.mac}:连接成功"))
                    }
                }

                override fun onConnectFail(bleDevice: BleDevice?, exception: BleException?) {
                    mGatt = null
                    mStatus.current = ConnectionStep.FAILED
                    mDevice = bleDevice
                    if (it.isActive) {
                        it.resume(
                            BleResult(
                                false,
                                null,
                                "${bleDevice?.mac}=> ${exception?.description}"
                            )
                        )
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
                    it.resume(BleResult(true, null, "${connectedDevice.mac}:连接成功"))
                }
            }
        }
    }


    suspend fun write(uuid_service: String, uuid_write: String, bytes: ByteArray): BleResult {
        return suspendCancellableCoroutine {
            if (mDevice == null || mStatus.current != ConnectionStep.CONNECTED) {
                it.resume(BleResult(false, null, "未连接"))
            }
            val callback = object : BleWriteCallback() {
                override fun onWriteSuccess(current: Int, total: Int, justWrite: ByteArray?) {
                    it.resume(
                        BleResult(
                            true,
                            justWrite ?: ByteArray(0),
                            "onWriteSuccess : ${bytes.toHexString()}"
                        )
                    )
                }

                override fun onWriteFailure(exception: BleException?) {
                    it.resume(
                        BleResult(
                            false,
                            null,
                            "onWriteFailure : ${exception?.description}"
                        )
                    )
                }
            }
            if (it.isActive) {
                BleManager.getInstance().write(mDevice, uuid_service, uuid_write, bytes, callback)
            }
        }
    }

    fun writeSplit(
        uuid_service: String,
        uuid_write: String,
        bytes: ByteArray,
        callback: SplitCallBack
    ) {
        BleManager.getInstance()
            .write(mDevice, uuid_service, uuid_write, bytes, true, object : BleWriteCallback() {
                override fun onWriteSuccess(current: Int, total: Int, justWrite: ByteArray?) {
                    callback.onWrite(current, total, justWrite)
                }

                override fun onWriteFailure(exception: BleException?) {
                    callback.onFail(exception)
                }

            })
    }

    interface SplitCallBack {
        fun onWrite(current: Int, total: Int, justWrite: ByteArray?)
        fun onFail(exception: BleException?)
    }

    suspend fun read(uuid_service: String, uuid_read: String): BleResult {
        return suspendCancellableCoroutine {
            if (mDevice == null || mStatus.current != ConnectionStep.CONNECTED) {
//                it.resumeWithException(Throwable("未连接"))
                it.resume(BleResult(false, null, "未连接"))
            }
            val callback = object : BleReadCallback() {

                override fun onReadSuccess(status: ByteArray?) {
                    if (it.isActive) {
                        it.resume(
                            BleResult(
                                true,
                                status ?: ByteArray(0),
                                "onReadSuccess : ${status?.toHexString()}"
                            )
                        )
                    }
                }

                override fun onReadFailure(exception: BleException?) {
                    if (it.isActive) {
                        it.resume(
                            BleResult(
                                false,
                                null,
                                "onReadFailure : ${exception?.description}"
                            )
                        )
                    }
                }
            }
            if (it.isActive) {
                BleManager.getInstance().read(mDevice, uuid_service, uuid_read, callback)
            }
        }
    }

    fun setupNotification(uuid_service: String, uuid_notify: String): Flow<BleResult> {
        return callbackFlow {
            if (mDevice == null || mStatus.current != ConnectionStep.CONNECTED) {
                if (isActive) {
                    offer(BleResult(false, null, "未连接"))
                }
            }
            val callback = object : BleNotifyCallback() {
                override fun onCharacteristicChanged(bytes: ByteArray?) {
                    if (isActive) {
                        offer(BleResult(true, bytes ?: ByteArray(0), null))
                    }
                }

                override fun onNotifyFailure(exception: BleException?) {
                    if (isActive) {
                        offer(BleResult(false, null, exception?.description))
                    }
                }

                override fun onNotifySuccess() {
                    if (isActive) {
                        offer(BleResult(true, null, "success"))
                    }
                }

            }
            if (isActive) {
                BleManager.getInstance().notify(mDevice, uuid_service, uuid_notify, callback)
            }
            awaitClose { Log.e("BleConnection", "notify callbackFlow awaitClose") }
        }
    }


    fun setupIndicate(uuid_service: String, uuid_indicate: String): Flow<BleResult> {
        return callbackFlow {
            if (mDevice == null || mStatus.current != ConnectionStep.CONNECTED) {
                if (isActive) {
                    offer(BleResult(false, null, "未连接"))
                }
            }
            val callback = object : BleIndicateCallback() {
                override fun onCharacteristicChanged(bytes: ByteArray?) {
                    if (isActive) {
                        offer(BleResult(true, bytes ?: ByteArray(0), null))
                    }
                }

                override fun onIndicateSuccess() {
                    if (isActive) {
                        offer(BleResult(true, null, "success"))
                    }
                }

                override fun onIndicateFailure(exception: BleException?) {
                    if (isActive) {
                        offer(BleResult(false, null, exception?.description))
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
            if (mDevice == null || mStatus.current != ConnectionStep.CONNECTED) {
                it.resume(23)
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


}