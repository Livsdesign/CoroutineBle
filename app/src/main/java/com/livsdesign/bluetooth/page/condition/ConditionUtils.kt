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

class ConditionsModel(
    value: Int,
    requestLocationPermission: () -> Unit,
    requestScanPermission: () -> Unit,
    requestConnectPermission: () -> Unit,
) {

    val items: List<ConditionItemModel>

    init {
        val flag1 = value and FLAG_BLUETOOTH_TOGGLE > 0
        val flag2 = value and FLAG_BLUETOOTH_SCAN > 0
        val flag3 = value and FLAG_BLUETOOTH_CONNECT > 0
        val flag4 = value and FLAG_LOCATION_TOGGLE > 0
        val flag5 = value and FLAG_LOCATION_PERMISSION > 0

        val connectBean = ConditionItemModel(
            if (flag3) "Connect Permission Ready" else "Connect permission not granted",
            if (flag3) "Ready" else "Grant",
            flag3.not()
        ) {
            requestConnectPermission.invoke()
        }

        val scanBean = ConditionItemModel(
            if (flag2) "Scan Permission Ready" else "Scan permission not granted",
            if (flag2) "Ready" else "Grant",
            flag2.not()
        ) {
            requestScanPermission.invoke()
        }

        val bluetoothBean = ConditionItemModel(
            if (flag1) "Bluetooth Ready" else "Bluetooth isn`t turned on",
            if (flag1) "Ready" else "Enable",
            flag1.not()
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
            if (flag4) "Location Ready" else "Location isn`t turned on",
            if (flag4) "Ready" else "Enable",
            flag4.not()
        ) {
            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            it.startActivity(intent)
        }

        val locationPermissionBean = ConditionItemModel(
            if (flag5) "Location Permission Ready" else "Location permission not granted",
            if (flag5) "Ready" else "Grant",
            flag5.not()
        ) {
            requestLocationPermission.invoke()
        }
        items = listOf(bluetoothBean, scanBean, connectBean, locationBean, locationPermissionBean)
    }


}

data class ConditionItemModel(
    val content: String,
    val btnContent: String,
    val enable: Boolean,
    val action: (context: Context) -> Unit
)