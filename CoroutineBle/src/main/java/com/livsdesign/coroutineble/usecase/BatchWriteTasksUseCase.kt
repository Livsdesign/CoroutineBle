package com.livsdesign.coroutineble.usecase

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.util.Log
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

typealias WriteCallback = (
    gatt: BluetoothGatt,
    characteristic: BluetoothGattCharacteristic,
    status: Int
) -> Unit

@SuppressLint("MissingPermission")
class BatchWriteTasksUseCase() {

    private var callback: WriteCallback? = null
    private var cancellableContinuation: CancellableContinuation<Boolean>? = null

    fun onCharacteristicWrite(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        status: Int
    ) {
        callback?.invoke(gatt, characteristic, status)
    }

    private fun setCallback(cb: WriteCallback) {
        this.callback = cb
    }

    fun isActive(): Boolean {
        return cancellableContinuation?.isActive == true
    }

    //todo timeout 断连后请求
    suspend fun execute(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        tasks: List<ByteArray>
    ) = suspendCancellableCoroutine<Boolean> {
        cancellableContinuation = it
        val pendingTasks = mutableListOf<ByteArray>()
        pendingTasks.addAll(tasks)
        setCallback { _, c, status ->
            //过滤掉其他callback
            if (!c.uuid.equals(characteristic.uuid)) return@setCallback
            if (status != BluetoothGatt.GATT_SUCCESS) {
                this.callback = null
                if (it.isActive) it.resume(false)
            } else {
                if (pendingTasks.isNotEmpty()) {
                    val task = pendingTasks.removeAt(0)
                    if (it.isActive && !write(gatt, characteristic, task)) {
                        this.callback = null
                        it.resume(false)
                    }
                } else {
                    this.callback = null
                    if (it.isActive) it.resume(true)
                }
            }
        }
        //首次
        if (it.isActive) {
            if (pendingTasks.isNotEmpty()) {
                val task = pendingTasks.removeAt(0)
                if (!write(gatt, characteristic, task)) {
                    this.callback = null
                    it.resume(false)
                }
            } else {
                it.resumeWithException(Throwable("Write Task is empty!"))
            }
        }
    }

    private fun write(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray
    ): Boolean {
        characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        characteristic.value = value
        return gatt.writeCharacteristic(characteristic)
    }
}