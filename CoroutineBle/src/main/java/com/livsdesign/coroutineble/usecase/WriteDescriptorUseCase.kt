package com.livsdesign.coroutineble.usecase

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattDescriptor
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

typealias WriteDescriptorCallback = (
    gatt: BluetoothGatt,
    descriptor: BluetoothGattDescriptor,
    status: Int
) -> Unit

class WriteDescriptorUseCase {

    private var callback: WriteDescriptorCallback? = null
    private var cancellableContinuation: CancellableContinuation<Boolean>? = null

    fun onDescriptorWrite(
        gatt: BluetoothGatt,
        descriptor: BluetoothGattDescriptor,
        status: Int
    ) {
        callback?.invoke(gatt, descriptor, status)
    }

    private fun setCallback(cb: WriteDescriptorCallback) {
        this.callback = cb
    }

    fun isActive(): Boolean {
        return cancellableContinuation?.isActive == true
    }

    @SuppressLint("MissingPermission")
    suspend fun execute(
        gatt: BluetoothGatt,
        descriptor: BluetoothGattDescriptor,
        value: ByteArray
    ) = suspendCancellableCoroutine<Boolean> {
        cancellableContinuation = it
        setCallback { _, d, status ->
            if (d.uuid.equals(descriptor.uuid)) {
                this.callback = null
                if (it.isActive) it.resume(status == BluetoothGatt.GATT_SUCCESS)
            }
        }
        descriptor.value = value
        if (it.isActive && !gatt.writeDescriptor(descriptor)) {
            this.callback = null
            it.resume(false)
        }
    }
}