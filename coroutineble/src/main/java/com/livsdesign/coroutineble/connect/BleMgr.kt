package com.livsdesign.coroutineble.connect

import android.app.Application
import com.clj.fastble.BleManager
import kotlinx.coroutines.ExperimentalCoroutinesApi

/**
 * todo 连接的配置
 */
@ExperimentalCoroutinesApi
class BleMgr {

    private var app: Application? = null

    fun setup(app: Application) {
        this.app = app
        //其实就是防止忘记初始化这个
        BleManager.getInstance().init(app)
        BleManager.getInstance().setReConnectCount(5, 250)
    }


    fun createConnection(): BleConnection {
        checkNotNull(app, { "need call function 'BleMgr.getInstance().setup(app)' first" })
        return BleConnection()
    }

    fun getAllConnectedDevices(): List<String> {
        checkNotNull(app, { "need call function 'BleMgr.getInstance().setup(app)' first" })
        return BleManager.getInstance().allConnectedDevice.map {
            it.mac
        }
    }

    companion object {
        @Volatile
        private var share: BleMgr? = null

        fun getInstance(): BleMgr =
            share ?: synchronized(this) {
                share ?: BleMgr().also { share = it }
            }
    }

}