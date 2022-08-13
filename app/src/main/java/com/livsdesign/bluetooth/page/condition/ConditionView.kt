package com.livsdesign.bluetooth.page.condition

import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.rememberPermissionState
import com.livsdesign.bluetooth.page.NavigationHost

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ConditionView() {

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


    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val viewModel = viewModel<ConditionViewModel>()
    DisposableEffect(context) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> viewModel.onResume(
                    context,
                    locationPermissionState,
                    scanPermissionState,
                    connectPermissionState
                )
                Lifecycle.Event.ON_STOP -> viewModel.onStop()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    val items = remember {
        viewModel.items
    }
    val ready = remember {
        viewModel.ready
    }
    val navHostController: NavHostController = rememberNavController()
    if (ready.value) {
        NavigationHost(navHostController)
    } else {
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
                itemsIndexed(items.value) { _, item ->
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

}


