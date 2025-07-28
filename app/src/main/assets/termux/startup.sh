#!/system/bin/sh
# Termux Environment Startup Script - 喵～
# 这个脚本负责初始化和启动Termux环境 AWA

# 设置基础变量
TERMUX_PREFIX="/data/data/cn.lemwoodfrp/termux"
SCRIPT_DIR="$(dirname "$0")"

# 加载环境配置 qwq
if [ -f "$SCRIPT_DIR/environment.sh" ]; then
    . "$SCRIPT_DIR/environment.sh"
    echo "Termux环境配置已加载 喵～"
else
    echo "警告: 找不到环境配置文件 qwq"
fi

# 创建必要的目录结构
create_directories() {
    echo "创建Termux目录结构中... AWA"
    mkdir -p "$TERMUX_HOME"
    mkdir -p "$TMPDIR"
    mkdir -p "$FRP_HOME"
    mkdir -p "$FRP_CONFIG_DIR"
    mkdir -p "$FRP_LOG_DIR"
    echo "目录结构创建完成 喵～"
}

# 设置权限
setup_permissions() {
    echo "设置文件权限中... qwq"
    # 为bin目录下的所有文件设置执行权限
    if [ -d "$TERMUX_PREFIX/bin" ]; then
        chmod -R 755 "$TERMUX_PREFIX/bin"
    fi
    echo "权限设置完成 AWA"
}

# 检查环境
check_environment() {
    echo "检查Termux环境状态... 喵～"
    
    if [ ! -d "$TERMUX_PREFIX" ]; then
        echo "错误: Termux前缀目录不存在 qwq"
        return 1
    fi
    
    if [ ! -d "$TERMUX_PREFIX/bin" ]; then
        echo "警告: bin目录不存在 AWA"
    fi
    
    echo "环境检查完成 喵～"
    return 0
}

# 主函数
main() {
    echo "=== Termux环境启动脚本 ==="
    echo "开始初始化Termux环境... qwq"
    
    create_directories
    setup_permissions
    
    if check_environment; then
        echo "Termux环境初始化成功! AWA"
        echo "环境前缀: $TERMUX_PREFIX"
        echo "用户目录: $TERMUX_HOME"
        echo "临时目录: $TMPDIR"
        echo "准备就绪 喵～"
        return 0
    else
        echo "Termux环境初始化失败 qwq"
        return 1
    fi
}

# 如果直接运行此脚本，执行主函数
if [ "${0##*/}" = "startup.sh" ]; then
    main "$@"
fi