package com.livsdesign.bluetooth.page.connection

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import androidx.compose.runtime.mutableStateOf
import com.livsdesign.coroutineble.toUInt16
import com.livsdesign.coroutineble.toUInt8
import java.util.*

fun BluetoothGattCharacteristic.getPropertyList(): List<CharacteristicProperty> {

    val list = mutableListOf<CharacteristicProperty>()
    if (properties and BluetoothGattCharacteristic.PROPERTY_WRITE > 0) {
        list.add(CharacteristicProperty.Write(this))
    }
    if (properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE > 0) {
        list.add(CharacteristicProperty.WriteWithoutResponse(this))
    }
    if (properties and BluetoothGattCharacteristic.PROPERTY_SIGNED_WRITE > 0) {
        list.add(CharacteristicProperty.WriteSigned(this))
    }
    if (properties and BluetoothGattCharacteristic.PROPERTY_READ > 0) {
        list.add(CharacteristicProperty.Read(this))
    }
    if (properties and BluetoothGattCharacteristic.PROPERTY_INDICATE > 0) {
        list.add(CharacteristicProperty.Indicate(this))
    }
    if (properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY > 0) {
        list.add(CharacteristicProperty.Notify(this))
    }
    return list
}

sealed class CharacteristicProperty(
    val characteristic: BluetoothGattCharacteristic,
    val name: String,
) {

    class Write(characteristic: BluetoothGattCharacteristic) :
        CharacteristicProperty(characteristic, "Write")

    class WriteWithoutResponse(characteristic: BluetoothGattCharacteristic) :
        CharacteristicProperty(characteristic, "WriteNoRsp")

    class WriteSigned(characteristic: BluetoothGattCharacteristic) :
        CharacteristicProperty(characteristic, "WriteSigned")

    class Read(characteristic: BluetoothGattCharacteristic) :
        CharacteristicProperty(characteristic, "Read")

    class Indicate(characteristic: BluetoothGattCharacteristic) :
        CharacteristicProperty(characteristic, "Indicate") {

        val enable = mutableStateOf(false)
    }

    class Notify(characteristic: BluetoothGattCharacteristic) :
        CharacteristicProperty(characteristic, "Notify") {
        val enable = mutableStateOf(false)
    }

}

data class BleService(
    val service: BluetoothGattService
) {
    val name = BluetoothUuids.getUUIdDesc(service.uuid.toString())
    val uuidString = service.uuid.toString()

    val characteristics = service.characteristics.map {
        BleCharacteristic(it)
    }
}

data class BleCharacteristic(
    val characteristic: BluetoothGattCharacteristic
) {
    val name = BluetoothUuids.getUUIdDesc(characteristic.uuid.toString())
    val uuidString = characteristic.uuid.toString()

    val properties = characteristic.getPropertyList()

    val lastValue = mutableStateOf(ByteArray(0))
}