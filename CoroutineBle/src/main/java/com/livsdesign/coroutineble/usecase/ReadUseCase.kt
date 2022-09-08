package com.livsdesign.coroutineble.usecase

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

typealias ReadCallback = (
    gatt: BluetoothGatt,
    characteristic: BluetoothGattCharacteristic,
    status: Int
) -> Unit

class ReadUseCase {

    private var callback: ReadCallback? = null
    private var cancellableContinuation: CancellableContinuation<ByteArray>? = null

    fun onCharacteristicRead(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        status: Int
    ) {
        callback?.invoke(gatt, characteristic, status)
    }

    private fun setCallback(cb: ReadCallback) {
        this.callback = cb
    }

    fun isActive(): Boolean {
        return cancellableContinuation?.isActive == true
    }

    @SuppressLint("MissingPermission")
    suspend fun execute(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
    ) = suspendCancellableCoroutine<ByteArray> {
        cancellableContinuation = it
        setCallback { _, c, status ->
            if (c.uuid.equals(characteristic.uuid)) {
                if (it.isActive) {
                    this.callback = null
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        it.resume(c.value)
                    } else {
                        it.resumeWithException(Throwable("Read Failed, gatt errorCode:$status"))
                    }
                }
            }
        }
        if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_READ != BluetoothGattCharacteristic.PROPERTY_READ) {
            this.callback = null
            it.resumeWithException(IllegalArgumentException("This characteristic not support READ"))
        } else {
            if (!gatt.readCharacteristic(characteristic)) {
                this.callback = null
                if (it.isActive) it.resumeWithException(Throwable("Read Failed!"))
            }
        }
    }
}