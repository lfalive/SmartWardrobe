![](https://img.shields.io/badge/release-1.0-blue.svg)  
一个简单的Android应用，通过蓝牙和单片机进行通信。  
学习了一些Kotlin基础后，直接上手蓝牙开发，之前接触的是经典蓝牙，因为资料太少，转战BLE。  

## 使用框架
[Anko](https://github.com/Kotlin/anko)  
[FastBle](https://github.com/Jasonchenlijian/FastBle)  

## V1.0 2019.7.15
[IntWardrobeV1.0.apk](https://github.com/lfalive/Smart-wardrobe/raw/master/app/release/IntWardrobe_v1.0_07-14_release.apk)  
* 对手机蓝牙的开关控制
* 针对Android6.0及以上版本的动态权限申请
* 对附近设备的搜索显示

主要用到了：
* 协程（为了优化资源）
* 动态权限的申请（大坑，调了很久）
* Anko提供的Toast和Alert（很好用）