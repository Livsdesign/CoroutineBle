package com.livsdesign.coroutineble.model

import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanResult
import android.os.SystemClock
import android.util.SparseArray
import com.livsdesign.coroutineble.toUInt16
import com.livsdesign.coroutineble.toUInt8
import java.nio.ByteBuffer

data class BleDevice(
    val scanResult: ScanResult
) {
    val mac: String = scanResult.device.address
    val advData: ByteArray? = scanResult.scanRecord?.bytes
    val rssi = scanResult.rssi
    val device: BluetoothDevice = scanResult.device
    val timeMills: Long = timeMillis(scanResult)
    var connectable = false
        private set
    var manufacturerData: ByteArray? = null
        private set
    var mesh = false
        private set
    val dataTypeArray = SparseArray<ByteArray>()
    var localName: String? = null
        private set

    private val DATA_TYPE_FLAGS: Byte = 0x01
    private val DATA_TYPE_LOCAL_NAME_COMPLETE: Byte = 0x09
    private val DATA_TYPE_MESH_ADV: Byte = 0x2B
    private val DATA_TYPE_SERVICE_UUIDS_16_BIT_COMPLETE: Byte = 0x03
    private val DATA_TYPE_MANUFACTURER_SPECIFIC_DATA = 0xFF.toByte()

    private fun setupAdvertise(advData: ByteArray) {
        val buffer = ByteBuffer.allocate(advData.size)
        buffer.put(advData)
        buffer.rewind()
        while (buffer.position() < buffer.capacity()) {
            val len: Int = buffer.get().toUInt8()
            if (buffer.position() + len <= buffer.capacity()) {
                val bytes = ByteArray(len)
                buffer[bytes]
                parseAdvParams(bytes)
            }
        }
    }

    private fun parseAdvParams(bytes: ByteArray) {
        if (bytes.isEmpty()) {
            return
        }
        val type = bytes[0]
        val content = ByteArray(bytes.size - 1)
        val key = type.toUInt8()
        dataTypeArray.put(key, content)
        System.arraycopy(bytes, 1, content, 0, content.size)
        when (type) {
            DATA_TYPE_FLAGS -> this.connectable = true
            DATA_TYPE_MANUFACTURER_SPECIFIC_DATA -> manufacturerData = content
            DATA_TYPE_LOCAL_NAME_COMPLETE -> localName = String(content)
            DATA_TYPE_MESH_ADV ->                 //adv承载器 （广播）
                this.mesh = true
            DATA_TYPE_SERVICE_UUIDS_16_BIT_COMPLETE -> {
                val uuid16bit: Int = toUInt16(content[0], content[1])
                //gatt 承载器（支持连接）
                mesh = uuid16bit == 0x2818 || uuid16bit == 0x2718
            }
            else -> {}
        }
    }

    private fun timeMillis(scanResult: ScanResult): Long {
        return System.currentTimeMillis() -
                SystemClock.elapsedRealtime() +
                scanResult.timestampNanos / 1000000
    }

    override fun toString(): String {
        val name = localName ?: "N/A"
        return "BleDevice(name='$name', mac='$mac', rssi=$rssi)"
    }

    init {
        advData?.let {
            setupAdvertise(it)
        }
    }
}