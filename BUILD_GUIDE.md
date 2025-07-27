# 柠枺frp (LemwoodFRP) 项目构建指南

## 项目概述
柠枺frp是一个基于Android平台的FRP（Fast Reverse Proxy）管理应用，支持frpc和frps的多实例管理。

## 项目特性
- ✅ Material Design 3 UI设计
- ✅ 支持frpc和frps多实例运行
- ✅ 配置文件管理
- ✅ 开机自启功能
- ✅ 电池优化处理
- ✅ GitHub版本检查和更新
- ✅ 前台服务支持
- ✅ 深色模式支持

## 技术栈
- **开发语言**: Kotlin
- **UI框架**: Jetpack Compose + Material Design 3
- **架构模式**: MVVM
- **网络请求**: Retrofit2 + OkHttp3
- **JSON解析**: Gson
- **异步处理**: Kotlin Coroutines
- **构建工具**: Gradle

## 项目结构
```
lemwoodfrp/
├── app/
│   ├── src/main/
│   │   ├── java/cn/lemwoodfrp/
│   │   │   ├── ui/              # UI界面
│   │   │   ├── service/         # FRP服务
│   │   │   ├── utils/           # 工具类
│   │   │   ├── model/           # 数据模型
│   │   │   ├── network/         # 网络请求
│   │   │   └── receiver/        # 广播接收器
│   │   ├── res/                 # 资源文件
│   │   └── AndroidManifest.xml
│   └── build.gradle
├── .github/workflows/           # CI/CD配置
└── gradle/                      # Gradle配置
```

## 已完成的功能模块

### 1. 核心服务
- ✅ `FRPService.kt` - FRP进程管理服务
- ✅ `BootReceiver.kt` - 开机自启接收器
- ✅ `LemwoodFRPApplication.kt` - 应用程序类

### 2. UI界面
- ✅ `MainActivity.kt` - 主活动
- ✅ `MainScreen.kt` - 主界面
- ✅ `FRPClientScreen.kt` - 客户端管理界面
- ✅ `FRPServerScreen.kt` - 服务端管理界面
- ✅ `SettingsScreen.kt` - 设置界面
- ✅ `AboutScreen.kt` - 关于界面
- ✅ `FRPConfigCard.kt` - 配置卡片组件
- ✅ `AddConfigDialog.kt` - 添加配置对话框
- ✅ `UpdateDialog.kt` - 更新对话框

### 3. 工具类
- ✅ `ConfigManager.kt` - 配置管理器
- ✅ `BatteryOptimizationUtils.kt` - 电池优化工具
- ✅ `NetworkManager.kt` - 网络请求管理器

### 4. 数据模型
- ✅ `FRPConfig.kt` - FRP配置数据模型
- ✅ `GitHubRelease.kt` - GitHub发布信息模型

### 5. 主题和资源
- ✅ Material Design 3 主题配置
- ✅ 深色模式支持
- ✅ 应用图标和资源文件
- ✅ 多语言字符串资源

### 6. 构建和部署
- ✅ Gradle构建配置
- ✅ GitHub Actions CI/CD
- ✅ ProGuard混淆规则
- ✅ 应用签名配置

## 构建说明

### 环境要求
- Android Studio Arctic Fox 或更高版本
- JDK 8 或更高版本
- Android SDK API 34
- Gradle 8.4

### 构建步骤
1. 克隆项目到本地
2. 使用Android Studio打开项目
3. 等待Gradle同步完成
4. 运行 `./gradlew assembleDebug` 构建Debug版本
5. 运行 `./gradlew assembleRelease` 构建Release版本

### 首次构建注意事项
- 首次构建会下载大量依赖，请确保网络连接稳定
- 构建过程可能需要5-10分钟，请耐心等待
- 如遇到网络问题，可以配置国内镜像源

## 开发者信息
- **开发者**: 柠枺 (ning-g-mo)
- **项目地址**: https://github.com/ning-g-mo/lemwoodfrp
- **开源协议**: MIT License

## 下一步开发计划
1. 完善FRP二进制文件集成
2. 添加更多配置选项
3. 优化UI交互体验
4. 添加日志查看功能
5. 支持更多FRP功能

---
*项目已基本完成，可以进行构建和测试。*