package com.livsdesign.coroutineble.env

data class Environment(
    var bluetooth: Boolean,
    var location: Boolean,
    var locationPermission: Boolean
) {
    fun ready(): Boolean {
        return bluetooth && location && locationPermission
    }

    override fun toString(): String {
        return "Environment(bluetooth=$bluetooth, location=$location, locationPermission=$locationPermission)"
    }


    fun equalsEnv(env: Environment): Boolean {
        return bluetooth == env.bluetooth
                && location == env.location
                && locationPermission == env.locationPermission
    }

}