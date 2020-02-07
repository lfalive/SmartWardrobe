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
import org.jetbrains.anko.alert
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.selector
import org.jetbrains.anko.toast
import java.io.File
import java.io.OutputStream
import kotlin.system.exitProcess


/*
 *  @project:   SmartWardrobe
 *  @module:    app
 *  @创建者:   Lfalive
 *  @创建时间: 2019/7/14
*/

data class Item(var id: Int, var type: String, var img: String)

class MainActivity : AppCompatActivity() {

    private var mBleDevice: BleDevice? = null   //待连接的设备
    private var mgatt: BluetoothGatt? = null    //GATT协议
    private var mUuidService: String? = null    //服务
    private var mUuidChara: String? = null  //特征
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
        initPermission()
        mpath = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS).toString() + "/"
        refreshSpinner()

        //连接设备
        btn_connect.setOnClickListener {
            doAsync {
                openBLE()
                scan()
            }
        }

        //连接状态
        btn_connectstate.setOnClickListener {
            toast(if (BleManager.getInstance().isConnected(mBleDevice)) "OK!" else "NOT OK!")
        }

        //断开连接
        btn_disconnect.setOnClickListener {
            if (BleManager.getInstance().isConnected(mBleDevice)) {
                BleManager.getInstance().disconnect(mBleDevice)
                mBleDevice = null
                mgatt = null
                mUuidService = null
                mUuidChara = null
            } else toast("暂无设备连接！")
        }

        //发送消息
        btn_send.setOnClickListener {
            if (msg.text.toString() == "") toast("消息为空！")
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
                tv_data.text = "${tv_data.text}共有${mitems.size}条数据\n"
                for (item in mitems) {
                    tv_data.text = "${tv_data.text}衣柜格子编号为${item.id}\n"
                    tv_data.text = "${tv_data.text}衣物类别为${item.type}\n"
                    tv_data.text = "${tv_data.text}衣物图片文件名为${item.img}\n"
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
        fileTree.maxDepth(1)    //需遍历的目录层级为1，即无需检查子目录
            .filter { it.isFile }   //只挑选文件，不处理文件夹
            .filter { it.extension in listOf("png", "jpg") }    //选择扩展名为png和jpg的图片文件
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
        //private var mRequestCode = 0x1 //权限请求码，因为本应用只需要一次请求，所以省去传参和回调里的判断
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
    private fun openBLE() {
        BleManager.getInstance().enableBluetooth()
        while (!BleManager.getInstance().isBlueEnable) {
        }
    }

    @SuppressLint("SetTextI18n")    //忽略警告
    private fun scan() {
        mBleDevice = null
        BleManager.getInstance().initScanRule(
            BleScanRuleConfig.Builder()
                .setDeviceMac("EC:79:DB:5D:AB:A1")  // 只扫描指定mac的设备
                .setScanTimeOut(5000)   // 扫描超时时间，可选，默认10秒；小于等于0表示不限制扫描时间
                .build()
        )
        BleManager.getInstance().scan(object : BleScanCallback() {
            //本次扫描动作是否开启成功
            override fun onScanStarted(success: Boolean) {
                if (success) {
                    toast("开始扫描！")
                } else toast("请稍后再试！")
            }

            //扫描过程中的所有过滤后的结果回调
            override fun onScanning(bleDevice: BleDevice) {
            }

            //本次扫描时段内所有被扫描且过滤后的设备集合
            override fun onScanFinished(scanResultList: List<BleDevice>) {
                if (scanResultList.isEmpty()) toast("未发现设备！")
                else {
                    mBleDevice = scanResultList[0]
                    connect()
                }
            }
        })
    }

    @SuppressLint("SetTextI18n")    //忽略警告
    private fun connect() {
        mgatt = null
        BleManager.getInstance().connect(mBleDevice, object : BleGattCallback() {
            override fun onStartConnect() {
                toast("开始连接")
            }

            override fun onConnectFail(bleDevice: BleDevice, exception: BleException) {
                toast("连接失败")
            }

            override fun onConnectSuccess(bleDevice: BleDevice, gatt: BluetoothGatt, status: Int) {
                toast("连接成功")
                mgatt = gatt
                openNotify()
                //status为0连接成功
            }

            //连接断开，特指连接后再断开的情况。
            override fun onDisConnected(
                isActiveDisConnected: Boolean,
                bleDevice: BleDevice,
                gatt: BluetoothGatt,
                status: Int
            ) {
                if (isActiveDisConnected) toast("断开连接！")
                else toast("连上了又断了")
            }
        })
    }

    @SuppressLint("SetTextI18n")    //忽略警告
    private fun openNotify() {
        val serviceList = mgatt?.services
        if (serviceList.isNullOrEmpty()) {
            toast("服务出错！")
            return
        }
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
                    toast("Notify成功！")
                }

                // 打开通知操作失败
                override fun onNotifyFailure(exception: BleException) {
                    toast("Notify失败！")
                }

                // 打开通知后，设备发过来的数据将在这里出现
                override fun onCharacteristicChanged(data: ByteArray) {
                    tv_connectlog.text = "${tv_connectlog.text}${String(data)}\n"
                }
            })
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
