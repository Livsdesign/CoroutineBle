package com.livsdesign.coroutineble.connect

import android.app.Application
import com.clj.fastble.BleManager

/**
 * todo 连接的配置
 */
class BleMgr private constructor(app: Application) {

    val connections = mutableListOf<BleConnection>()

    init {
        BleManager.getInstance().init(app)
        BleManager.getInstance().setReConnectCount(2, 1000)
    }

    private fun createConnection(mac: String): BleConnection {
        val connection = BleConnection(mac)
        connections.add(connection)
        return connection
    }

    fun getConnection(mac: String): BleConnection {
        var mConnection: BleConnection? = null
        for (connection in connections) {
            if (connection.mac == mac) {
                mConnection = connection
                break
            }
        }
        if (mConnection == null) {
            mConnection = createConnection(mac)
        }
        return mConnection
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