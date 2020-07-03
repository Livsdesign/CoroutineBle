package com.livsdesign.coroutineble.scan

import android.bluetooth.BluetoothDevice
import androidx.core.util.isNotEmpty
import no.nordicsemi.android.support.v18.scanner.ScanRecord
import java.nio.ByteBuffer

class BleDevice(
    val bluetoothDevice: BluetoothDevice,
    val rssi: Int,
    private val scanRecord: ScanRecord?
) {

    val receiveTimeMillis = System.currentTimeMillis()

    val localName: String = scanRecord?.deviceName ?: ""

    val bytes = scanRecord?.bytes

    val manufacturerData = scanRecord?.manufacturerSpecificData?.run {
        if (isNotEmpty()) {
            val data1 = this.valueAt(0)
            val data2 = keyAt(0).shr(8).and(0xff).toByte()
            val data3 = keyAt(0).and(0xff).toByte()
            val sb = ByteBuffer.allocate(data1.size + 2)
            sb.put(data3)
            sb.put(data2)
            sb.put(data1)
            sb.array()
        } else {
            null
        }
    }

    /**
     *  广播包 =》... FF ad cd [Manufacturer Specific Data]
     *
     * @param manufacturerId [0,65535]
     *
     * [ ad cd ] to Int ManufacturerId
     */
    fun getManufacturerSpecificData(manufacturerId: Int): ByteArray? {
        return scanRecord?.getManufacturerSpecificData(manufacturerId)
    }

}