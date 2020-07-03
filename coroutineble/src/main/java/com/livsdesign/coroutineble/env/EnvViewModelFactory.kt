package com.livsdesign.coroutineble.env

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class EnvViewModelFactory(val ctx: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
        if (modelClass != EnvViewModel::class.java) {
            throw IllegalArgumentException("Unknown ViewModel class")
        }
        return EnvViewModel(ctx) as T
    }

}