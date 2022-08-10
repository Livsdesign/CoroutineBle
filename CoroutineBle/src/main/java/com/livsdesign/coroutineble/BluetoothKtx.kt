package com.livsdesign.coroutineble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

fun BluetoothGattCharacteristic.hasProperty(property: Int): Boolean {
    return this.properties and property > 0
}

fun BluetoothGattCharacteristic.hasProperty(properties: List<Int>): Boolean {
    return properties.all {
        this.properties and it > 0
    }
}

fun Context.checkLocation(): Boolean {
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

const val FLAG_BLUETOOTH_TOGGLE = 0b00000001
const val FLAG_BLUETOOTH_SCAN = 0b00000010
const val FLAG_BLUETOOTH_CONNECT = 0b00000100
const val FLAG_BLUETOOTH_ADVERTISER = 0b00001000
const val FLAG_LOCATION_TOGGLE = 0b00010000
const val FLAG_LOCATION_PERMISSION = 0b00100000

/**
 * ScanResult中包含BluetoothDevice
 * BluetoothDevice.getName() 需要BLUETOOTH_CONNECT权限
 */
const val STATE_READY_SCAN = 0b00110011
const val STATE_READY_CONNECT = 0b00000101
const val STATE_READY_SCAN_CONNECT = 0b00110111
const val STATE_READY_BLUETOOTH_ALL = 0b00111111

fun Context.checkBluetoothState(): Int {
    val manager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?
    val bluetoothToggle = manager?.adapter?.isEnabled ?: false
    val locationToggle = checkLocation()
    val locationPermission = ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    val scan: Boolean
    val connect: Boolean
    val advertiser: Boolean
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        scan = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.BLUETOOTH_SCAN
        ) == PackageManager.PERMISSION_GRANTED
        connect = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
        advertiser = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.BLUETOOTH_ADVERTISE
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        scan = true
        connect = true
        advertiser = true
    }
    var state = 0
    if (bluetoothToggle) {
        state = state or FLAG_BLUETOOTH_TOGGLE
    }
    if (locationToggle) {
        state = state or FLAG_LOCATION_TOGGLE
    }
    if (locationPermission) {
        state = state or FLAG_LOCATION_PERMISSION
    }
    if (scan) {
        state = state or FLAG_BLUETOOTH_SCAN
    }
    if (connect) {
        state = state or FLAG_BLUETOOTH_CONNECT
    }
    if (advertiser) {
        state = state or FLAG_BLUETOOTH_ADVERTISER
    }
    return state
}

fun Context.connectReady(): Boolean {
    return checkBluetoothState() and STATE_READY_CONNECT > 0
}

fun Context.scanReady(): Boolean {
    return checkBluetoothState() and STATE_READY_SCAN > 0
}

/**
 * @param autoConnect 自动连接
 * @param phy 大于Android 0有效
 * @see BluetoothDevice.PHY_LE_1M_MASK
 * @see BluetoothDevice.PHY_LE_2M_MASK
 * @see BluetoothDevice.PHY_LE_CODED_MASK
 * @param handler 大于Android 0有效，主要目的是让蓝牙在指定线程运行，如果是null则新建一个子线程运行
 */
@SuppressLint("MissingPermission")
fun Context.connectBleDevice(
    device: BluetoothDevice,
    callback: BluetoothGattCallback,
    autoConnect: Boolean = false,
    phy: Int = 1,
    handler: Handler? = null
): BluetoothGatt? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        device.connectGatt(
            this,
            autoConnect,
            callback,
            BluetoothDevice.TRANSPORT_LE, phy, handler
        )
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        device.connectGatt(
            this,
            autoConnect,
            callback,
            BluetoothDevice.TRANSPORT_LE
        )
    } else {
        device.connectGatt(this, autoConnect, callback)
    }
}