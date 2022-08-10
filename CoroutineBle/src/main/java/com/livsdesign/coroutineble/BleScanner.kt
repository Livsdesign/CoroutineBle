package com.livsdesign.coroutineble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlin.coroutines.cancellation.CancellationException

class BleScanner {

    @SuppressLint("MissingPermission")
    fun discover(
        context: Context,
        scanMode: Int = ScanSettings.SCAN_MODE_LOW_LATENCY,
        reportDelay: Long = 1000
    ) = callbackFlow {
        if (!context.connectReady()) throw IllegalStateException("Scan not Ready,Please check bluetooth and permission")
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?
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

}