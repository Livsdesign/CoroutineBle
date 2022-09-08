package com.livsdesign.coroutineble.usecase

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

typealias RequestMtuCallback = (
    gatt: BluetoothGatt,
    mtu: Int,
    status: Int
) -> Unit

class RequestMtuUseCase {

    private var callback: RequestMtuCallback? = null
    private var cancellableContinuation: CancellableContinuation<Int>? = null

    fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
        callback?.invoke(gatt, mtu, status)
    }

    private fun setCallback(cb: RequestMtuCallback) {
        this.callback = cb
    }

    fun isActive(): Boolean {
        return cancellableContinuation?.isActive == true
    }

    @SuppressLint("MissingPermission")
    suspend fun execute(
        gatt: BluetoothGatt,
        size: Int
    ) = suspendCancellableCoroutine<Int> {
        cancellableContinuation = it
        setCallback { _, mtuSize, status ->
            this.callback = null
            if (it.isActive) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    it.resume(mtuSize)
                } else {
                    it.resumeWithException(Throwable("Request MTU Size Failed, gatt status: $status"))
                }
            }
        }
        if (it.isActive && !gatt.requestMtu(size)) {
            this.callback = null
            it.resumeWithException(Throwable("Request MTU Size Failed!"))
        }
    }
}