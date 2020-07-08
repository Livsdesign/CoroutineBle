package com.livsdesign.coroutineble.connect

import android.app.Application
import com.clj.fastble.BleManager

/**
 * todo 连接的配置
 */
class BleMgr private constructor(app: Application) {

    init {
        BleManager.getInstance().init(app)
        BleManager.getInstance().setReConnectCount(2, 1000)
    }

    fun createConnection(): BleConnection {
        return BleConnection()
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