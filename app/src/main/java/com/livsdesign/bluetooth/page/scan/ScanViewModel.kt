package com.livsdesign.bluetooth.page.scan

import android.bluetooth.le.ScanResult
import android.content.Context
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.livsdesign.coroutineble.BleScanner
import com.livsdesign.coroutineble.model.BleDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class ScanViewModel : ViewModel() {
    private val TAG = "ScanViewModel"

    private val scanner = BleScanner()
    private val deviceCache = mutableMapOf<String, BleDevice>()
    private var sortedList = mutableListOf<String>()
    val devices = mutableStateOf<List<BleDevice>>(emptyList())
    private val setting = AtomicBoolean(false)

    private var scanJob: Job? = null
    fun startScan(context: Context) {
        stopScan()
        scanJob = viewModelScope.launch {
            scanner.discover(context).catch {
                Log.e("TAG", "startScan: ${it.message}")
            }.collect {
                Log.e(TAG, "startScan: " )
                appendDevices(it)
            }
        }
    }

    fun stopScan() {
        scanJob?.takeIf { it.isActive }?.apply {
            cancel()
        }
    }

    private fun appendDevices(scanResults: List<ScanResult>) =
        viewModelScope.launch(Dispatchers.Default) {
            //maybe miss devices
            if (setting.compareAndSet(false, true)) {
                for (scanResult in scanResults) {
                    val device = BleDevice(scanResult)
                    deviceCache[device.mac] = device
                    if (!sortedList.contains(device.mac)) {
                        sortedList.add(device.mac)
                    }
                }
                val newList = mutableListOf<BleDevice>()
                for (mac in sortedList) {
                    deviceCache[mac]?.let {
                        newList.add(it)
                    }
                }
                devices.value = newList
                setting.set(false)
            }
        }

    fun sortDevices() = viewModelScope.launch(Dispatchers.Default) {
        if (setting.compareAndSet(false, true)) {
            val list: List<BleDevice> = deviceCache.values.toList().sortedWith { o1, o2 ->
                val diff = o1.rssi - o2.rssi
                if (diff == 0) {
                    0
                } else if (diff > 0) {
                    -1
                } else {
                    1
                }
            }
            list.let {
                devices.value = it.toMutableList()
                sortedList = it.map { device -> device.mac }.toMutableList()
            }
            setting.set(false)
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopScan()
    }

}