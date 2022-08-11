package com.livsdesign.bluetooth.page.condition

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.core.app.ActivityCompat
import com.livsdesign.coroutineble.*
import com.livsdesign.coroutineble.model.BleFactor

class ConditionsModel(
    bleFactor: BleFactor,
    requestLocationPermission: () -> Unit,
    requestScanPermission: () -> Unit,
    requestConnectPermission: () -> Unit,
) {

    val items: List<ConditionItemModel>

    init {
        bleFactor.apply {
            val connectBean = ConditionItemModel(
                if (bluetoothConnectPermission) "Connect Permission Ready" else "Connect permission not granted",
                if (bluetoothConnectPermission) "Ready" else "Grant",
                bluetoothConnectPermission.not()
            ) {
                requestConnectPermission.invoke()
            }

            val scanBean = ConditionItemModel(
                if (bluetoothScanPermission) "Scan Permission Ready" else "Scan permission not granted",
                if (bluetoothScanPermission) "Ready" else "Grant",
                bluetoothScanPermission.not()
            ) {
                requestScanPermission.invoke()
            }

            val bluetoothBean = ConditionItemModel(
                if (bluetoothEnable) "Bluetooth Ready" else "Bluetooth isn`t turned on",
                if (bluetoothEnable) "Ready" else "Enable",
                bluetoothEnable.not()
            ) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || ActivityCompat.checkSelfPermission(
                        it,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    val manager = it.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?
                    manager?.adapter?.enable()
                } else {
                    Toast.makeText(it, "Please grant connect Permission first!", Toast.LENGTH_LONG)
                        .show()
                }
            }


            val locationBean = ConditionItemModel(
                if (locationEnable) "Location Ready" else "Location isn`t turned on",
                if (locationEnable) "Ready" else "Enable",
                locationEnable.not()
            ) {
                val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                it.startActivity(intent)
            }

            val locationPermissionBean = ConditionItemModel(
                if (locationPermission) "Location Permission Ready" else "Location permission not granted",
                if (locationPermission) "Ready" else "Grant",
                locationPermission.not()
            ) {
                requestLocationPermission.invoke()
            }
            items = listOf(bluetoothBean, scanBean, connectBean, locationBean, locationPermissionBean)
        }


    }


}

data class ConditionItemModel(
    val content: String,
    val btnContent: String,
    val enable: Boolean,
    val action: (context: Context) -> Unit
)