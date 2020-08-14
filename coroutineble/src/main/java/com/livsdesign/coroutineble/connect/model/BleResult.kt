package com.livsdesign.coroutineble.connect.model


sealed class BleResult(val state: Boolean) {
    class Success(val value: ByteArray?) : BleResult(true)
    class Failed(val exception: Exception) : BleResult(false)
}
