package com.livsdesign.bluetooth

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import com.livsdesign.bluetooth.page.condition.ConditionView
import com.livsdesign.bluetooth.ui.theme.CoroutineBleTheme
import com.livsdesign.coroutineble.model.BleFactor

class MainActivity : ComponentActivity() {

    private val state = mutableStateOf(BleFactor())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CoroutineBleTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ConditionView()
                }
            }
        }
    }

}
