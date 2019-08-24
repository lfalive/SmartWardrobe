package com.huster.smartwardrobe

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Environment
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.clj.fastble.BleManager
import com.clj.fastble.callback.BleGattCallback
import com.clj.fastble.callback.BleNotifyCallback
import com.clj.fastble.callback.BleScanCallback
import com.clj.fastble.callback.BleWriteCallback
import com.clj.fastble.data.BleDevice
import com.clj.fastble.exception.BleException
import com.clj.fastble.scan.BleScanRuleConfig
import kotlinx.android.synthetic.main.activity_main.*
import org.jetbrains.anko.*
import java.io.File
import java.io.OutputStream
import kotlin.system.exitProcess

/*
 *  @项目名:   SmartWardrobe
 *  @project:   SmartWardrobe
 *  @module:    app
 *  @创建者:   Lfalive
 *  @创建时间: 2019/7/14
*/

data class Item(var id: Int, var type: String, var img: String)

class MainActivity : AppCompatActivity() {

    private var mBleDevice: BleDevice? = null   //待连接的设备
    private var mgatt: BluetoothGatt? = null    //GATT协议
    private var mUuidService: String? = null   //服务
    private var mUuidChara: String? = null //特征
    private var mpath: String = ""  //文件目录
    private var mitems: MutableList<Item> = mutableListOf() //数据list

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
        tv_status.text = if (BleManager.getInstance().isBlueEnable) "开启" else "关闭"
        initPermission()
        mpath = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS).toString() + "/"
        refreshSpinner()

        //蓝牙开关按钮
        btn_openbt.setOnClickListener { btSwitch(1) }
        btn_closebt.setOnClickListener { btSwitch(0) }

        //开始扫描
        btn_startsearch.setOnClickListener {
            if (BleManager.getInstance().isBlueEnable) {
                //注意这里有扫描参数设置
                startScan(
                    BleScanRuleConfig.Builder().setScanTimeOut(3000).build()
                )
            } else toast("请打开蓝牙！")
        }

        //连接设备
        btn_connect.setOnClickListener {
            when {
                mBleDevice == null -> tv_connectlog.text =
                    tv_connectlog.text.toString() + "未发现智能衣柜！\n"
                BleManager.getInstance().isConnected(mBleDevice) -> toast("请勿重复连接！")
                else -> connect()
            }
        }

        //连接状态
        btn_connectstate.setOnClickListener {
            if (BleManager.getInstance().isConnected(mBleDevice)) tv_connectlog.text =
                tv_connectlog.text.toString() + "OK!\n"
            else tv_connectlog.text = tv_connectlog.text.toString() + "NOT OK!\n"
        }

        //断开连接
        btn_disconnect.setOnClickListener {
            if (BleManager.getInstance().isConnected(mBleDevice))
                BleManager.getInstance().disconnect(mBleDevice)
            else tv_connectlog.text = tv_connectlog.text.toString() + "暂无设备连接！\n"
        }

        //打开notify
        btn_opennotify.setOnClickListener { openNotify() }

        //发送消息
        btn_send.setOnClickListener {
            if (msg.text.toString() == "") tv_connectlog.text =
                tv_connectlog.text.toString() + "消息为空！\n"
            else sendMsg(msg.text.toString() + "\n")
        }

        //储存数据
        btn_writedata.setOnClickListener {
            when {
                et_itemid.text.isNullOrEmpty() -> toast("请输入衣柜格子编号")
                et_itemtype.text.isNullOrEmpty() -> toast("请输入衣物类别")
                et_itemimg.text.isNullOrEmpty() -> toast("请输入图片文件名")
                else -> {
                    File(mpath + "data.txt")
                        .appendText("${et_itemid.text} ${et_itemtype.text} ${et_itemimg.text}\n")
                    toast("保存成功")
                    et_itemid.text = null
                    et_itemtype.text = null
                    et_itemimg.text = null
                }
            }
        }

        //显示数据
        btn_readdata.setOnClickListener {
            if (File(mpath + "data.txt").exists()) {
                tv_data.text = "数据文件为" + mpath + "data.txt\n"
                readData()
                tv_data.text = tv_data.text.toString() + "共有" + mitems.size.toString() + "条数据\n"
                for (item in mitems) {
                    tv_data.text = tv_data.text.toString() + "衣柜格子编号为" + item.id.toString() + "\n"
                    tv_data.text = tv_data.text.toString() + "衣物类别为" + item.type + "\n"
                    tv_data.text = tv_data.text.toString() + "衣物图片文件名为" + item.img + "\n"
                }
            } else toast("找不到文件！")
        }

        //清空数据
        btn_deletedata.setOnClickListener {
            File(mpath + "data.txt").delete()
            tv_data.text = "已删除" + mpath + "data.txt\n"
        }

    }

    private fun readData() {
        mitems.clear()
        //避免空行
        File(mpath + "data.txt").forEachLine { line ->
            if (line.isNotBlank()) {
                val temp = line.split(" ")
                mitems.add(Item(temp[0].toInt(), temp[1], temp[2]))
            }
        }
    }

    //用Bitmap保存图片
    private fun saveImage(path: String, bitmap: Bitmap) {
        try {
            val file = File(path)
            //outputStream获取文件的输出流对象
            //writer获取文件的Writer对象
            //printWriter获取文件的PrintWriter对象
            val fos: OutputStream = file.outputStream()
            //压缩格式为JPEG图像，压缩质量为100%
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos)
            fos.flush()
            fos.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun refreshSpinner() {
        val fileNames: MutableList<String> = mutableListOf()
        //在该目录下走一圈，得到文件目录树结构
        val fileTree: FileTreeWalk = File(mpath).walk()
        fileTree.maxDepth(1) //需遍历的目录层级为1，即无需检查子目录
            .filter { it.isFile } //只挑选文件，不处理文件夹
            .filter { it.extension in listOf("png", "jpg") } //选择扩展名为png和jpg的图片文件
            .forEach { fileNames.add(it.name) } //循环处理符合条件的文件
        if (fileNames.isNotEmpty()) {
            tv_spinner.text = "点击选择图片"
            tv_spinner.setOnClickListener {
                selector("请选择图片文件", fileNames) { _, i ->
                    tv_spinner.text = fileNames[i]
                    //readBytes读取字节数组形式的文件内容
                    val bytes = File(mpath + fileNames[i]).readBytes()
                    //decodeByteArray从字节数组解析图片
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    //val bitmap = BitmapFactory.decodeFile(mPath + fileNames[i])
                    iv_image.setImageBitmap(bitmap)
                }
            }
        } else {
            tv_spinner.text = "暂无图片"
            iv_image.setImageDrawable(null)
        }
    }

    private fun initPermission() {
        //private var mRequestCode = 0x1  //权限请求码,因为本应用只需要一次请求，所以省去传参和回调里的判断
        val permissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        for (permission in permissions) {
            if (ContextCompat.checkSelfPermission(
                    application,
                    permission
                ) != PackageManager.PERMISSION_GRANTED
            )
                ActivityCompat.requestPermissions(this@MainActivity, permissions, 0x1)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        //本应用只有一次请求，requestCode为0x1
        for (Result in grantResults) {
            if (Result != PackageManager.PERMISSION_GRANTED) {
                alert("蓝牙模块需要您授予权限才能工作！") {
                    positiveButton("获取权限") { initPermission() }
                    negativeButton("退出应用") { exitProcess(0) }
                }.show()
                break
            }
        }
    }

    @Suppress("ControlFlowWithEmptyBody")   //忽略警告
    private fun btSwitch(flag: Int) {
        doAsync {
            //打开/关闭蓝牙，阻塞进程
            when (flag) {
                1 -> {
                    BleManager.getInstance().enableBluetooth()
                    while (!BleManager.getInstance().isBlueEnable) {
                    }
                }
                0 -> {
                    BleManager.getInstance().disableBluetooth()
                    while (BleManager.getInstance().isBlueEnable) {
                    }
                }
            }
            uiThread { tv_status.text = if (BleManager.getInstance().isBlueEnable) "开启" else "关闭" }
        }
    }

    @SuppressLint("SetTextI18n")    //忽略警告
    private fun startScan(scanRuleConfig: BleScanRuleConfig) {
        BleManager.getInstance().initScanRule(scanRuleConfig) //注册扫描参数
        BleManager.getInstance().scan(object : BleScanCallback() {
            //本次扫描动作是否开启成功
            override fun onScanStarted(success: Boolean) {
                if (success) {
                    tv_scanresult.text = "Scanning......"
                    toast("开始扫描！")
                } else toast("正在扫描中！请稍后再试！")
            }

            //扫描过程中的所有过滤后的结果回调
            override fun onScanning(bleDevice: BleDevice) {
                tv_scanresult.text = "${tv_scanresult.text}\n${bleDevice.name}\n${bleDevice.mac}"
                if (bleDevice.mac == "EC:79:DB:5D:AB:A1") mBleDevice = bleDevice
            }

            //本次扫描时段内所有被扫描且过滤后的设备集合
            override fun onScanFinished(scanResultList: List<BleDevice>) {
                tv_scanresult.text = "扫描结束！找到" + scanResultList.size.toString() + "个设备。\n"
                if (mBleDevice == null) tv_scanresult.text = "${tv_scanresult.text}未"
                tv_scanresult.text = "${tv_scanresult.text}发现智能衣柜！"
                for (device in scanResultList)
                    tv_scanresult.text = "${tv_scanresult.text}\n${device.name}\n${device.mac}"
            }
        })
    }

    @SuppressLint("SetTextI18n")    //忽略警告
    private fun connect() {
        BleManager.getInstance().connect(mBleDevice, object : BleGattCallback() {
            override fun onStartConnect() {
                tv_connectlog.text = tv_connectlog.text.toString() + "开始进行连接\n"
            }

            override fun onConnectFail(bleDevice: BleDevice, exception: BleException) {
                tv_connectlog.text = tv_connectlog.text.toString() + "连接失败......\n"
            }

            override fun onConnectSuccess(bleDevice: BleDevice, gatt: BluetoothGatt, status: Int) {
                tv_connectlog.text = tv_connectlog.text.toString() + "连接成功\n"
                mgatt = gatt
                //status为0连接成功
            }

            //连接断开，特指连接后再断开的情况。
            override fun onDisConnected(
                isActiveDisConnected: Boolean,
                bleDevice: BleDevice,
                gatt: BluetoothGatt,
                status: Int
            ) {
                if (isActiveDisConnected) tv_connectlog.text =
                    tv_connectlog.text.toString() + "断开连接！\n"
                else tv_connectlog.text = tv_connectlog.text.toString() + "连上了又断了\n"
            }
        })
    }

    @SuppressLint("SetTextI18n")    //忽略警告
    private fun openNotify() {
        val serviceList = mgatt?.services
        if (!serviceList.isNullOrEmpty()) {
            //获取服务和特征
            for (service in serviceList) {
                mUuidService = service.uuid.toString()
                val characteristicList = service.characteristics
                for (characteristic in characteristicList) {
                    mUuidChara = characteristic.uuid.toString()
                }
            }
            BleManager.getInstance().notify(
                mBleDevice,
                mUuidService,
                mUuidChara,
                object : BleNotifyCallback() {
                    // 打开通知操作成功
                    override fun onNotifySuccess() {
                        tv_connectlog.text = "${tv_connectlog.text}$mUuidService\n$mUuidChara\n"
                        tv_connectlog.text = "${tv_connectlog.text}打开通知操作成功\n"
                    }

                    // 打开通知操作失败
                    override fun onNotifyFailure(exception: BleException) {
                        tv_connectlog.text = "${tv_connectlog.text}$exception\n"
                    }

                    // 打开通知后，设备发过来的数据将在这里出现
                    override fun onCharacteristicChanged(data: ByteArray) {
                        tv_connectlog.text = tv_connectlog.text.toString() + String(data) + "\n"
                    }
                })
        } else {
            toast("请连接设备！")
        }
    }

    @SuppressLint("SetTextI18n")    //忽略警告
    private fun sendMsg(data: String) {
        BleManager.getInstance().write(
            mBleDevice,
            mUuidService,
            mUuidChara,
            data.toByteArray(),
            object : BleWriteCallback() {
                // 发送数据到设备成功（分包发送的情况下，可以通过方法中返回的参数可以查看发送进度）
                override fun onWriteSuccess(current: Int, total: Int, justWrite: ByteArray) {
                    tv_connectlog.text = "${tv_connectlog.text}发送成功！\n"
                }

                // 发送数据到设备失败
                override fun onWriteFailure(exception: BleException) {
                    tv_connectlog.text = "${tv_connectlog.text}$exception\n"
                }
            })
    }

}
