package com.livsdesign.coroutineble.connect

import android.app.Application
import com.clj.fastble.BleManager
import kotlinx.coroutines.ExperimentalCoroutinesApi

/**
 * todo 连接的配置
 */
@ExperimentalCoroutinesApi
class BleMgr private constructor(app: Application) {

    init {
        //其实就是防止忘记初始化这个
        BleManager.getInstance().init(app)
        BleManager.getInstance().setReConnectCount(5, 250)
    }


    fun createConnection(): BleConnection {
        return BleConnection()
    }

    fun getAllConnectedDevices(): List<String> {
        return BleManager.getInstance().allConnectedDevice.map {
            it.mac
        }
    }

    companion object {

        @Volatile
        private var instance: BleMgr? = null

        fun getInstance(app: Application): BleMgr {
            return instance ?: synchronized(this) {
                return instance ?: BleMgr(app).also { instance = it }
            }
        }

    }
}