package com.livsdesign.coroutineble.model


sealed class BaseResult<T>(val state: Boolean) {
    class Success<T>(val value: T) : BaseResult<T>(true)
    class Failed<T>(val exception: Exception) : BaseResult<T>(false)
}

