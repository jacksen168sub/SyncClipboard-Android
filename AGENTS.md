# SyncClipboard Android - 项目上下文文档

## 项目概述

SyncClipboard Android 是一个基于 SyncClipboard API 开发的 Android 客户端应用，用于实现跨设备的剪贴板同步功能。该项目采用现代 Android 开发技术栈，支持文本、图片和文件的实时同步，具备后台自启常驻、日志记录、实时推送、智能去重等功能。

### 核心功能
- **剪贴板实时同步**：支持文本、图片和文件的跨设备同步
- **实时推送**：通过 SignalR WebSocket 实现服务端到客户端的实时推送
- **智能去重**：基于内容哈希的智能去重机制，避免重复条目
- **后台运行**：可后台自启常驻，支持多任务页隐藏
- **日志功能**：提供完整的同步日志记录和查看
- **快捷控制**：支持 Quick Settings Tile 快速同步控制
- **图片处理**：支持图片上传、下载和缓存管理
- **内容来源跟踪**：区分本地创建、服务器同步和合并的内容
- **历史记录**：支持获取和管理历史记录

### 技术栈
- **UI 框架**：Jetpack Compose + Material 3
- **架构模式**：MVVM + Clean Architecture
- **异步处理**：Kotlin Coroutines + Flow
- **数据存储**：Room (数据库) + DataStore (配置)
- **网络通信**：Retrofit 2 + OkHttp + WebSocket (SignalR)
- **后台任务**：WorkManager
- **图片加载**：Coil
- **注解处理**：KSP (Kotlin Symbol Processing)
- **实时通信**：自定义 SignalR 协议实现
- **同步控制**：Kotlin Mutex（防止并发冲突）

### 系统要求
- **最低版本**：Android 9.0 (API 28)
- **目标版本**：Android 14 (API 35)
- **编译 SDK**：API 35
- **JDK 版本**：Java 11+
- **当前版本**：1.5.0 (versionCode: 13)

## 项目结构

```
app/src/main/java/com/jacksen168/syncclipboard/
├── data/                          # 数据层
│   ├── api/                       # API 接口定义
│   │   ├── ApiClient.kt          # API 客户端配置（含 GitHub API）
│   │   ├── GitHubApiService.kt   # GitHub API 服务（更新检查）
│   │   └── SyncClipboardApi.kt   # 剪贴板同步 API
│   ├── database/                   # 数据库
│   │   └── ClipboardDatabase.kt  # Room 数据库配置
│   ├── model/                      # 数据模型
│   │   ├── ClipboardItem.kt      # 剪贴板项模型（含智能去重）
│   │   ├── Settings.kt           # 设置模型
│   │   └── UpdateInfo.kt         # 更新信息模型
│   ├── network/                   # 网络通信
│   │   ├── SignalRClient.kt      # SignalR WebSocket 客户端
│   │   └── SignalRMessage.kt     # SignalR 消息模型
│   └── repository/                 # 仓库层
│       ├── ClipboardRepository.kt # 剪贴板数据仓库（含智能去重）
│       ├── SettingsRepository.kt  # 设置数据仓库
│       └── UpdateRepository.kt    # 更新数据仓库
├── presentation/                  # UI 层
│   ├── ClipboardTileActivity.kt  # 透明 Activity（Tile 触发）
│   ├── MainActivity.kt           # 主 Activity
│   ├── component/                 # UI 组件
│   │   ├── ErrorDialog.kt        # 错误对话框
│   │   ├── NoUpdateDialog.kt     # 无更新对话框
│   │   ├── PermissionRequestDialog.kt # 权限请求对话框
│   │   ├── UpdateCheckCard.kt    # 更新检查卡片
│   │   └── UpdateDialog.kt       # 更新对话框
│   ├── navigation/                # 导航
│   │   └── Navigation.kt         # 导航配置
│   ├── screen/                    # 页面
│   │   ├── HomeScreen.kt         # 主页
│   │   ├── LogScreen.kt          # 日志页
│   │   └── SettingsScreen.kt     # 设置页
│   ├── theme/                     # 主题
│   │   ├── Color.kt              # 颜色定义
│   │   ├── Shape.kt              # 形状定义
│   │   └── Type.kt               # 字体定义
│   └── viewmodel/                 # ViewModel
│       ├── MainViewModel.kt      # 主页 ViewModel
│       ├── SettingsViewModel.kt   # 设置页 ViewModel
│       └── UpdateViewModel.kt    # 更新 ViewModel
├── receiver/                      # 广播接收器
│   └── BootReceiver.kt           # 开机自启接收器
├── service/                       # 服务层
│   ├── ClipboardManager.kt       # 剪贴板管理器
│   ├── ClipboardSyncService.kt   # 剪贴板同步服务
│   └── ClipboardSyncTileService.kt # Quick Settings Tile 服务
├── util/                          # 工具类
│   ├── ContentLimiter.kt         # 内容限制器（UI 性能优化）
│   ├── Logger.kt                 # 日志工具
│   └── PermissionManager.kt      # 权限管理器
└── work/                          # 后台任务
    └── SyncWorker.kt             # 同步 Worker
```

