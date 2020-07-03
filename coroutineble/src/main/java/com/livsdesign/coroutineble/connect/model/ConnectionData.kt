package com.livsdesign.coroutineble.connect.model

import androidx.lifecycle.LiveData

class ConnectionData : LiveData<ConnectionData>() {

    var mac: String = ""

    var state = ConnectionState.IDLE
        set(value) {
            if (field != value) {
                field = value
                postValue(this)
            }
        }

}