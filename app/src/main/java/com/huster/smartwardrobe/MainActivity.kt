package com.huster.smartwardrobe

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.content.DialogInterface
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.view.WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.clj.fastble.BleManager
import com.clj.fastble.callback.*
import com.clj.fastble.data.BleDevice
import com.clj.fastble.exception.BleException
import com.clj.fastble.scan.BleScanRuleConfig
import com.zhy.adapter.recyclerview.CommonAdapter
import com.zhy.adapter.recyclerview.MultiItemTypeAdapter
import com.zhy.adapter.recyclerview.base.ViewHolder
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.left_menu.*
import org.jetbrains.anko.*
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

@Suppress("ControlFlowWithEmptyBody", "DEPRECATION")
@SuppressLint("SetTextI18n")    //忽略警告,getresources
class MainActivity : AppCompatActivity() {

    private var connectDone: Boolean = false    //单次连接完成否
    private var connected: Boolean = false  //当前蓝牙连接状态
    private var mBleDevice: BleDevice? = null   //待连接的设备
    private var mgatt: BluetoothGatt? = null    //GATT协议
    private var mUuidService: String? = null    //服务
    private var mUuidChara: String? = null  //特征
    private lateinit var mpath: String  //文件目录
    private lateinit var mAdapter: CommonAdapter<Bitmap>    //GridView适配器
    private var mitems: MutableList<Item> = mutableListOf() //数据list
    private var mDatas: MutableList<Bitmap> = mutableListOf()
    private var clothes: MutableList<Bitmap> = mutableListOf()
    private var pants: MutableList<Bitmap> = mutableListOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(FLAG_TRANSLUCENT_STATUS, FLAG_TRANSLUCENT_STATUS)   //透明状态栏
        setContentView(R.layout.activity_main)