## 构建和运行

### 环境要求
- **Android Studio**：最新版（推荐 2024.1+）
- **JDK**：Java 11+（推荐 Java 17）
- **Android SDK**：API 28+
- **Gradle**：项目自带 Gradle Wrapper

### 签名配置

首次构建需要配置签名：

**已有密钥文件**：
1. 将 `.jks` 文件放到 `app/` 目录下
2. 创建或编辑 `keystore.properties` 文件，填入以下内容：
```properties
storeFile=app/syncclipboard-release.jks
storePassword=your_store_password
keyAlias=your_key_alias
keyPassword=your_key_password
```

**生成新密钥**：
- Windows：`generate-keystore.bat`
- Linux：`./generate-keystore.sh`

### 构建命令

**Windows**：
```batch
# 创建 local.properties 文件（首次需要）
# sdk.dir=C:\\Users\\<用户名>\\AppData\\Local\\Android\\Sdk

# Debug 版本
gradlew.bat assembleDebug

# Release 版本（签名）
gradlew.bat assembleRelease

# Release 版本（不签名）
gradlew.bat assembleUnsignedRelease

# 清理构建
gradlew.bat clean
```

**Linux/Mac**：
```bash
# 设置执行权限（首次需要）
chmod +x gradlew.sh
chmod +x generate-keystore.sh

# Debug 版本
./gradlew.sh assembleDebug

# Release 版本（签名）
./gradlew.sh assembleRelease

# Release 版本（不签名）
./gradlew.sh assembleUnsignedRelease

# 清理构建
./gradlew.sh clean
```

### 输出位置

- **Debug**：`app/build/outputs/apk/debug/app-debug.apk`
- **Release**：`app/build/outputs/apk/release/app-release-signed.apk`
- **Unsigned Release**：`app/build/outputs/apk/unsignedRelease/app-unsignedRelease.apk`

### 安装到设备

```bash
adb install app/build/outputs/apk/release/app-release-signed.apk
```

## 开发约定

### 代码风格
- 使用 Kotlin 语言
- 遵循官方 Kotlin 编码规范
- 使用 Jetpack Compose 构建 UI
- 采用 MVVM 架构模式
- 使用 Flow 进行响应式数据流

### 包结构约定
- `data/`：数据层，包含 API、数据库、Repository、网络通信
- `presentation/`：UI 层，包含 Activity、ViewModel、Composable 组件
- `service/`：服务层，包含后台服务
- `receiver/`：广播接收器
- `util/`：工具类
- `work/`：WorkManager 后台任务

### 依赖管理
- 使用 Gradle Version Catalog (`gradle/libs.versions.toml`) 管理依赖版本
- 使用 KSP 处理 Room 注解
- 使用 Kotlin Compose Compiler Plugin

### 构建配置
- **编译 SDK**：API 35
- **最低 SDK**：API 28
- **目标 SDK**：API 35
- **Java 版本**：Java 11
- **Kotlin 版本**：2.0.20
- **Compose Compiler**：1.5.14

### 签名版本
- 启用所有签名版本：v1 + v2 + v3 + v4
- 支持 ABI 拆分：arm64-v8a, armeabi-v7a

### CI/CD
- GitHub Actions 用于自动化构建和发布
- 使用环境变量管理 CI 签名配置
- 支持 PR 检查和 Release 自动发布

## 核心功能实现

### 智能去重机制

