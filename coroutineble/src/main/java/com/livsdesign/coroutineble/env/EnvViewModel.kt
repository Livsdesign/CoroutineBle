package com.livsdesign.coroutineble.env

import android.content.Context
import android.view.View
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class EnvViewModel(private val ctx: Context) : ViewModel() {

    var envLiveData = EnvLiveData(ctx)

    fun envObserve(activity: FragmentActivity) {
        envLiveData.observe(activity, Observer {
            val fm = activity.supportFragmentManager
            if (it.ready()) {
                val envFragment = fm.findFragmentByTag("env")
                if (envFragment != null) {
                    fm.beginTransaction()
                        .remove(envFragment)
                        .commitNowAllowingStateLoss()
                }
            } else {
                val parent = activity.window.decorView
                if (parent.id == View.NO_ID) {
                    parent.id = View.generateViewId()
                }
                fm.beginTransaction()
                    .replace(parent.id, BleEnvFragment(), "env")
                    .commitNowAllowingStateLoss()
            }
        })
    }

    companion object {

        /**
         * 生命周期与activity一致
         */
        fun loadViewModel(activity: FragmentActivity): EnvViewModel {
            return ViewModelProvider(
                activity,
                EnvViewModelFactory(activity)
            ).get(EnvViewModel::class.java)
        }

    }

}