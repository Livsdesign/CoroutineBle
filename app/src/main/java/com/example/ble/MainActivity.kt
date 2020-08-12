package com.example.ble

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.livsdesign.coroutineble.connect.BleMgr
import com.livsdesign.coroutineble.env.EnvViewModel
import com.livsdesign.coroutineble.env.EnvViewModelFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi

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
                connection.setOnStatusChangedCallback(null)
            }
        })
    }

}
