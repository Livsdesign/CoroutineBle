package com.livsdesign.bluetooth.page.connection

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.livsdesign.coroutineble.BluetoothPeripheral
import com.livsdesign.coroutineble.model.ConnectionState.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlin.random.Random

class ConnectionVMFactory(
    private val peripheral: BluetoothPeripheral
) : ViewModelProvider.NewInstanceFactory() {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ConnectionViewModel(peripheral) as T
    }
}

class ConnectionViewModel(private val peripheral: BluetoothPeripheral) : ViewModel() {

    val state = peripheral.connectionState
    val services = mutableStateOf<List<BleService>>(emptyList())

    fun onConnectionStateTap() {
        when (state.value) {
            IDLE, LOST, FAILED, DISCONNECTED -> connect()
            CONNECTING, DISCONNECTING -> {}
            CONNECTED -> disconnect()
        }
    }

    private fun connect() = viewModelScope.launch {
        peripheral.connect()
    }

    private fun disconnect() = viewModelScope.launch {
        peripheral.disconnect()
    }

    override fun onCleared() {
        super.onCleared()
        peripheral.release()
    }

    fun handleProperty(
        context: Context,
        parent: BleCharacteristic,
        property: CharacteristicProperty
    ) =
        viewModelScope.launch {
            when (property) {
                is CharacteristicProperty.Indicate -> toggleNotify(parent, property)
                is CharacteristicProperty.Notify -> toggleNotify(parent, property)
                is CharacteristicProperty.Read -> read(parent)
                is CharacteristicProperty.Write -> {
                    val randomBytes = Random.nextBytes(10)
                    if (peripheral.write(parent.characteristic, randomBytes)) {
                        parent.lastValue.value = randomBytes
                    } else {
                        Toast.makeText(context, "Write Failed", Toast.LENGTH_LONG).show()
                    }
                }
                is CharacteristicProperty.WriteSigned -> {
                    val randomBytes = Random.nextBytes(10)
                    if (peripheral.writeSigned(parent.characteristic, randomBytes)) {
                        parent.lastValue.value = randomBytes
                    } else {
                        Toast.makeText(context, "Write Failed", Toast.LENGTH_LONG).show()
                    }
                }
                is CharacteristicProperty.WriteWithoutResponse -> {
                    val randomBytes = Random.nextBytes(10)
                    if (peripheral.writeWithoutResponse(parent.characteristic, randomBytes)) {
                        parent.lastValue.value = randomBytes
                    } else {
                        Toast.makeText(context, "Write Failed", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

    ///////////////////////////////////////////////////////////////////////////
    //
    ///////////////////////////////////////////////////////////////////////////
    private val notifyJobMap = mutableMapOf<String, Job>()
    private fun toggleNotify(parent: BleCharacteristic, property: CharacteristicProperty) {
        if (property is CharacteristicProperty.Notify) {
            notifyJobMap[parent.uuidString]?.takeIf { it.isActive }?.apply { cancel() }
            if (property.enable.value) {
                property.enable.value = false
            } else {
                val job = viewModelScope.launch {
                    peripheral.notifications(parent.characteristic).catch { }.collect {
                        parent.lastValue.value = it
                    }
                }
                property.enable.value = true
                notifyJobMap[parent.uuidString] = job
            }
        } else if (property is CharacteristicProperty.Indicate) {
            notifyJobMap[parent.uuidString]?.takeIf { it.isActive }?.apply { cancel() }
            if (property.enable.value) {
                property.enable.value = false
            } else {
                val job = viewModelScope.launch {
                    peripheral.notifications(parent.characteristic).catch { }.collect {
                        parent.lastValue.value = it
                    }
                }
                property.enable.value = true
                notifyJobMap[parent.uuidString] = job
            }
        }
    }

    private fun cancelAllNotify() {
        for (job in notifyJobMap.values) {
            job.takeIf { it.isActive }?.apply { cancel() }
        }
        notifyJobMap.clear()
    }

    private fun read(bleCharacteristic: BleCharacteristic) = viewModelScope.launch {
        peripheral.read(bleCharacteristic.characteristic)?.let {
            bleCharacteristic.lastValue.value = it
        }
    }

    init {
        viewModelScope.launch {
            state.collect {
                services.value = if (it == CONNECTED) {
                    peripheral.discoverServices().map { gattService ->
                        BleService(gattService)
                    }
                } else {
                    cancelAllNotify()
                    emptyList()
                }
            }
        }
        connect()
    }
}

