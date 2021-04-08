package com.livsdesign.coroutineble.model

import android.bluetooth.BluetoothDevice

data class PhyInfo(
    val mac: String, val txPhy: Int, val rxPhy: Int, val status: Int
)