package com.example.smartwardrobe

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.clj.fastble.BleManager
import com.clj.fastble.callback.BleGattCallback
import com.clj.fastble.callback.BleScanCallback
import com.clj.fastble.data.BleDevice
import com.clj.fastble.exception.BleException
import com.clj.fastble.scan.BleScanRuleConfig
import kotlinx.android.synthetic.main.activity_main.*
import org.jetbrains.anko.alert
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.toast
import org.jetbrains.anko.uiThread
import kotlin.system.exitProcess

/*
 *  @项目名：  SmartWardrobe
 *  @APP名：   IntWardrobe
 *  @创建者:   Lfalive
 *  @创建时间: 2019/7/14
*/

class MainActivity : AppCompatActivity() {

    var mbleDevice: BleDevice? = null    //待连接的设备

    @SuppressLint("SetTextI18n")    //忽略警告
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        BleManager.getInstance().init(application)  //轮子初始化
        BleManager.getInstance()
            .enableLog(true)
            .setReConnectCount(1, 5000)
            .setSplitWriteNum(20)
            .setConnectOverTime(10000).operateTimeout = 5000

        //初始化
        tv_status.text = if(BleManager.getInstance().isBlueEnable) "开启" else "关闭"
        initPermission()

        //蓝牙开关按钮
        btn_openbt.setOnClickListener { btSwitch(1) }
        btn_closebt.setOnClickListener { btSwitch(0) }

        //开始扫描
        btn_startsearch.setOnClickListener {
            if(!BleManager.getInstance().isBlueEnable) toast("请打开蓝牙！")
            else {
                //注意这里有扫描参数设置
                startScan(BleScanRuleConfig.Builder().
                    setScanTimeOut(10000).
                    build())
            }
        }

        //连接设备
        btn_connect.setOnClickListener {
            if(mbleDevice != null) connect()
            else tv_connectlog.text = tv_connectlog.text.toString() + "\n未发现智能衣柜！"
        }

        //连接状态
        btn_connectstate.setOnClickListener {
            if(BleManager.getInstance().isConnected(mbleDevice)) tv_connectlog .text = tv_connectlog.text.toString() + "\nOK!"
            else tv_connectlog.text = tv_connectlog.text.toString() + "\nNOT OK!"
        }

        //断开连接
        btn_disconnect.setOnClickListener {
            if(BleManager.getInstance().isConnected(mbleDevice))
                BleManager.getInstance().disconnect(mbleDevice)
            else tv_connectlog.text = tv_connectlog.text.toString() + "\n暂无设备连接！"
        }

    }

    private fun initPermission(){
        //private var mRequestCode = 0x1  //权限请求码,因为本应用只需要一次请求，所以省去传参和回调里的判断
        val permissions = arrayOf( Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION)
        for (permission in permissions) {
            if(ContextCompat.checkSelfPermission(application, permission) != PackageManager.PERMISSION_GRANTED)
                ActivityCompat.requestPermissions(this@MainActivity, permissions, 0x1)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        //本应用只有一次请求，requestCode为0x1
        for (Result in grantResults) {
            if (Result != PackageManager.PERMISSION_GRANTED)
            {
                alert("蓝牙模块需要您授予权限才能工作！") {
                    positiveButton("获取权限") { initPermission() }
                    negativeButton("退出应用") { exitProcess(0) }
                }.show()
                break
            }
        }
    }

    private fun btSwitch(flag : Int)
    {
        doAsync {
            //打开/关闭蓝牙，阻塞进程
            when(flag)
            {
                1 -> {
                    BleManager.getInstance().enableBluetooth()
                    while(!BleManager.getInstance().isBlueEnable) {}
                }
                0 -> {
                    BleManager.getInstance().disableBluetooth()
                    while(BleManager.getInstance().isBlueEnable) {}
                }
            }
            uiThread { tv_status.text = if(BleManager.getInstance().isBlueEnable) "开启" else "关闭" }
        }
    }

    @SuppressLint("SetTextI18n")    //忽略警告
    private fun startScan(scanRuleConfig : BleScanRuleConfig)
    {
        BleManager.getInstance().initScanRule(scanRuleConfig) //注册扫描参数
        BleManager.getInstance().scan(object : BleScanCallback() {
            //本次扫描动作是否开启成功
            override fun onScanStarted(success: Boolean) {
                if(success)
                {
                    tv_scanresult.text = "Scanning......"
                    toast("开始扫描！")
                }
                else toast("正在扫描中！请稍后再试！")
            }
            //扫描过程中的所有过滤后的结果回调
            override fun onScanning(bleDevice: BleDevice) {
                tv_scanresult.text = "${tv_scanresult.text}\n${bleDevice.name}\n${bleDevice.mac}"
                if(mbleDevice == null) mbleDevice = bleDevice
            }
            //本次扫描时段内所有被扫描且过滤后的设备集合
            override fun onScanFinished(scanResultList: List<BleDevice>) {
                tv_scanresult.text = "扫描结束！找到" + scanResultList.size.toString() + "个设备。"
                if(mbleDevice == null) tv_scanresult.text = "${tv_scanresult.text}\n未发现智能衣柜！"
                for(device in scanResultList)
                    tv_scanresult.text = "${tv_scanresult.text}\n${device.name}\n${device.mac}"
            }
        })
    }

    @SuppressLint("SetTextI18n")    //忽略警告
    private fun connect()
    {
        //根据mac地址连接设备
        BleManager.getInstance().connect(mbleDevice, object : BleGattCallback() {
            override fun onStartConnect() {
                tv_connectlog.text = tv_connectlog.text.toString() + "\n开始进行连接"
            }
            override fun onConnectFail(bleDevice: BleDevice, exception: BleException) {
                tv_connectlog.text = tv_connectlog.text.toString() + "\n连接失败......"
            }
            override fun onConnectSuccess(bleDevice: BleDevice, gatt: BluetoothGatt, status: Int) {
                tv_connectlog.text = tv_connectlog.text.toString() + "\n连接成功   $status"
                //status为0连接成功
            }
            //连接断开，特指连接后再断开的情况。
            override fun onDisConnected(
                isActiveDisConnected: Boolean,
                bleDevice: BleDevice,
                gatt: BluetoothGatt,
                status: Int
            ) {
                tv_connectlog.text = tv_connectlog.text.toString() + "\n连上了又断了"
                toast("wdnmd")
            }
        })
    }

}
