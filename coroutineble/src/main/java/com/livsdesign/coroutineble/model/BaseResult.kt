package com.livsdesign.coroutineble.model


sealed class BaseResult(val state: Boolean) {
    class Success<T>(val value: T) : BaseResult(true)
    class Failed(val exception: Exception) : BaseResult(false)
}

