package com.livsdesign.coroutineble.usecase

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattDescriptor
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

typealias ReadDescriptorCallback = (
    gatt: BluetoothGatt,
    descriptor: BluetoothGattDescriptor,
    status: Int
) -> Unit

class ReadDescriptorUseCase {

    private var callback: ReadDescriptorCallback? = null
    private var cancellableContinuation: CancellableContinuation<ByteArray>? = null

    fun onDescriptorRead(
        gatt: BluetoothGatt,
        descriptor: BluetoothGattDescriptor,
        status: Int
    ) {
        callback?.invoke(gatt, descriptor, status)
    }

    private fun setCallback(cb: ReadDescriptorCallback) {
        this.callback = cb
    }

    fun isActive(): Boolean {
        return cancellableContinuation?.isActive == true
    }

    @SuppressLint("MissingPermission")
    suspend fun execute(
        gatt: BluetoothGatt,
        descriptor: BluetoothGattDescriptor,
    ) = suspendCancellableCoroutine<ByteArray> {
        cancellableContinuation = it
        setCallback { _, d, status ->
            if (d.uuid.equals(descriptor.uuid)) {
                this.callback = null
                if (it.isActive) {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        it.resume(descriptor.value)
                    } else {
                        it.resumeWithException(Throwable("Read descriptor Failed, gatt status: $status"))
                    }
                }
            }
        }
        if (it.isActive && !gatt.readDescriptor(descriptor)) {
            this.callback = null
            it.resumeWithException(Throwable("Read descriptor Failed!"))
        }
    }
}