package com.livsdesign.bluetooth.page.condition

import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.rememberPermissionState

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ConditionView(state: Int) {

    val locationPermissionState = rememberPermissionState(
        android.Manifest.permission.ACCESS_FINE_LOCATION
    )

    val scanPermissionState: PermissionState? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            rememberPermissionState(
                android.Manifest.permission.BLUETOOTH_SCAN
            )
        } else {
            null
        }
    val connectPermissionState: PermissionState? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            rememberPermissionState(
                android.Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            null
        }
    val model = ConditionsModel(
        state,
        requestConnectPermission = {
            connectPermissionState?.launchPermissionRequest()
        },
        requestScanPermission = {
            scanPermissionState?.launchPermissionRequest()
        },
        requestLocationPermission = {
            locationPermissionState.launchPermissionRequest()
        })


    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Text("Warning", style = MaterialTheme.typography.h4)
        Text(
            "This is a bluetooth app that requires bluetooth and location functions. Please turn on or grant the following functions and permissions.",
            style = MaterialTheme.typography.subtitle1
        )
        Spacer(modifier = Modifier.height(8.dp))
        Divider(
            modifier = Modifier.fillMaxWidth(),
            color = Color.LightGray,
            thickness = Dp.Hairline
        )
        Spacer(modifier = Modifier.height(16.dp))
        LazyColumn {
            itemsIndexed(model.items) { _, item ->
                Column {
                    Text(item.content)
                    Button(onClick = {
                        item.action.invoke(context)
                    }, enabled = item.enable) {
                        Text(item.btnContent)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}


