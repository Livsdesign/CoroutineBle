package com.livsdesign.bluetooth.page.condition

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.mutableStateOf
import androidx.core.app.ActivityCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.livsdesign.coroutineble.model.BleFactor
import com.livsdesign.coroutineble.model.bleFactorMonitor
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class ConditionViewModel : ViewModel() {

    var items = mutableStateOf<List<ConditionItemModel>>(emptyList())
    var ready = mutableStateOf(false)

    private var job: Job? = null

    @OptIn(ExperimentalPermissionsApi::class)
    fun onResume(
        context: Context,
        locationPermissionState: PermissionState,
        scanPermissionState: PermissionState?,
        connectPermissionState: PermissionState?
    ) {
        job?.takeIf { it.isActive }?.apply { cancel() }
        job = viewModelScope.launch {
            context.bleFactorMonitor().collect {
                ready.value = it.scanAndConnectReady()
                it.updateItems(locationPermissionState, scanPermissionState, connectPermissionState)
            }
        }
    }

    fun onStop() {
        job?.takeIf { it.isActive }?.apply { cancel() }
    }

    @OptIn(ExperimentalPermissionsApi::class)
    private fun BleFactor.updateItems(
        locationPermissionState: PermissionState,
        scanPermissionState: PermissionState?,
        connectPermissionState: PermissionState?
    ) {
        val connectBean = ConditionItemModel(
            if (bluetoothConnectPermission) "Connect Permission Ready" else "Connect permission not granted",
            if (bluetoothConnectPermission) "Ready" else "Grant",
            bluetoothConnectPermission.not()
        ) {
            connectPermissionState?.apply {
                if (permissionRequested && !shouldShowRationale) {
                    it.manualGrant()
                } else {
                   launchPermissionRequest()
                }
            }
        }

        val scanBean = ConditionItemModel(
            if (bluetoothScanPermission) "Scan Permission Ready" else "Scan permission not granted",
            if (bluetoothScanPermission) "Ready" else "Grant",
            bluetoothScanPermission.not()
        ) {
            scanPermissionState?.apply {
                if (permissionRequested && !shouldShowRationale) {
                    it.manualGrant()
                } else {
                    launchPermissionRequest()
                }
            }
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
            if (locationPermissionState.permissionRequested && !locationPermissionState.shouldShowRationale) {
                it.manualGrant()
            } else {
                locationPermissionState.launchPermissionRequest()
            }
        }
        items.value =
            listOf(bluetoothBean, scanBean, connectBean, locationBean, locationPermissionBean)
    }
}

data class ConditionItemModel(
    val content: String,
    val btnContent: String,
    val enable: Boolean,
    val action: (Context) -> Unit
)

/**
 * 跳转到设置页面
 */
fun Context.manualGrant() {
    val intent = Intent()
    intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
    intent.data = Uri.parse("package:$packageName")
    startActivity(intent)
}