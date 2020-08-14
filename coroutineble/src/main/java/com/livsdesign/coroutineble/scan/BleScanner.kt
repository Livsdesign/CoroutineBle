package com.livsdesign.coroutineble.scan

import androidx.annotation.IntRange
import androidx.lifecycle.MutableLiveData
import com.livsdesign.coroutineble.SingleLiveEvent
import no.nordicsemi.android.support.v18.scanner.*

/**
 * 使用LiveData实现
 */
class BleScanner private constructor() {

    val isScanningLiveData = MutableLiveData<Boolean>().apply {
        value = false
    }

    /**
     * 在这一秒内扫描到的设备，并未所有扫描到的设备汇总
     */
    val scanResultLiveEvent = SingleLiveEvent<List<ScanResult>>()


    private var scanCallback = newCallback()

    /**
     * @param scanMode
     *
     * 越来越耗电，但越来越快
     * @see android.bluetooth.le.ScanSettings.SCAN_MODE_OPPORTUNISTIC
     * @see android.bluetooth.le.ScanSettings.SCAN_MODE_LOW_POWER
     * @see android.bluetooth.le.ScanSettings.SCAN_MODE_BALANCED
     * @see android.bluetooth.le.ScanSettings.SCAN_MODE_LOW_LATENCY
     *
     * 扫描限制：在android7.0后，在30s内，不能超过5次扫描周期（scan和stop为一个扫描周期）
     * 扫描建议：扫描开始和结束关联页面生命周期。
     *
     */
    private val defaultScanMode = android.bluetooth.le.ScanSettings.SCAN_MODE_LOW_POWER
    private val defaultFilters = emptyList<ScanFilter>()
    fun scan(
        @IntRange(from = -1, to = 2) scanMode: Int = defaultScanMode,
        filters: List<ScanFilter> = defaultFilters
    ) {
        val settings = ScanSettings.Builder()
            .setLegacy(false)
            .setScanMode(scanMode)
            .setReportDelay(1000)//buffer
            .setUseHardwareBatchingIfSupported(false)//如果设成true的话有的设备会很慢
            .build()
        try {
            isScanningLiveData.postValue(true)
            scanCallback = newCallback()
            BluetoothLeScannerCompat.getScanner().startScan(filters, settings, scanCallback)
        } catch (e: Exception) {
            e.printStackTrace()
            isScanningLiveData.postValue(false)
        }
    }


    fun stop() {
        try {
            BluetoothLeScannerCompat.getScanner().stopScan(scanCallback)
            isScanningLiveData.postValue(false)
        } catch (e: IllegalArgumentException) {
            e.printStackTrace()
        }
    }

    private fun newCallback() = object : ScanCallback() {

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            isScanningLiveData.postValue(false)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            super.onBatchScanResults(results)
            if (results.isNotEmpty()) {
                if (isScanningLiveData.value != true) {
                    isScanningLiveData.value = true
                }
                scanResultLiveEvent.value = results
            }
        }
    }
}