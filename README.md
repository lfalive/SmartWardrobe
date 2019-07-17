![![GitHub release](https://img.shields.io/github/release/lfalive/Smart-wardrobe.svg) ](https://img.shields.io/badge/release-1.0-blue.svg)  

一个简单的Android应用，通过蓝牙和单片机进行通信。  
学习了一些Kotlin基础后，直接上手蓝牙开发，之前接触的是经典蓝牙，因为资料太少，转战BLE。  

## 使用框架
[Anko](https://github.com/Kotlin/anko)  
[FastBle](https://github.com/Jasonchenlijian/FastBle)  

## V1.0 2019.7.15
[IntWardrobeV1.0.apk](https://raw.githubusercontent.com/lfalive/Smart-wardrobe/master/app/release/IntWardrobe_v1.0_07-15_release.apk?token=AI5MP7XEYQHQAEYRE3BLPTK5GWDGW)  

* 对手机蓝牙的开关控制
* 针对Android6.0及以上版本的动态权限申请
* 对附近设备的搜索显示

用到了：
* 协程（为了优化资源）
* 动态权限的申请（大坑，调了很久）
* Anko提供的Toast和Alert（很好用）

## V1.1 2019.7.17

[IntWardrobeV1.0.apk](https://raw.githubusercontent.com/lfalive/Smart-wardrobe/master/app/release/IntWardrobe_v1.1_07-17_release.apk?token=AI5MP7WBNKEGSTTISUFKCMC5HAVEM)    

* 确定了目标设备的mac地址(刚收到HC-42
* 连接指定设备/查询连接状态/断开连接
* 连接情况的log记录和显示

用到了：
* 指定jdk版本以解决一些问题
* 使用@SuppressLint()忽略了一类警告（关于String的，暂时不想管）
* 将待连接的设备定义为BleDevice类全局变量（本应用目前只考虑与指定设备通信）