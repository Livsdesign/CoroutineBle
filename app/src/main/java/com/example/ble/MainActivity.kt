package com.example.ble

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.livsdesign.coroutineble.connect.BleConnection
import com.livsdesign.coroutineble.connect.BleMgr
import com.livsdesign.coroutineble.env.EnvViewModel
import com.livsdesign.coroutineble.env.EnvViewModelFactory
import com.livsdesign.coroutineble.toHexString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@ExperimentalCoroutinesApi
class MainActivity : AppCompatActivity() {


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val viewModel =
            ViewModelProvider(this, EnvViewModelFactory(this)).get(EnvViewModel::class.java)

        viewModel.envObserve(this)

        viewModel.envLiveData.observe(this, Observer {
            if (it.ready()) {
                val bleMgr = BleMgr.getInstance(application)
                val connection = bleMgr.createConnection()
                setup(connection)
            }
        })
    }


    fun setup(connection: BleConnection) {
        GlobalScope.launch(Dispatchers.Main) {
            val result = connection.connect("55:55:55:34:23:11")

            val flow = if (result.state) {
                connection.notify(
                    "00001828-0000-1000-8000-00805F9B34FB",
                    "00002ADC-0000-1000-8000-00805F9B34FB"
                )
            } else {
                connection.notify(
                    "00001828-0000-1000-8000-00805F9B34FB",
                    "00002ADC-0000-1000-8000-00805F9B34FB"
                )
            }
            flow.collect {
                Log.e("flow", it.toString())
            }


        }
    }
}