项目实现了基于内容哈希的智能去重功能，用于避免剪贴板列表中出现重复条目：

**去重策略**：
- **图片类型**：使用文件内容 SHA256 哈希值进行分组
- **文本类型**：使用 contentHash（content + type + fileName 的 SHA256）进行分组
- **去重规则**：保留最新条目，合并重复条目的状态

**实现位置**：`ClipboardRepository.kt` - `smartDeduplication()` 方法

**UI 性能优化**：
- 使用 `ContentLimiter` 对过大的文本内容进行裁剪
- 避免在 UI 中渲染过长的文本，提升性能

### 同步控制机制

使用 Kotlin Mutex 实现同步锁，防止上传和下载同时进行导致的数据冲突：

**关键特性**：
- 使用 `syncMutex` 保护同步操作
- 防止上传和下载并发执行
- 确保数据一致性

**实现位置**：`ClipboardRepository.kt` - 同步方法中的 `syncMutex.withLock`

### 内容来源跟踪

使用 `ClipboardSource` 枚举跟踪内容的来源：

**来源类型**：
- `LOCAL` - 本地创建的内容
- `REMOTE` - 从服务器同步的内容
- `MERGED` - 合并后的内容

**实现位置**：`ClipboardItem.kt` - `source` 字段

### 数据库迁移

支持数据库版本升级，当前使用 `Migration 1_2`：

**迁移配置**：
```kotlin
.addMigrations(ClipboardDatabase.MIGRATION_1_2)
```

## API 协议说明

### SyncClipboard API v3.1.1+

项目使用 SyncClipboard 服务端 API v3.1.1+ 版本，主要端点：

#### 核心端点
- `GET /SyncClipboard.json` - 获取当前剪贴板内容
- `PUT /SyncClipboard.json` - 更新剪贴板内容
- `GET /file/{filename}` - 下载文件
- `PUT /file/{filename}` - 上传文件
- `HEAD /file/{filename}` - 检查文件是否存在
- `GET /api/history` - 获取历史记录
- `POST /api/history` - 创建历史记录

#### 实时推送
- `WebSocket /SyncClipboardHub` - SignalR 实时推送端点

### 数据格式

#### SyncClipboardRequest (更新剪贴板)
```json
{
  "type": "Text|Image|File",
  "hash": "SHA256哈希值（空字符串让服务端计算）",
  "text": "文本内容",
  "hasData": true,
  "dataName": "文件名",
  "size": 文件大小
}
```

#### SyncClipboardResponse (剪贴板响应)
```json
{
  "type": "Text|Image|File",
  "hash": "SHA256哈希值",
  "text": "文本内容",
  "hasData": true,
  "dataName": "文件名",
  "size": 文件大小
}
```

#### HistoryRecordDto (历史记录)
```json
{
  "hash": "内容哈希",
  "text": "文本内容",
  "type": "Text|Image|File|Group|Unknown|None",
  "createTime": "ISO 8601 时间戳",
  "lastModified": "ISO 8601 时间戳",
  "lastAccessed": "ISO 8601 时间戳",
  "starred": false,
  "pinned": false,
  "size": 0,
  "hasData": false,
  "version": 0,
  "isDeleted": false
}
```

#### Hash 计算机制
- **服务端 Hash**：基于格式化字符串计算（type|text|dataName|size）
- **客户端 Hash**：
  - 图片类型：文件内容的 SHA256
  - 文本类型：content + type + fileName 的 SHA256
- **重要**：由于服务端和客户端 hash 算法不同，上传文件时应发送空 hash 字段，让服务端自行计算和验证

### SignalR 协议实现

项目使用自定义 SignalR 协议实现，通过 WebSocket 与服务端 `/SyncClipboardHub` 端点通信：

**主要功能**：
- 实时接收服务端剪贴板更新推送
- 自动重连机制（5秒延迟）
- 心跳保持连接（15秒间隔）
- 支持不安全 SSL 连接（开发测试）

**关键方法**：
- `start()` - 启动连接
- `stop()` - 停止连接
- `subscribe()` - 订阅剪贴板更新事件
- `receiveClipboardUpdates()` - 接收剪贴板更新 Flow

**实现位置**：`data/network/SignalRClient.kt`

## 数据模型

### ClipboardItem (剪贴板项)

核心数据模型，包含以下字段：