        //初始化
        initTitle()
        initPermission()
        initSlideMenu()
        initGridView()
        mpath = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS).toString() + "/"
        refreshSpinner()

        //蓝牙模块初始化
        BleManager.getInstance().init(application)
        BleManager.getInstance()
            .enableLog(true)
            .setReConnectCount(1, 5000)
            .setSplitWriteNum(10)
            .setConnectOverTime(10000).operateTimeout = 5000

        //连接设备
        btn_connect.setOnClickListener {
            if (!connected) {   //连接
                connectDone = false
                val progress = mAlert("正在连接……")
                doAsync {
                    openBLE()
                    scan()
                    while (!connectDone) {
                    }
                    uiThread {
                        progress.cancel()
                    }
                }
            } else {    //断开连接
                val progress = mAlert("断开连接……")
                doAsync {
                    BleManager.getInstance().disconnect(mBleDevice)
                    while (BleManager.getInstance().isConnected(mBleDevice)) {
                    }
                    uiThread {
                        progress.cancel()
                        btn_connect.setImageDrawable(
                            ResourcesCompat.getDrawable(resources, R.drawable.icon_disconnected, null)
                        )
                    }
                }
            }
        }

        //存衣
        btn_insert.setOnClickListener { toast("正在开发……") }

        //菜单
        btn_slide.setOnClickListener {
            if (mDrawer.isDrawerOpen(GravityCompat.START)) {
                mDrawer.closeDrawer(GravityCompat.START)
            } else mDrawer.openDrawer(GravityCompat.START)
        }

        //切换分类
        btn_menu1.setOnClickListener { menuClicked(btn_menu1) }
        btn_menu2.setOnClickListener { menuClicked(btn_menu2) }

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

    private fun mAlert(msg: String): DialogInterface {
        return alert {
            isCancelable = false
            customView {
                verticalLayout {
                    progressBar().indeterminateTintList =
                        AppCompatResources.getColorStateList(context, R.color.colorPrimary)
                    textView(msg).textAlignment = View.TEXT_ALIGNMENT_CENTER
                    verticalPadding = dip(16)
                }
            }
        }.show()
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

    private fun initTitle() {
        val resourceId =
            applicationContext.resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) {
            val statusBarHeight = applicationContext.resources.getDimensionPixelSize(resourceId)
            mTitle.layoutParams.height = statusBarHeight + dip(40)
            mTitle.topPadding = statusBarHeight
        }
    }

    private fun initPermission() {
        //private var mRequestCode = 0x1 //权限请求码，因为本应用只需要一次请求，所以省去传参和回调里的判断
        val permissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        for (permission in permissions) {
            if (ContextCompat.checkSelfPermission(application, permission) != PackageManager.PERMISSION_GRANTED)
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

    private fun initSlideMenu() {
        mDrawer.setScrimColor(resources.getColor(R.color.colorGrey))   //蒙层颜色
        mDrawer.addDrawerListener(object : DrawerLayout.DrawerListener {
            override fun onDrawerStateChanged(newState: Int) {
            }

            override fun onDrawerSlide(drawerView: View, slideOffset: Float) {
                mContent.translationX = drawerView.measuredWidth * slideOffset
                btn_slide.rotation = -slideOffset * 180
            }

            override fun onDrawerClosed(drawerView: View) {
            }

            override fun onDrawerOpened(drawerView: View) {
            }
        }
        )
    }

    private fun menuClicked(view: View) {
        btn_menu1.background = ResourcesCompat.getDrawable(resources, R.color.colorPrimary, null)
        btn_menu2.background = ResourcesCompat.getDrawable(resources, R.color.colorPrimary, null)
        view.background = ResourcesCompat.getDrawable(resources, R.color.colorDrakBlue, null)
        mDatas.clear()
        when (view) {
            btn_menu1 -> mDatas.addAll(clothes)
            btn_menu2 -> mDatas.addAll(pants)
        }
        mAdapter.notifyDataSetChanged()
        mDrawer.closeDrawer(GravityCompat.START)
    }

    private fun initGridView() {
        for (i in 1..9) {
            ResourcesCompat.getDrawable(resources, R.drawable.clothes, null)?.toBitmap()?.let { clothes.add(it) }
            ResourcesCompat.getDrawable(resources, R.drawable.pants, null)?.toBitmap()?.let { pants.add(it) }
        }
        rv.setHasFixedSize(true)
        rv.isNestedScrollingEnabled = false //禁止滑动
        rv.layoutManager = GridLayoutManager(this, 2)
        rv.itemAnimator = DefaultItemAnimator()
        mAdapter = object : CommonAdapter<Bitmap>(this, R.layout.cell, mDatas) {
            override fun convert(holder: ViewHolder?, t: Bitmap?, position: Int) {
                holder?.setImageBitmap(R.id.imageview, t)
            }
        }
        mAdapter.setOnItemClickListener(object : MultiItemTypeAdapter.OnItemClickListener {
            override fun onItemClick(
                view: View,
                holder: RecyclerView.ViewHolder,
                position: Int
            ) {
                alert("是否取出该衣服？") {
                    yesButton {
                        mDatas.removeAt(position)
                        rv.adapter?.notifyItemRemoved(position)
                    }
                    cancelButton { }
                }.show()
            }

            override fun onItemLongClick(
                view: View,
                holder: RecyclerView.ViewHolder,
                position: Int
            ): Boolean {
                return false
            }
        })
        rv.adapter = mAdapter
        menuClicked(btn_menu1)
    }

    private fun openBLE() {
        BleManager.getInstance().enableBluetooth()
        while (!BleManager.getInstance().isBlueEnable) {
        }
    }

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
                } else {
                    connectDone = true
                    toast("请稍后再试！")
                }
            }

            //扫描过程中的所有过滤后的结果回调
            override fun onScanning(bleDevice: BleDevice) {
            }

            //本次扫描时段内所有被扫描且过滤后的设备集合
            override fun onScanFinished(scanResultList: List<BleDevice>) {
                if (scanResultList.isEmpty()) {
                    connectDone = true
                    toast("未发现设备！")
                } else {
                    mBleDevice = scanResultList[0]
                    connect()
                }
            }
        })
    }

    private fun connect() {
        mgatt = null
        BleManager.getInstance().connect(mBleDevice, object : BleGattCallback() {
            override fun onStartConnect() {
                toast("开始连接")
            }

            override fun onConnectFail(bleDevice: BleDevice, exception: BleException) {
                connectDone = true
                toast("连接失败")
            }

            override fun onConnectSuccess(bleDevice: BleDevice, gatt: BluetoothGatt, status: Int) {
                connectDone = true
                toast("连接成功")
                mgatt = gatt
                openNotify()
                //status为0连接成功
            }

            //连接断开，特指连接后再断开的情况。
            override fun onDisConnected(
                isActiveDisConnected: Boolean,  //是否主动断开
                bleDevice: BleDevice,
                gatt: BluetoothGatt,
                status: Int
            ) {
                connectDone = true
                toast("连上了又断了")
            }
        })
    }

    private fun openNotify() {
        val serviceList = mgatt?.services
        if (serviceList.isNullOrEmpty()) {
            connectDone = true
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
                    connectDone = true
                    btn_connect.setImageDrawable(ResourcesCompat.getDrawable(resources, R.drawable.icon_connected, null))
                    btn_send.isEnabled = true
                    toast("Notify成功！")
                }

                // 打开通知操作失败
                override fun onNotifyFailure(exception: BleException) {
                    connectDone = true
                    toast("Notify失败！")
                }

                // 打开通知后，设备发过来的数据将在这里出现
                override fun onCharacteristicChanged(data: ByteArray) {
                    tv_connectlog.text = "${tv_connectlog.text}${String(data)}\n"
                }
            })
    }

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
