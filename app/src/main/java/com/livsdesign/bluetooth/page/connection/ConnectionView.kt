package com.livsdesign.bluetooth.page.connection

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Divider
import androidx.compose.material.IconButton
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.livsdesign.bluetooth.R
import com.livsdesign.coroutineble.BluetoothPeripheral
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import com.google.accompanist.flowlayout.FlowRow
import com.livsdesign.coroutineble.model.ConnectionState
import com.livsdesign.coroutineble.model.ConnectionState.*
import com.livsdesign.coroutineble.toHexString

@Composable
fun ConnectionView(
    navHostController: NavHostController,
    mac: String?
) {
    val context = LocalContext.current
    val device = getBluetoothDevice(context, mac ?: "")
    if (device == null) {
        InvalidPeripheralView(navHostController, mac)
    } else {
        val peripheral = BluetoothPeripheral(context, device)
        ValidPeripheralView(navHostController, peripheral)
    }
}

@Composable
fun ValidPeripheralView(navHostController: NavHostController, peripheral: BluetoothPeripheral) {

    val viewModel = viewModel<ConnectionViewModel>(factory = ConnectionVMFactory(peripheral))
    val connectionState = viewModel.state.collectAsState()
    val services = remember { viewModel.services }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = {
                navHostController.popBackStack()
            }, modifier = Modifier.defaultMinSize(minHeight = 56.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_back),
                        contentDescription = null,
                    )
                    Text(
                        "Devices",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            Button(
                contentPadding = PaddingValues(8.dp),
                modifier = Modifier.defaultMinSize(minHeight = 32.dp),
                enabled = connectionState.value.canTap(),
                onClick = {
                    viewModel.onConnectionStateTap()
                },
            ) {
                Text(connectionState.value.name, style = MaterialTheme.typography.labelSmall)
            }
            Spacer(modifier = Modifier.width(16.dp))
        }
        Divider(
            modifier = Modifier.fillMaxWidth().height(1.dp),
            color = MaterialTheme.colorScheme.primary
        )
        if (services.value.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(if (connectionState.value == CONNECTED) "Empty" else "Not connected")
            }
        } else {
            LazyColumn {
                itemsIndexed(services.value) { _, item ->
                    ServiceView(item, viewModel)
                }
            }
        }
    }
}

@Composable
fun ServiceView(service: BleService, viewModel: ConnectionViewModel) {
    var expand by remember {
        mutableStateOf(false)
    }
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(8.dp)
        ) {
            Column {
                Text(
                    service.name,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    service.uuidString,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = {
                expand = expand.not()
            }) {
                val res = if (expand) R.drawable.ic_hide else R.drawable.ic_show
                Icon(
                    painter = painterResource(id = res),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        if (expand) {
            if (service.characteristics.isEmpty()) {
                Text("empty")
            } else {
                for (characteristic in service.characteristics) {
                    CharacteristicView(characteristic, viewModel)
                }
            }

        }
    }
}

@Composable
fun CharacteristicView(
    characteristic: BleCharacteristic,
    viewModel: ConnectionViewModel
) {
    val context = LocalContext.current
    val desc = remember {
        characteristic.lastValue
    }
    Column(
        modifier = Modifier.fillMaxWidth().background(color = MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Text(characteristic.name, style = MaterialTheme.typography.bodyMedium)
        Text(
            characteristic.uuidString,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.tertiary
        )
        FlowRow(
            mainAxisSpacing = 4.dp,
            crossAxisSpacing = 4.dp
        ) {
            for (property in characteristic.properties) {
                Button(
                    onClick = {
                        viewModel.handleProperty(context, characteristic, property)
                    },
                    contentPadding = PaddingValues(4.dp),
                    modifier = Modifier.defaultMinSize(minHeight = 32.dp)
                ) {
                    if (property is CharacteristicProperty.Indicate) {
                        val enable = remember { property.enable }
                        val res = if (enable.value) R.drawable.ic_checked else R.drawable.ic_uncheck
                        Icon(
                            painter = painterResource(res),
                            null,
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                    } else if (property is CharacteristicProperty.Notify) {
                        val enable = remember { property.enable }
                        val res = if (enable.value) R.drawable.ic_checked else R.drawable.ic_uncheck
                        Icon(
                            painter = painterResource(res),
                            null,
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                    Text(property.name, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        if (desc.value.isNotEmpty()) {
            Text(
                desc.value.toHexString(' ').run { "Current: $this" },
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.background(
                    color = MaterialTheme.colorScheme.tertiary,
                    shape = RoundedCornerShape(4.dp)
                ),
                color = MaterialTheme.colorScheme.onTertiary
            )
        }
    }
}


@Composable
fun InvalidPeripheralView(navHostController: NavHostController, mac: String?) {
    Column {
        TextButton(onClick = {
            navHostController.popBackStack()
        }, modifier = Modifier.defaultMinSize(minHeight = 56.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_back),
                    contentDescription = null,
                )
                Text(
                    "Devices",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        Divider(
            modifier = Modifier.fillMaxWidth().height(1.dp),
            color = MaterialTheme.colorScheme.primary
        )

        Box {
            Text(
                "Invalid Device",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

fun getBluetoothDevice(context: Context, mac: String): BluetoothDevice? {
    val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?
    return try {
        manager?.adapter?.getRemoteDevice(mac)
    } catch (e: Exception) {
        null
    }
}

fun ConnectionState.canTap(): Boolean {
    return when (this) {
        IDLE, CONNECTED, LOST, FAILED, DISCONNECTED -> true
        CONNECTING, DISCONNECTING -> false
    }
}