**网络同步字段**：
- `id` - 唯一标识符
- `content` - 内容（文本或图片哈希）
- `type` - 类型（TEXT/IMAGE/FILE）
- `timestamp` - 时间戳
- `deviceName` - 设备名称
- `fileName` - 文件名
- `fileSize` - 文件大小
- `mimeType` - MIME 类型

**本地字段**：
- `isSynced` - 是否已同步
- `localPath` - 本地文件路径
- `createdAt` - 创建时间
- `source` - 内容来源（LOCAL/REMOTE/MERGED）
- `contentHash` - 内容哈希（用于智能去重）
- `lastModified` - 最后修改时间
- `isSyncing` - 是否正在同步
- `uiContent` - UI 显示内容（裁剪后）

**辅助方法**：
- `generateContentHash()` - 生成内容哈希

### 枚举类型

**ClipboardType** - 剪贴板内容类型：
- `TEXT` - 文本
- `IMAGE` - 图片
- `FILE` - 文件

**ClipboardSource** - 内容来源：
- `LOCAL` - 本地创建
- `REMOTE` - 服务器同步
- `MERGED` - 合并内容

**ProfileType** - 历史记录类型：
- `TEXT` - 文本
- `FILE` - 文件
- `IMAGE` - 图片
- `GROUP` - 分组
- `UNKNOWN` - 未知
- `NONE` - 无

## 特殊注意事项

### Android 系统限制
1. **后台剪贴板读取**：Android 10+ 系统限制应用在后台读取剪贴板
   - 解决方案：使用 Magisk/Xposed 模块（如 Clipboard Whitelist）解除限制
   - 推荐搭配：[Clipboard Whitelist](https://modules.lsposed.org/module/io.github.tehcneko.clipboardwhitelist)

2. **日志访问权限**：Android 13+ 需要用户手动授权访问系统日志
   - 解决方案：使用 Xposed 模块（如 DisableLogRequest）自动授权

### 图片上传注意事项
1. **文件命名**：使用原始文件名作为存储和下载标识
2. **Hash 验证**：不发送客户端计算的 hash，让服务端自行验证
3. **文件大小限制**：建议单个文件不超过 10MB
4. **缓存管理**：图片缓存在应用 cache 目录，需定期清理

### 性能优化
1. **UI 内容裁剪**：使用 `ContentLimiter` 对过长的文本进行裁剪
2. **智能去重**：避免重复条目，减少内存占用
3. **Flow 响应式**：使用 Flow 进行数据流处理，提升性能
4. **同步锁**：使用 Mutex 防止并发冲突

### 权限说明
应用需要以下权限：
- **网络权限**：INTERNET, ACCESS_NETWORK_STATE
- **后台运行**：FOREGROUND_SERVICE, FOREGROUND_SERVICE_DATA_SYNC, WAKE_LOCK
- **开机自启**：RECEIVE_BOOT_COMPLETED
- **存储权限**：WRITE_EXTERNAL_STORAGE, READ_EXTERNAL_STORAGE, MANAGE_EXTERNAL_STORAGE
- **通知权限**：POST_NOTIFICATIONS (Android 13+)
- **系统权限**：SYSTEM_ALERT_WINDOW, DISABLE_KEYGUARD, REORDER_TASKS
- **电池优化**：REQUEST_IGNORE_BATTERY_OPTIMIZATIONS

### 服务端要求
- 使用 [SyncClipboard](https://github.com/Jeric-X/SyncClipboard/) 作为服务端
- 支持 API v3.1.1+ 版本
- Windows 客户端自带服务器功能
- 需要 SignalR Hub 端点支持

## 测试

项目配置了基本的测试框架：
- **单元测试**：JUnit 4
- **Android 测试**：AndroidX Test, Espresso
- **UI 测试**：Compose UI Testing

运行测试：
```bash
# 单元测试
./gradlew test

# Android 测试
./gradlew connectedAndroidTest
```

## 相关文档
- [README.md](README.md) - 项目说明和快速开始
- [BUILD.md](BUILD.md) - 详细构建指南
- [LICENSE](LICENSE) - MIT 开源协议

## 开源仓库
- **GitHub**：https://github.com/jacksen168sub/SyncClipboard-Android
- **服务端仓库**：https://github.com/Jeric-X/SyncClipboard