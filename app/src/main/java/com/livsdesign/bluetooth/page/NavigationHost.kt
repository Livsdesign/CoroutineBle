package com.livsdesign.bluetooth.page

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.livsdesign.bluetooth.page.connection.ConnectionView
import com.livsdesign.bluetooth.page.scan.ScanResultView

@Composable
fun NavigationHost(
    navHostController: NavHostController,
) {
    NavHost(
        navHostController,
        startDestination = Route.CENTRAL.key,
    ) {
        composable(Route.CENTRAL.key) {
            ScanResultView(navHostController)
        }
        composable("${Route.PERIPHERAL.key}/{mac}", listOf(navArgument("mac") {
            type = NavType.StringType
        })) {
            ConnectionView(navHostController, it.arguments?.getString("mac"))
        }
    }
}


enum class Route(
    val key: String
) {
    //主页
    CENTRAL("Central"),//1.扫描；2.管理多个外设的连接状态

    //外设profile
    PERIPHERAL("Peripheral"),

}

