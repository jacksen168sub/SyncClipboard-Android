@echo off
chcp 65001 >nul
echo 正在生成 SyncClipboard 应用签名密钥...
echo.

REM 切换到项目根目录
cd /d "%~dp0"

REM 生成签名密钥
keytool -genkey -v ^
  -keystore ./app/syncclipboard-release.jks ^
  -alias syncclipboard ^
  -keyalg RSA ^
  -keysize 2048 ^
  -validity 10000 ^
  -storepass "SyncClipboard@2025" ^
  -keypass "SyncClipboard@2025" ^
  -dname "CN=SyncClipboard, OU=Development, O=SyncClipboard, L=Unknown, S=Unknown, C=CN"

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ✅ 签名密钥生成成功！
    echo 📁 密钥文件：syncclipboard-release.jks
    echo 🔑 密钥别名：syncclipboard
    
    REM 生成 keystore.properties 配置文件
    echo 📝 正在生成 keystore.properties 配置文件...
    (
        echo # 签名密钥配置文件
        echo # 注意：这个文件包含敏感信息，不应该提交到版本控制系统
        echo.
        echo # 密钥库文件路径
        echo storeFile=syncclipboard-release.jks
        echo.
        echo # 密钥库密码
        echo storePassword=SyncClipboard@2025
        echo.
        echo # 密钥别名
        echo keyAlias=syncclipboard
        echo.
        echo # 密钥密码
        echo keyPassword=SyncClipboard@2025
    ) > keystore.properties
    
    if exist keystore.properties (
        echo ✅ keystore.properties 配置文件生成成功！
    ) else (
        echo ❌ keystore.properties 配置文件生成失败！
    )
    
    echo.
    echo ⚠️  重要提醒：
    echo   - 请妥善保管 syncclipboard-release.jks 文件
    echo   - 请妥善保管 keystore.properties 文件
    echo   - 这些文件丢失将无法更新应用！
    echo   - keystore.properties 已自动配置完成
    echo.
) else (
    echo.
    echo ❌ 签名密钥生成失败！
    echo 请检查是否安装了 Java JDK
)

pause