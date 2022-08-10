package com.livsdesign.coroutineble.model

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.os.Build
import androidx.annotation.IntRange
import com.livsdesign.coroutineble.*
import java.util.*


@SuppressLint("MissingPermission")
sealed class BleRequest() {

    val date = Date()
    var result = false

    internal open fun connectDevice(): BluetoothGatt? {
        return null
    }

    internal abstract fun BluetoothGatt.execute()

    class Connect(
        private val context: Context,
        val device: BluetoothDevice,
        private val callback: BluetoothGattCallback,
        val autoConnect: Boolean = false,
        val phy: Int = 1,
    ) : BleRequest() {

        override fun BluetoothGatt.execute() {
            //ignore
        }

        override fun connectDevice(): BluetoothGatt? {
            return context.connectBleDevice(device, callback, autoConnect, phy).apply {
                result = this != null
            }
        }
    }

    class Disconnect() : BleRequest() {
        override fun BluetoothGatt.execute() {
            disconnect()
            result = true
        }
    }

    class DiscoverServices() : BleRequest() {
        override fun BluetoothGatt.execute() {
            result = discoverServices()
        }
    }

    class SetNotification(
        val characteristic: BluetoothGattCharacteristic,
        val enable: Boolean
    ) : BleRequest() {

        override fun BluetoothGatt.execute() {
            result = setCharacteristicNotification(characteristic, enable)
        }

    }

    class SetMTU(
        @IntRange(from = 23, to = 517) val size: Int
    ) : BleRequest() {

        override fun BluetoothGatt.execute() {
            result = requestMtu(size)
        }

    }

    class SetConnectionProperty(
        @IntRange(from = 0, to = 2) val property: Int
    ) : BleRequest() {

        override fun BluetoothGatt.execute() {
            result = requestConnectionPriority(property)
        }

    }

    class SetPhy(
        val tx: Int,
        val rx: Int,
        val phyOptions: Int
    ) : BleRequest() {

        override fun BluetoothGatt.execute() {
            result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                setPreferredPhy(tx, rx, phyOptions)
                true
            } else false
        }

    }

    class Read(
        val characteristic: BluetoothGattCharacteristic
    ) : BleRequest() {
        override fun BluetoothGatt.execute() {
            result = readCharacteristic(characteristic)
        }
    }

    class ReadDescriptor(
        val descriptor: BluetoothGattDescriptor
    ) : BleRequest() {

        override fun BluetoothGatt.execute() {
            result = readDescriptor(descriptor)
        }
    }

    class Write(
        val characteristic: BluetoothGattCharacteristic,
        val writeType: Int,
        val newValue: ByteArray
    ) : BleRequest() {

        override fun BluetoothGatt.execute() {
            val flag = when (writeType) {
                BluetoothGattCharacteristic.WRITE_TYPE_SIGNED ->
                    characteristic.hasProperty(BluetoothGattCharacteristic.PROPERTY_SIGNED_WRITE)
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT ->
                    characteristic.hasProperty(BluetoothGattCharacteristic.PROPERTY_WRITE)
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE ->
                    characteristic.hasProperty(BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)
                else -> false
            }
            result = if (flag) {
                characteristic.writeType = writeType
                characteristic.value = newValue
                writeCharacteristic(characteristic)
            } else {
                false
            }
        }
    }

    class WriteDescriptor(
        val descriptor: BluetoothGattDescriptor,
        val newValue: ByteArray
    ) : BleRequest() {

        override fun BluetoothGatt.execute() {
            descriptor.value = newValue
            result = writeDescriptor(descriptor)
        }
    }

    class Error(val errorMessage:String):BleRequest(){
        override fun BluetoothGatt.execute() {
        }
    }

}

sealed class BleResponse(val status: Int) {

    open val date = Date()
    fun isSuccess() = status == BluetoothGatt.GATT_SUCCESS

    class OnConnectionStateChange(val state: ConnectionState) :
        BleResponse(BluetoothGatt.GATT_SUCCESS)

    class OnServicesDiscovered(val services: List<BluetoothGattService>, status: Int) :
        BleResponse(status)

    //todo characteristic是否是引用
    class OnCharacteristicRead(val characteristic: BluetoothGattCharacteristic, status: Int) :
        BleResponse(status)

    class OnCharacteristicWrite(val characteristic: BluetoothGattCharacteristic, status: Int) :
        BleResponse(status)

    class OnCharacteristicChanged(val characteristic: BluetoothGattCharacteristic) :
        BleResponse(BluetoothGatt.GATT_SUCCESS)

    class OnDescriptorWrite(val descriptor: BluetoothGattDescriptor, status: Int) :
        BleResponse(status)

    class OnDescriptorRead(
        val descriptor: BluetoothGattDescriptor,
        status: Int,
    ) : BleResponse(status)

    class OnMtuChanged(
        val mtu: Int,
        status: Int
    ) : BleResponse(status)
}