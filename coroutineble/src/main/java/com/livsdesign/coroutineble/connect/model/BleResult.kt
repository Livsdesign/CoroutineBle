package com.livsdesign.coroutineble.connect.model

import java.lang.Exception

//class BleResult(
//    val state: Boolean,
//    val result: ByteArray?,
//    val msg: String?
//){
//    override fun toString(): String {
//        return "BleResult(state=$state, result=${result?.contentToString()}, msg=$msg)"
//    }
//}

sealed class BleResult(val state: Boolean)
class Success(val bytes: ByteArray?) : BleResult(true)
class Failed(val exception: Exception) : BleResult(false)