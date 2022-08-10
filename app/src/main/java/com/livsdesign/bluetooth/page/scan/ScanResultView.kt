package com.livsdesign.bluetooth.page.scan

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.livsdesign.bluetooth.page.Route
import com.livsdesign.coroutineble.model.BleDevice
import com.livsdesign.coroutineble.model.ConnectionState

@Composable
fun ScanResultView(navHostController: NavHostController) {
    val viewModel = viewModel<ScanViewModel>()
    val context = LocalContext.current
    DisposableEffect(Unit) {
        viewModel.startScan(context)
        onDispose {
            viewModel.stopScan()
        }
    }
    val deviceItems = remember {
        viewModel.devices
    }
    val hasConnectPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
    Column(
        modifier = Modifier.fillMaxSize().background(color = MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        Text(
            "Discover",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        if (deviceItems.value.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text("Scanning...")
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                itemsIndexed(deviceItems.value) { index, item ->
                    DeviceItemView(item, hasConnectPermission) {
                        navHostController.navigate("${Route.PERIPHERAL.key}/${it.mac}")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
        Button(
            onClick = {
                viewModel.sortDevices()
            }, modifier = Modifier.fillMaxWidth()
        ) {
            Text("Sort", modifier = Modifier.padding(8.dp))
        }
    }
}

@Composable
fun DeviceItemView(
    item: BleDevice,
    hasConnectPermission: Boolean,
    onConnectEvent: (BleDevice) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.onBackground,
                shape = RoundedCornerShape(16.dp)
            )
            .background(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(16.dp)
            )
            .padding(16.dp)
    ) {
        Column {
            Text(
                item.getDisplayName(hasConnectPermission),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                "${item.mac}(${item.rssi}mdb)",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (item.connectable) {
            Spacer(modifier = Modifier.fillMaxWidth().weight(1f))
            Button(onClick = { onConnectEvent.invoke(item) }) {
                Text("Connect")
            }
        }

    }
}

@SuppressLint("MissingPermission")
fun BleDevice.getDisplayName(hasConnectPermission: Boolean): String {
    if (hasConnectPermission && !device.name.isNullOrEmpty()) {
        return device.name!!
    }
    if (!localName.isNullOrEmpty()) {
        return localName!!
    }
    return "N/A"
}
