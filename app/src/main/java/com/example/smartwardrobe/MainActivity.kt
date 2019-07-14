package com.example.smartwardrobe

import android.Manifest
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.clj.fastble.callback.BleScanCallback
import com.clj.fastble.data.BleDevice
import com.clj.fastble.BleManager
import com.clj.fastble.scan.BleScanRuleConfig
import kotlinx.android.synthetic.main.activity_main.*
import kotlin.system.exitProcess
import org.jetbrains.anko.*

/*
 *  @项目名：  SmartWardrobe
 *  @创建者:   Lfalive
 *  @创建时间: 2019/7/14
*/

class MainActivity : AppCompatActivity() {

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

    }

    private fun initPermission(){
        //private var mRequestCode = 0x1  //权限请求码,因为本应用只需要一次请求，所以省去传参和回调里的判断
        val permissions = arrayOf( Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION)
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

    private fun startScan(scanRuleConfig : BleScanRuleConfig)
    {
        //注册扫描参数
        BleManager.getInstance().initScanRule(scanRuleConfig)

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
            }
            //本次扫描时段内所有被扫描且过滤后的设备集合
            override fun onScanFinished(scanResultList: List<BleDevice>) {
                tv_scanresult.text = "扫描结束！找到" + scanResultList.size.toString() + "个设备。"
                for(device in scanResultList)
                    tv_scanresult.text = "${tv_scanresult.text}\n${device.name}\n${device.mac}"
            }
        })
    }

}
