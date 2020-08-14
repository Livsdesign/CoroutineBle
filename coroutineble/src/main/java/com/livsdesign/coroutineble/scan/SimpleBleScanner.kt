package com.livsdesign.coroutineble.scan

import androidx.annotation.IntRange
import androidx.lifecycle.MutableLiveData
import com.livsdesign.coroutineble.connect.model.BleResult
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import no.nordicsemi.android.support.v18.scanner.*

typealias OnBatchScanCallback = (results: MutableList<ScanResult>) -> Unit

class SimpleBleScanner {

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
    private var mCallback: ScanCallback? = null

    suspend fun scan(
        @IntRange(from = -1, to = 2) scanMode: Int = defaultScanMode,
        filters: List<ScanFilter> = defaultFilters,
        batchScanCallback: OnBatchScanCallback
    ): BleResult {
        return suspendCancellableCoroutine {
            val callback = object : ScanCallback() {

                override fun onScanFailed(errorCode: Int) {
                    super.onScanFailed(errorCode)
                    isScanningLiveData.value=false
                    if (it.isActive) {
                        it.resume(BleResult.Failed(Exception("Scan fail,code:$errorCode")))
                    }
                }

                override fun onBatchScanResults(results: MutableList<ScanResult>) {
                    super.onBatchScanResults(results)
                    if (results.isNotEmpty()) {
                        batchScanCallback.invoke(results)
                    }
                }
            }
            mCallback = callback
            try {
                BluetoothLeScannerCompat.getScanner()
                    .startScan(filters, createSetting(scanMode), callback)
                isScanningLiveData.value=true
            } catch (e: Exception) {
                e.printStackTrace()
                isScanningLiveData.value=false
                if (it.isActive) {
                    it.resume(BleResult.Failed(e))
                }
            }
        }
    }

    fun stop() {
        mCallback ?: return
        try {
            BluetoothLeScannerCompat.getScanner().stopScan(mCallback!!)
            isScanningLiveData.value=false
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createSetting(
        @IntRange(from = -1, to = 2)
        scanMode: Int = defaultScanMode
    ): ScanSettings {
        return ScanSettings.Builder()
            .setLegacy(false)
            .setScanMode(scanMode)
            .setReportDelay(1000)//buffer
            .setUseHardwareBatchingIfSupported(false)//如果设成true的话有的设备会很慢
            .build()
    }

}