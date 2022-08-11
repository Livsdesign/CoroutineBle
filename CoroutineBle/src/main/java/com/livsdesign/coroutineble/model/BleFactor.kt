package com.livsdesign.coroutineble.model

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow

data class BleFactor(
    var bluetoothEnable: Boolean = false,
    var bluetoothScanPermission: Boolean = false,
    var bluetoothConnectPermission: Boolean = false,
    var bluetoothAdvertisePermission: Boolean = false,
    var locationEnable: Boolean = false,
    var locationPermission: Boolean = false
) {
    fun connectReady(): Boolean {
        return bluetoothEnable && bluetoothConnectPermission
    }

    fun scanReady(): Boolean {
        return bluetoothEnable && bluetoothScanPermission && locationEnable && locationPermission
    }

    fun scanAndConnectReady(): Boolean {
        return bluetoothEnable && bluetoothScanPermission && bluetoothConnectPermission && locationEnable && locationPermission
    }
}


fun Context.bleFactorMonitor() = callbackFlow {
    val factor = BleFactor(
        bluetoothEnable = isBluetoothEnable(),
        bluetoothScanPermission = isBluetoothScanGranted(),
        bluetoothConnectPermission = isBluetoothConnectGranted(),
        bluetoothAdvertisePermission = isBluetoothAdvertiseGranted(),
        locationEnable = isLocationEnable(),
        locationPermission = isLocationPermissionGranted()
    )
    trySend(factor)
    val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action ?: return
            when (action) {
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, 0)) {
                        BluetoothAdapter.STATE_OFF -> factor.bluetoothEnable = false
                        BluetoothAdapter.STATE_ON -> factor.bluetoothEnable = true
                        else -> return
                    }
                    trySend(factor)
                }
                LocationManager.PROVIDERS_CHANGED_ACTION -> {
                    val enable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        intent.getBooleanExtra(LocationManager.EXTRA_LOCATION_ENABLED, false)
                    } else {
                        isLocationEnable()
                    }
                    if (enable != factor.locationEnable) {
                        factor.locationEnable = enable
                        trySend(factor)
                    }
                }
            }
        }
    }

    val intentFilter = IntentFilter()
    intentFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
    intentFilter.addAction(LocationManager.PROVIDERS_CHANGED_ACTION)
    registerReceiver(receiver, intentFilter)
    Log.d("bleFactorMonitor", "registerReceiver")

    awaitClose {
        Log.d("bleFactorMonitor", "unregisterReceiver")
        unregisterReceiver(receiver)
    }
}

fun Context.isBluetoothEnable(): Boolean {
    val manager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?
    return manager?.adapter?.isEnabled ?: false
}

fun Context.isBluetoothScanGranted(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.BLUETOOTH_SCAN
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
}

fun Context.isBluetoothConnectGranted(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
}

fun Context.isBluetoothAdvertiseGranted(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.BLUETOOTH_ADVERTISE
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
}

fun Context.isLocationEnable(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager?
        locationManager?.isLocationEnabled ?: false
    } else {
        try {
            val state =
                Settings.Secure.getInt(contentResolver, Settings.Secure.LOCATION_MODE)
            state != Settings.Secure.LOCATION_MODE_OFF
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}

fun Context.isLocationPermissionGranted(): Boolean {
    return ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
}