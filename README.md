# 📋 SyncClipboard Android

> 基于SyncClipboard api开发的安卓版本,使用AI coding娱乐的副产物

使用[SyncClipboard](https://github.com/Jeric-X/SyncClipboard/) 作为服务端

<img src="https://github.com/user-attachments/assets/80ef734b-5df7-4751-aa8a-a8428d3b4027" width="320px">
<img src="https://github.com/user-attachments/assets/d5db3ca1-441b-4cf8-be77-d8f71550b5a2" width="320px">


## ✨ 主要功能

### 🔄 剪贴板同步
- **剪贴板实时同步**
- **支持文本和图片**
- **可后台自启**

## 🚀 快速开始

### 第一次使用

1. **安装应用**: [发行版](https://github.com/jacksen168sub/SyncClipboard-Android/releases) 下载APK直接安装
3. **授权权限**: 跟着提示给权限就行
2. **配置服务器**: 填入你的SyncClipboard服务器地址
4. **开始同步**: 点击右下角开始按钮，然后试试复制点什么

### 服务端搭建

如果你还没有服务端，请先看看它 [SyncClipboard](https://github.com/Jeric-X/SyncClipboard) ,Windows客户端自带服务器

## 🛠️ 技术栈

- **Jetpack Compose**
- **MVVM + Clean Architecture**
- **Kotlin协程**
- **Room + DataStore**
- **Retrofit**

## 📱 支持设备

- **系统要求**: Android 9.0 及以上
- **Redmi K50**: Android 12 测试可用
- **Vivo Y5S**: Android 9 测试可用

## 🔧 开发相关

### 想要自己编译？

详细内容请查看 [BUILD.md](https://github.com/jacksen168sub/SyncClipboard-Android/blob/main/BUILD.md)

```bash
# 克隆代码
git clone https://github.com/jacksen168sub/SyncClipboard-Android.git
cd SyncClipboard-Android

# 先创建个签名&密钥
./generate-keystore.bat

# 根目录创建 local.properties 输入SDK路径
sdk.dir=C\:\\Users\\<自己的用户名>\\AppData\\Local\\Android\\Sdk

# 用Android Studio打开，或者命令行编译
./gradlew assembleDebug
```

### 注意事项

1. 由于安卓系统限制,在安卓10及以上的系统应用无法在后台读取剪贴板,但可以使用基于Root权限的工具(Magisk/Xposed)解除应用后台读取剪贴版的权限,如[Riru-ClipboardWhitelist](https://github.com/Kr328/Riru-ClipboardWhitelist) / [Clipboard Whitelist](https://modules.lsposed.org/module/io.github.tehcneko.clipboardwhitelist)。由于在安卓13及以上的系统应用必须由用户手动授权才被允许访问系统日志(剪贴板),也可以使用Xposed自动为应用授权访问系统日志的权限,如[DisableLogRequest/禁用日志访问请求](https://github.com/QueallyTech/DisableLogRequest) ,推荐搭配[Clipboard Whitelist](https://modules.lsposed.org/module/io.github.tehcneko.clipboardwhitelist)使用

### 项目结构

```
app/src/main/java/com/jacksen168/syncclipboard/
├── data/           # 数据层：API、数据库、Repository
├── presentation/   # UI层：界面、ViewModel、导航
├── service/        # 服务层：剪贴板监听、后台同步
├── receiver/       # 广播接收器：开机自启等
├── util/          # 工具类：权限管理等
└── work/          # 后台任务：定时同步
```

### 开发环境
- Android Studio最新版
- JDK 11+
- 一颗想让同步更方便的心❤️

## 📜 开源协议

MIT License - 随便用
