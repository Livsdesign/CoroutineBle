package com.livsdesign.coroutineble.model

import androidx.lifecycle.LiveData

class ConnectionStatus : LiveData<ConnectionStatus>() {

    var current = ConnectionStep.IDLE
        set(value) {
            field = value
            postValue(this)
        }
    


}