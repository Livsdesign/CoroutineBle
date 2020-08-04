package com.livsdesign.coroutineble.scan

import androidx.annotation.IntRange
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import no.nordicsemi.android.support.v18.scanner.*

@ExperimentalCoroutinesApi
class BleFlowScanner {

    val isScanningLiveData = MutableLiveData<Boolean>()

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
    var producerScope: ProducerScope<MutableList<ScanResult>>? = null

    fun scan(
        @IntRange(from = -1, to = 2) scanMode: Int = defaultScanMode,
        filters: List<ScanFilter> = defaultFilters
    ): Flow<List<ScanResult>> {
        return callbackFlow {
            producerScope = this
            val callback = object : ScanCallback() {

                override fun onScanFailed(errorCode: Int) {
                    super.onScanFailed(errorCode)
                    isScanningLiveData.postValue(false)
                    cancel()
                }

                override fun onBatchScanResults(results: MutableList<ScanResult>) {
                    super.onBatchScanResults(results)
                    if (results.isNotEmpty()) {
                        offer(results)
                    }
                }
            }
            if (!isActive) return@callbackFlow

            val settings = ScanSettings.Builder()
                .setLegacy(false)
                .setScanMode(scanMode)
                .setReportDelay(1000)//buffer
                .setUseHardwareBatchingIfSupported(false)//如果设成true的话有的设备会很慢
                .build()
            try {
                BluetoothLeScannerCompat.getScanner().startScan(filters, settings, callback)
                isScanningLiveData.postValue(true)
            } catch (e: Exception) {
                e.printStackTrace()
                isScanningLiveData.postValue(false)
                cancel()
            }
            awaitClose {
                BluetoothLeScannerCompat.getScanner().stopScan(callback)
                isScanningLiveData.postValue(false)
            }
        }
    }

    fun cancel() {
        producerScope?.cancel()
    }
}