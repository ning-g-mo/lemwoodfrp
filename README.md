# 柠枺frp (LemwoodFRP)

一个基于Android的FRP客户端和服务端管理应用，支持frpc和frps的多实例运行管理。

## 功能特性

### 🚀 核心功能
- **FRP客户端管理**: 支持多个frpc实例同时运行
- **FRP服务端管理**: 支持多个frps实例同时运行
- **配置管理**: 简单易用的配置界面，支持导入导出
- **自动启动**: 开机自动启动指定的FRP配置
- **状态监控**: 实时显示FRP进程运行状态

### 📱 用户体验
- **Material Design 3**: 现代化的UI设计
- **深色模式**: 支持系统深色模式
- **电池优化**: 智能处理Android电池优化策略
- **通知管理**: 前台服务通知，确保服务稳定运行

### 🔄 版本管理
- **自动更新检查**: 从GitHub API获取最新版本信息
- **公告系统**: 从GitHub仓库获取重要公告
- **发布管理**: 完整的CI/CD流程，自动构建发布

## 技术栈

- **开发语言**: Kotlin
- **UI框架**: Jetpack Compose + Material Design 3
- **架构模式**: MVVM
- **网络请求**: Retrofit2 + OkHttp3
- **JSON解析**: Gson
- **异步处理**: Kotlin Coroutines
- **生命周期**: Android Architecture Components

## 系统要求

- **最低版本**: Android 7.1 (API 25)
- **目标版本**: Android 14 (API 34)
- **权限要求**: 
  - 网络访问
  - 前台服务
  - 开机启动
  - 电池优化白名单
  - 文件读写

## 安装使用

### 从Release下载
1. 前往 [Releases](https://github.com/ning-g-mo/lemwoodfrp/releases) 页面
2. 下载最新版本的APK文件
3. 在Android设备上安装APK

### 从源码构建
```bash
# 克隆仓库
git clone https://github.com/ning-g-mo/lemwoodfrp.git
cd lemwoodfrp

# 构建Debug版本
./gradlew assembleDebug

# 构建Release版本
./gradlew assembleRelease
```

## 使用说明

### 1. 添加FRP配置
- 打开应用，选择"客户端"或"服务端"标签
- 点击"+"按钮添加新配置
- 填写服务器地址、端口等信息
- 保存配置

### 2. 启动FRP服务
- 在配置列表中找到要启动的配置
- 点击开关按钮启动/停止服务
- 查看运行状态

### 3. 设置自动启动
- 进入"设置"页面
- 开启"开机自动启动"
- 在配置中勾选需要自动启动的项目

### 4. 电池优化设置
- 进入"设置"页面
- 点击"禁用电池优化"
- 在系统设置中将应用加入白名单

## 开发规范

### 项目结构
```
lemwoodfrp/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/cn/lemwoodfrp/
│   │   │   │   ├── ui/          # UI相关
│   │   │   │   ├── service/     # FRP服务
│   │   │   │   ├── utils/       # 工具类
│   │   │   │   ├── model/       # 数据模型
│   │   │   │   └── network/     # 网络请求
│   │   │   ├── res/             # 资源文件
│   │   │   └── AndroidManifest.xml
│   │   └── test/
│   └── build.gradle
├── .github/workflows/           # CI/CD配置
└── build.gradle
```

### 代码规范
- 使用Kotlin作为主要开发语言
- 遵循Android官方代码规范
- 使用Material Design 3组件
- 采用MVVM架构模式

## 贡献指南

1. Fork 本仓库
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 开启 Pull Request

## 许可证

本项目采用 MIT 许可证 - 查看 [LICENSE](LICENSE) 文件了解详情

## 联系方式

- **开发者**: 柠枺 (ning-g-mo)
- **GitHub**: [https://github.com/ning-g-mo](https://github.com/ning-g-mo)
- **项目地址**: [https://github.com/ning-g-mo/lemwoodfrp](https://github.com/ning-g-mo/lemwoodfrp)

## 更新日志

### v1.0.0 (即将发布)
- 🎉 首次发布
- ✨ 支持FRP客户端和服务端管理
- ✨ Material Design 3 UI
- ✨ 自动启动功能
- ✨ 版本检查和更新
- ✨ 电池优化处理

## 致谢

感谢以下开源项目：
- [FRP](https://github.com/fatedier/frp) - 快速反向代理
- [Android Jetpack](https://developer.android.com/jetpack) - Android开发组件
- [Material Design](https://material.io/) - 设计系统