package com.livsdesign.bluetooth

import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.livsdesign.bluetooth.page.NavigationHost
import com.livsdesign.bluetooth.page.condition.ConditionView
import com.livsdesign.bluetooth.page.scan.ScanResultView
import com.livsdesign.bluetooth.ui.theme.CoroutineBleTheme
import com.livsdesign.coroutineble.STATE_READY_SCAN_CONNECT
import com.livsdesign.coroutineble.checkBluetoothState

class MainActivity : ComponentActivity() {

    private val state = mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CoroutineBleTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val mState = remember { state }
                    val navHostController: NavHostController = rememberNavController()
                    if (mState.value and STATE_READY_SCAN_CONNECT == STATE_READY_SCAN_CONNECT) {
                        NavigationHost(navHostController)
                    } else {
                        ConditionView(mState.value)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateBluetoothState()
        registerChanged()
    }

    override fun onStop() {
        super.onStop()
        unregisterChanged()
    }

    fun updateBluetoothState() {
        state.value = checkBluetoothState()
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action ?: return) {
                BluetoothAdapter.ACTION_STATE_CHANGED,
                LocationManager.PROVIDERS_CHANGED_ACTION -> updateBluetoothState()
            }
        }
    }

    private fun registerChanged() {
        val intentFilter = IntentFilter()
        intentFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        intentFilter.addAction(LocationManager.PROVIDERS_CHANGED_ACTION)
        registerReceiver(receiver, intentFilter)
    }

    private fun unregisterChanged() {
        unregisterReceiver(receiver)
    }
}
