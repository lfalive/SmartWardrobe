![![GitHub release](https://img.shields.io/github/release/lfalive/Smart-wardrobe.svg) ](https://img.shields.io/badge/release-1.3-blue.svg)  

一个简单的Android应用，通过蓝牙和单片机进行通信。  
学习了一些Kotlin基础后，直接上手蓝牙开发，之前接触的是经典蓝牙，因为资料太少，转战BLE。  

## 使用框架
[Anko](https://github.com/Kotlin/anko)  
[FastBle](https://github.com/Jasonchenlijian/FastBle)  

## 下载
[IntWardrobeV1.3.apk](https://raw.githubusercontent.com/lfalive/SmartWardrobe/master/app/release/SmartWardrobe_v1.3_08-24_release.apk?token=AI5MP7SZVJR5F6FU3QFBL4S5NIIRC)

## 功能

### V1.0 2019.7.15
* 对手机蓝牙的开关控制
* 针对Android6.0及以上版本的动态权限申请
* 对附近设备的搜索显示

### V1.1 2019.7.17

* 确定了目标设备的mac地址(刚收到HC-42
* 连接指定设备/查询连接状态/断开连接
* 连接情况的log记录和显示

### V1.2 2019.7.18
* 获取相应Service和Characteristic
* 通过BLE协议进行通信，与HC-42模块互发消息，即时接收

### V1.3 2019.8.24
* 文件遍历和文本文件和图片文件的IO

## 学到了
* 协程（为了优化资源、异步处理任务
* 动态权限的申请（大坑，调了很久
* Anko提供的Toast和Alert等（很好用
* 指定jdk版本以解决一些问题
* 使用@SuppressLint()忽略了一类警告（关于String的，暂时不想管
* Byte[]、HexString、String的相互转换和处理
* 用Edit_text控件实现Spinner
* kotlin.io库和java.io库的使用
* Bitmap、Byte[]、图片文件的相互转换