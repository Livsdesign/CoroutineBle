## CoroutineBle

[![0.0.1](https://jitpack.io/v/Livsdesign/CoroutineBle.svg)](https://jitpack.io/#Livsdesign/CoroutineBle)

> 使用协程进行二次封装的BLE库（个人自用）

### 导入
```groovy
//project build.gradle
allprojects {
	repositories {
		//...
		maven { url 'https://jitpack.io' }
	}
}
  
//module build.gradle
dependencies {
	implementation 'com.github.Livsdesign:CoroutineBle:Tag'
}
```

### 蓝牙状态检测

> Android系统从版本4.3开始支持低功耗蓝牙（BLE），且安卓设备具备蓝牙功能；
>
> Android6.0以后扫描蓝牙设备，需要用到位置信息功能
>
> Android6.0以后使用位置信息功能，必须动态申请位置信息权限
>
> 具体限制根据国产手机厂商ROM的代码而定，一般都符合以上规范

1. 蓝牙开关检测
2. 位置信息开关检测
3. 位置信息权限检测

#### 使用

​	在你的ViewModel中初始化EnvLiveData，并在Activity或者Fragment中Observe即可



> PS: 此库提供一个默认的UI交互方式，开发者可直接使用，或者利用EnvLiveData自定义UI
>
> 下面是默认方法的使用，自定义UI可以此为参考
>
> ```kotlin
> //这是默认的蓝牙状态检测的使用步骤
> //步骤一，初始化ViewModel
> private val envViewModel: EnvViewModel by lazy {
>     //ViewModelProvider的ViewModelStoreOwner必须是Actitivy
>     ViewModelProvider(activity, EnvViewModelFactory(context)).get(
>         EnvViewModel::class.java
>     )
> }
> 
> //步骤二，observe
> envViewModel.envObserve(activity)
> ```



### 扫描

扫描注意事项：

	1. Android 7.0以后，系统限制30s内扫描不能超过5次（scan -> stop为一次）
 	2. 以上限制同样以手机ROM的代码决定（国产手机）

> ```groovy
> //该扫描功能用Nordic的开源库来实现
> implementation 'no.nordicsemi.android.support.v18:scanner:1.4.3'
> ```

```Kotlin
//订阅扫描结果,1s内扫描到的结果
val liveData:MutableLiveData<List<BleDevice>>() = BleScanner.getInstance().scanResultLiveData
//默认以低功耗的方式开始扫描
BleScanner.getInstance().scan()//ScanSettings.SCAN_MODE_LOW_POWER
//or 选择其他扫描模式
BleScanner.getInstance().scan(ScanSettings.SCAN_MODE_BALANCED)
//结束扫描
BleScanner.getInstance().stop()
```

### 连接

```kotlin
val bleMgr=BleMgr.getInstance(application)
//获取连接对象
val connection=bleMgr.getConnection(mac)

//使用LiveData订阅连接状态
connection.data.observe(owner,Observer{ it -> 
    when(it.state){
        ConnectionState.IDLE->{}//未开始连接
        ConnectionState.CONNECTING->{}//连接中
        ConnectionState.CONNECTED->{}//已连接，却已发现服务
        ConnectionState.FAILED->{}//连接失败
        ConnectionState.DISCONNECTED->{}//手机发起的连接断开
        ConnectionState.LOST->{}//设备发起的连接断开
    }
})

GlobalScope.launch (Dispatchers.Main){
   val bleResult = connection.connect(mac)
    if(bleResult.state){
        Log.d("result","连接成功")
    }else{
        Log.d("result","连接失败；原因：${it.msg}")
    }
}

//断开连接
connection.disconnect()
```

### 写（Write or Write No Response）

```
GlobalScope.launch (Dispatchers.Main){
   val bleResult = connection.write(uuid_service,uuid_write,contentBytes)
    if(bleResult.state){
        Log.d("result","写成功")
    }else{
        Log.d("result","写失败；原因：${it.msg}")
    }
}
```

### 读（Read）

```Kotlin
GlobalScope.launch (Dispatchers.Main){
   val bleResult = connection.read(uuid_service,uuid_read)
    if(bleResult.state){
        Log.d("result","内容：${it.result}")
    }else{
        Log.d("result","读失败；原因：${it.msg}")
    }
}
```

### Notify or indicate

```Kotlin
GlobalScope.launch (Dispatchers.Main){
   val notifyFlow = connection.notify(uuid_service,uuid_notify)
   notifyFlow.flowOn(Dispatchers.Main)
    .collect{ bytes->
        //todo something
   }
    
   val indicateFlow = connection.indicate(uuid_service,uuid_indicate)
   indicateFlow.flowOn(Dispatchers.Main)
   .collect{ bytes->
        //todo something
   }
}
```

### 其他

```kotlin
//更改连接参数，改变响应速度
connection.requestConnectParam(connectionPriority)
//获取服务和特征
connection.getServices()
```

