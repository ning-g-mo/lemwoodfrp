## 柠枺frp (LemwoodFRP) 开发规范

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
├── gradle/
├── build.gradle
└── settings.gradle
```

### 开发规范

#### 1. 代码规范
- 使用Kotlin作为主要开发语言
- 遵循Android官方代码规范
- 使用Material Design 3组件
- 采用MVVM架构模式

#### 2. 命名规范
- 包名：cn.lemwoodfrp
- Activity：以Activity结尾
- Fragment：以Fragment结尾
- Service：以Service结尾
- 常量：全大写，下划线分隔

#### 3. 功能模块
- **FRP管理**：支持frpc和frps多实例运行
- **版本检查**：从GitHub API获取最新版本
- **公告系统**：从GitHub仓库获取公告信息
- **电池优化**：处理Android电池优化策略
- **配置管理**：FRP配置文件管理

#### 4. 技术栈
- UI框架：Material Design 3
- 网络请求：Retrofit2 + OkHttp3
- JSON解析：Gson
- 生命周期：Android Architecture Components
- 异步处理：Kotlin Coroutines

#### 5. 兼容性
- 最低支持：Android 7.1 (API 25)
- 目标版本：Android 14 (API 34)

#### 6. 构建部署
- 使用GitHub Actions进行CI/CD
- 自动化测试和构建
- 发布到GitHub Releases
- 不要在本地进行构建操作。请使用GitHub Actions进行构建。

### 7. 要求
- 尽量使用mcp工具
- 本地终端为PowerShell
- GitHub ID为ning-g-mo 昵称是柠枺