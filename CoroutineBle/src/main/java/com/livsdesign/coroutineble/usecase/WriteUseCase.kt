package com.livsdesign.coroutineble.usecase

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.util.Log
import com.livsdesign.coroutineble.WriteType
import com.livsdesign.coroutineble.hasProperty
import com.livsdesign.coroutineble.toHexString
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException


class WriteUseCase() {
    private val TAG = "WriteUseCase"

    private var callback: WriteCallback? = null
    private var cancellableContinuation: CancellableContinuation<Boolean>? = null

    fun onCharacteristicWrite(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        status: Int
    ) {
        Log.e(TAG, "onCharacteristicWrite: " )
        callback?.invoke(gatt, characteristic, status)
    }

    private fun setCallback(cb: WriteCallback) {
        this.callback = cb
    }

    fun isActive(): Boolean {
        return cancellableContinuation?.isActive == true
    }

    @SuppressLint("MissingPermission")
    suspend fun execute(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        type: WriteType,
        value: ByteArray
    ) = suspendCancellableCoroutine<Boolean> {
        cancellableContinuation = it
        setCallback { _, c, status ->
            if (c.uuid.equals(characteristic.uuid)) {
                if (it.isActive) {
                    this.callback = null
                    it.resume(status == BluetoothGatt.GATT_SUCCESS)
                }
            }
        }
        val writeProperty: Int = when (type) {
            WriteType.WithResponse -> BluetoothGattCharacteristic.PROPERTY_WRITE
            WriteType.WithoutResponse -> BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE
            WriteType.Signed -> BluetoothGattCharacteristic.PROPERTY_SIGNED_WRITE
        }
        if (!characteristic.hasProperty(writeProperty)) {
            this.callback = null
            it.resumeWithException(IllegalArgumentException("This characteristic not support ${type.name}"))
        } else {
            characteristic.writeType = type.value
            characteristic.value = value

            if (!gatt.writeCharacteristic(characteristic)) {
                Log.e(TAG, "false: ${value.toHexString(',')}")
                this.callback = null
                if (it.isActive) it.resume(false)
            }else{
                Log.e(TAG, "true: ${value.toHexString(',')}")
            }
        }
    }
}