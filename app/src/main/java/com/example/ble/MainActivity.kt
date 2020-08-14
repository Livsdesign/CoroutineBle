package com.example.ble

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.livsdesign.coroutineble.connect.BleConnection
import com.livsdesign.coroutineble.connect.BleMgr
import com.livsdesign.coroutineble.connect.model.BleResult
import com.livsdesign.coroutineble.env.EnvViewModel
import com.livsdesign.coroutineble.env.EnvViewModelFactory
import com.livsdesign.coroutineble.toHexString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.GlobalScope
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
                val bleMgr = BleMgr.getInstance()
//                bleMgr.setup(application)
                val connection = bleMgr.createConnection()
                test(connection)
            }
        })
    }

    fun test(connect: BleConnection) {
        GlobalScope.launch(Dispatchers.Main) {
            val result = connect.connect("FF:FF:41:00:75:D2"){

            }
            if (result.state) {
                val response = connect.setNotification(
                    "4a425453-4720-4d65-7368-204c45441910",
                    "4a425453-4720-4d65-7368-204c45441911",
                    false
                ) {
                    Log.e("??", it.toHexString())
                }
                if (response is BleResult.Failed) {
                    Log.e("??", response.exception.message.toString())
                } else {
                    Log.e("??", "success")
                }
            }
        }
    }

}
