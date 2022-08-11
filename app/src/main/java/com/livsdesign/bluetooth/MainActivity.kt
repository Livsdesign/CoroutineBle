package com.livsdesign.bluetooth

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.livsdesign.bluetooth.page.NavigationHost
import com.livsdesign.bluetooth.page.condition.ConditionView
import com.livsdesign.bluetooth.ui.theme.CoroutineBleTheme
import com.livsdesign.coroutineble.model.BleFactor
import com.livsdesign.coroutineble.model.bleFactorMonitor
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val state = mutableStateOf(BleFactor())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.e("???: ", "${state.value}")
        setContent {
            CoroutineBleTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
//                    val mState = remember { state }
//                    val navHostController: NavHostController = rememberNavController()
//                    if (mState.value.scanAndConnectReady()) {
//                        NavigationHost(navHostController)
//                    } else {
//                        ConditionView(mState.value)
//                    }
                    ConditionView()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.e("TAG", "onDestroy() called")
    }

}
