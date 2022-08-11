package com.livsdesign.coroutineble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.Handler
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlin.coroutines.cancellation.CancellationException

fun BluetoothGattCharacteristic.hasProperty(property: Int): Boolean {
    return this.properties and property > 0
}

fun BluetoothGattCharacteristic.hasProperty(properties: List<Int>): Boolean {
    return properties.all {
        this.properties and it > 0
    }
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

@SuppressLint("MissingPermission")
fun Context.scanPeripheral(
    scanMode: Int = ScanSettings.SCAN_MODE_LOW_LATENCY,
    reportDelay: Long = 1000
) = callbackFlow {
    val manager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?
    val bluetoothAdapter = manager?.adapter
    val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            trySend(listOf(result))
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            trySend(results)
        }

        override fun onScanFailed(errorCode: Int) {
            val exception = IllegalStateException("Scan Failed (errorCode: $errorCode)")
            cancel(CancellationException(exception.message, exception))
        }
    }
    val scanner: BluetoothLeScanner? = if (bluetoothAdapter == null) {
        val exception = IllegalStateException("Bluetooth Exception(maybe Bluetooth disable)")
        cancel(CancellationException(exception.message, exception))
        null
    } else {
        val scanner = bluetoothAdapter.bluetoothLeScanner
        val scanSettings = ScanSettings.Builder()
            .setScanMode(scanMode)
            .setReportDelay(reportDelay)
            .build()
        scanner?.startScan(null, scanSettings, scanCallback)
        scanner
    }
    awaitClose {
        scanner?.stopScan(scanCallback)
    }
}