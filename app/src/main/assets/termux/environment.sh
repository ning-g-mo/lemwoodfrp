# Termux Environment Configuration - 喵～
# 这个文件定义了Termux环境的基础配置 AWA

# 基础路径配置
export TERMUX_PREFIX="/data/data/cn.lemwoodfrp/termux"
export TERMUX_HOME="$TERMUX_PREFIX/home"
export HOME="$TERMUX_HOME"
export TMPDIR="$TERMUX_PREFIX/tmp"

# PATH配置 - 优先使用Termux的工具 qwq
export PATH="$TERMUX_PREFIX/bin:/system/bin:/system/xbin"

# 库路径配置
export LD_LIBRARY_PATH="$TERMUX_PREFIX/lib:/system/lib:/system/lib64"

# 语言和编码设置
export LANG="en_US.UTF-8"
export LC_ALL="en_US.UTF-8"

# Shell配置
export SHELL="$TERMUX_PREFIX/bin/bash"

# FRP相关配置 AWA
export FRP_HOME="$TERMUX_PREFIX/frp"
export FRP_CONFIG_DIR="$FRP_HOME/config"
export FRP_LOG_DIR="$FRP_HOME/logs"

# 调试模式 - 可以通过这个变量控制日志级别 喵～
export TERMUX_DEBUG="0"