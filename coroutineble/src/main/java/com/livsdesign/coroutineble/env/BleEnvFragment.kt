package com.livsdesign.coroutineble.env

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager.PERMISSION_DENIED
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.livsdesign.coroutineble.R
import kotlinx.android.synthetic.main.fragment_ble_env.*


class BleEnvFragment : Fragment() {

    private val envViewModel: EnvViewModel by lazy {
        ViewModelProvider(requireActivity(), EnvViewModelFactory(requireContext())).get(
            EnvViewModel::class.java
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_ble_env, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        envViewModel.envLiveData.observe(this, Observer {
            if (it.ready()) {
                fragmentManager?.apply {
                    try {
                        beginTransaction().remove(this@BleEnvFragment).commitNowAllowingStateLoss()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            } else {
                if (it.bluetooth) {
                    bluetoothBtn.isEnabled = false
                    bluetoothBtn.text = "已开启"
                } else {
                    bluetoothBtn.isEnabled = true
                    bluetoothBtn.text = "去开启"
                }
                if (it.location) {
                    locationBtn.isEnabled = false
                    locationBtn.text = "已开启"
                } else {
                    locationBtn.isEnabled = true
                    locationBtn.text = "去开启"
                }
                if (it.locationPermission) {
                    permissionBtn.isEnabled = false
                    permissionBtn.text = "已授权"
                } else {
                    permissionBtn.isEnabled = true
                    permissionBtn.text = "去授权"
                }
            }
        })

        setupView()
    }

    private fun setupView() {
        bluetoothBtn.setOnClickListener {
            val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            intent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivityForResult(intent, 100)
        }

        locationBtn.setOnClickListener {
            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            intent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivityForResult(intent, 100)
        }

        permissionBtn.setOnClickListener {
            val permissions = arrayOf(
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
            val sp = it.context.getSharedPreferences("ble_env", Context.MODE_PRIVATE)
            val firstRequest = sp.getBoolean("firstRequest", true)
            if (firstRequest) {
                requestPermissions(permissions, 100)
                sp.edit().putBoolean("firstRequest", false).apply()
            } else {
                val should1 =
                    shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)
                val should2 =
                    shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION)
                if (should1 && should2) {
                    requestPermissions(permissions, 100)
                } else {
                    val intent = Intent()
                    intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                    intent.data = Uri.parse("package:" + it.context.packageName)
                    startActivity(intent)
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100) {
            envViewModel.envLiveData.checkEnv()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100) {
            var needCheck = true
            for (index in grantResults.indices) {
                val refuse = grantResults[index] == PERMISSION_DENIED
                if (refuse) {
                    needCheck = false
                    break
                }
            }
            if (needCheck) {
                envViewModel.envLiveData.checkEnv()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        envViewModel.envLiveData.checkEnv()
    }

    //全屏显示
    var lastFlag = WindowManager.LayoutParams.FLAG_FULLSCREEN
    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is FragmentActivity) {
            lastFlag = context.window.attributes.flags
            context.window.setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
            )
        } else if (context is Fragment) {
            lastFlag = context.requireActivity().window.attributes.flags
            context.requireActivity().window.setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
            )
        }
    }

    override fun onDetach() {
        super.onDetach()
        requireActivity().window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
    }

}