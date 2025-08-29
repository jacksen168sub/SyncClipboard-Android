# 🔨 构建指南

> 一份不太正式的构建指南

## 💻 开发环境准备

### 必须的工具
- **Android Studio**: 用最新版就行，不用太纠结版本号
- **JDK**: Java 11+
- **Android SDK**: API 28+

### 推荐配置
- 内存至少8GB（我猜的）
- 存储留个4GB（依赖下载要吃不少空间,还是我猜的）
- 网速要稳定（下载依赖时不能断网）

## 🚀 快速开始

### 1. 签名配置

- #### 如果你已经有jks文件

1. **放到app/下**: app/syncclipboard-release.jks
2. **更新配置**: 打开 `keystore.properties`，填入实际值

- #### 如果还没有密钥
```bash
generate-keystore.bat  # 一步生成密钥和配置
```

### 2. 构建命令

**Windows:**
```batch
# 根目录创建文件 local.properties 输入SDK路径
sdk.dir=C\:\\Users\\<自己的用户名>\\AppData\\Local\\Android\\Sdk

# Debug版
gradlew.bat assembleDebug

# Release版
gradlew.bat assembleRelease
```

## 🐛 常见问题

### 清理缓存
```bash
gradlew clean
rm -rf .gradle/  # 更彻底的清理
```

## 📦 部署

**APK位置**:
- Debug: `app/build/outputs/apk/debug/`
- Release: `app/build/outputs/apk/release/`

**直接安装**:
```bash
adb install app/build/outputs/apk/release/app-release.apk
```

---

> 📚 有问题就提Issue，看到就回复解决
