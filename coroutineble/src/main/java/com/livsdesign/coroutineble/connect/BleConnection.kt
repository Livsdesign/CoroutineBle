package com.livsdesign.coroutineble.connect

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattService
import androidx.annotation.IntRange
import com.clj.fastble.BleManager
import com.clj.fastble.callback.*
import com.clj.fastble.data.BleDevice
import com.clj.fastble.exception.BleException
import com.livsdesign.coroutineble.toHexString
import com.livsdesign.coroutineble.connect.model.BleResult
import com.livsdesign.coroutineble.connect.model.ConnectionData
import com.livsdesign.coroutineble.connect.model.ConnectionState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class BleConnection internal constructor() {

    val data = ConnectionData()//liveData
    private var mDevice: BleDevice? = null

    suspend fun connect(mac: String): BleResult {
        data.mac = mac
        return suspendCancellableCoroutine {
            val callback = object : BleGattCallback() {
                override fun onStartConnect() {
                    data.state = ConnectionState.CONNECTING
                }

                override fun onDisConnected(
                    isActiveDisConnected: Boolean,
                    device: BleDevice?,
                    gatt: BluetoothGatt?,
                    status: Int
                ) {
                    gatt?.close()
                    mDevice = device
                    data.state = if (isActiveDisConnected) {
                        ConnectionState.DISCONNECTED
                    } else {
                        ConnectionState.LOST
                    }
                }

                override fun onConnectSuccess(
                    bleDevice: BleDevice?,
                    gatt: BluetoothGatt?,
                    status: Int
                ) {
                    mDevice = bleDevice
                    data.state = ConnectionState.CONNECTED
                    if (it.isActive) {
                        it.resume(BleResult(true, null, "${bleDevice?.device?.address}:连接成功"))
                    }
                }

                override fun onConnectFail(bleDevice: BleDevice?, exception: BleException?) {
                    data.state = ConnectionState.FAILED
                    mDevice = bleDevice
                    if (it.isActive) {
                        it.resume(
                            BleResult(
                                false,
                                null,
                                "${bleDevice?.device?.address}=> ${exception?.description}"
                            )
                        )
                    }
                }
            }
            if (it.isActive) {
                BleManager.getInstance().connect(mac, callback)
            }
        }
    }

    suspend fun write(uuid_service: String, uuid_write: String, bytes: ByteArray): BleResult {
        return suspendCancellableCoroutine {
            if (mDevice == null || data.state != ConnectionState.CONNECTED) {
                it.resumeWithException(Throwable("未连接"))
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


    suspend fun read(uuid_service: String, uuid_read: String): BleResult {
        return suspendCancellableCoroutine {
            if (mDevice == null || data.state != ConnectionState.CONNECTED) {
                it.resumeWithException(Throwable("未连接"))
            }
            val callback = object : BleReadCallback() {

                override fun onReadSuccess(data: ByteArray?) {
                    if (it.isActive) {
                        it.resume(
                            BleResult(
                                true,
                                data ?: ByteArray(0),
                                "onReadSuccess : ${data?.toHexString()}"
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


    @ExperimentalCoroutinesApi
    suspend fun notify(uuid_service: String, uuid_notify: String): Flow<ByteArray> {
        return callbackFlow {
            if (mDevice == null || data.state != ConnectionState.CONNECTED) {
                close(Throwable("未连接"))
            }
            val callback = object : BleNotifyCallback() {
                override fun onCharacteristicChanged(data: ByteArray?) {
                    if (isActive) {
                        offer(data ?: ByteArray(0))
                    }
                }

                override fun onNotifyFailure(exception: BleException?) {
                    if (isActive) {
                        close(Throwable(exception?.description))
                    }
                }

                override fun onNotifySuccess() {
                }

            }
            if (isActive) {
                BleManager.getInstance().notify(mDevice, uuid_service, uuid_notify, callback)
            }
        }
    }

    @ExperimentalCoroutinesApi
    suspend fun indicate(uuid_service: String, uuid_indicate: String): Flow<ByteArray> {
        return callbackFlow {
            if (mDevice == null || data.state != ConnectionState.CONNECTED) {
                close(Throwable("未连接"))
            }
            val callback = object : BleIndicateCallback() {
                override fun onCharacteristicChanged(data: ByteArray?) {
                    if (isActive) {
                        offer(data ?: ByteArray(0))
                    }
                }

                override fun onIndicateSuccess() {
                }

                override fun onIndicateFailure(exception: BleException?) {
                    if (isActive) {
                        close(Throwable(exception?.description))
                    }

                }


            }
            if (isActive) {
                BleManager.getInstance().indicate(mDevice, uuid_service, uuid_indicate, callback)
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
    fun requestConnectParam(@IntRange(from = 0, to = 2) connectionPriority: Int) {
        BleManager.getInstance().requestConnectionPriority(mDevice, connectionPriority)
    }

    fun disconnect() {
        mDevice?.run {
            BleManager.getInstance().disconnect(this)
        }
    }

    fun getServices(): List<BluetoothGattService> {
        return if (mDevice != null && data.state == ConnectionState.CONNECTED) {
            BleManager.getInstance().getBluetoothGattServices(mDevice)
        } else {
            emptyList()
        }
    }


}