#!/bin/bash

# 设置颜色输出
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# 项目根目录
PROJECT_DIR="$(pwd)"
# Android SDK 路径
ANDROID_SDK="$ANDROID_HOME"
echo "Android SDK Path: $ANDROID_SDK"
# 模拟器名称
AVD_NAME="e_medal_test_device"
# 模拟器设备类型
DEVICE_TYPE="pixel_4a"
# 系统镜像
# SYSTEM_IMAGE="system-images;android-34;google_apis_playstore;arm64-v8a"
SYSTEM_IMAGE="system-images;android-Baklava;google_apis_ps16k;arm64-v8a"
# APK路径
APK_PATH="$PROJECT_DIR/app/build/outputs/apk/debug/app-debug.apk"

# 检查Android SDK环境变量
if [ -z "$ANDROID_SDK" ]; then
    echo -e "${RED}错误: ANDROID_HOME 环境变量未设置${NC}"
    exit 1
fi

# 显示标题
echo -e "${GREEN}====================================${NC}"
echo -e "${GREEN}    E-Medal 应用一键部署脚本       ${NC}"
echo -e "${GREEN}====================================${NC}"

# 步骤1: 构建应用程序
echo -e "\n${YELLOW}[步骤 1/6] 构建应用程序...${NC}"
cd "$PROJECT_DIR" && ./gradlew assembleDebug

if [ $? -ne 0 ]; then
    echo -e "${RED}构建失败!${NC}"
    exit 1
fi

echo -e "${GREEN}构建成功!${NC}"

# 步骤2: 检查APK是否存在
echo -e "\n${YELLOW}[步骤 2/6] 检查APK文件...${NC}"
if [ ! -f "$APK_PATH" ]; then
    echo -e "${RED}错误: APK文件不存在: $APK_PATH${NC}"
    exit 1
fi

echo -e "${GREEN}APK文件已找到: $APK_PATH${NC}"

# 步骤3: 检查模拟器是否存在
echo -e "\n${YELLOW}[步骤 3/6] 检查模拟器...${NC}"
avd_exists=$("$ANDROID_SDK/cmdline-tools/latest/bin/avdmanager" list avd | grep "$AVD_NAME")

if [ -z "$avd_exists" ]; then
    echo -e "${YELLOW}模拟器 '$AVD_NAME' 不存在, 正在创建...${NC}"
    
    # 检查系统镜像是否已安装
    system_image_exists=$("$ANDROID_SDK/cmdline-tools/latest/bin/sdkmanager" --list | grep "$SYSTEM_IMAGE")
    
    if [ -z "$system_image_exists" ]; then
        echo -e "${YELLOW}系统镜像不存在, 正在下载...${NC}"
        "$ANDROID_SDK/cmdline-tools/latest/bin/sdkmanager" "$SYSTEM_IMAGE"
    fi
    
    # 创建模拟器
    echo "no" | "$ANDROID_SDK/cmdline-tools/latest/bin/avdmanager" create avd \
        -n "$AVD_NAME" \
        -k "$SYSTEM_IMAGE" \
        -d "$DEVICE_TYPE" \
        --sdcard 512M
    
    if [ $? -ne 0 ]; then
        echo -e "${RED}创建模拟器失败!${NC}"
        exit 1
    fi
    
    # 配置模拟器以使用较少的资源
    echo "hw.ramSize=1024" >> ~/.android/avd/${AVD_NAME}.avd/config.ini
    echo "hw.lcd.density=420" >> ~/.android/avd/${AVD_NAME}.avd/config.ini
    echo "disk.dataPartition.size=2048M" >> ~/.android/avd/${AVD_NAME}.avd/config.ini
    
    echo -e "${GREEN}模拟器创建成功!${NC}"
else
    echo -e "${GREEN}模拟器 '$AVD_NAME' 已存在.${NC}"
fi

# 步骤4: 检查模拟器是否已运行
echo -e "\n${YELLOW}[步骤 4/6] 启动模拟器...${NC}"
emulator_running=$(adb devices | grep emulator | wc -l)

if [ "$emulator_running" -eq 0 ]; then
    echo -e "${YELLOW}启动模拟器...${NC}"
    "$ANDROID_SDK/emulator/emulator" -avd "$AVD_NAME" -no-snapshot -no-boot-anim -memory 1024 &
    emulator_pid=$!
    
    # 等待模拟器启动
    echo -e "${YELLOW}等待模拟器启动...${NC}"
    adb wait-for-device
    
    # 等待系统启动完成
    echo -e "${YELLOW}等待系统启动完成...${NC}"
    while [ "$(adb shell getprop sys.boot_completed 2>/dev/null)" != "1" ]; do
        sleep 2
    done
    
    echo -e "${GREEN}模拟器已启动!${NC}"
else
    echo -e "${GREEN}模拟器已经在运行.${NC}"
fi

# 步骤5: 安装APK
echo -e "\n${YELLOW}[步骤 5/6] 安装APK...${NC}"
adb install -r "$APK_PATH"

if [ $? -ne 0 ]; then
    echo -e "${RED}安装APK失败!${NC}"
    exit 1
fi

echo -e "${GREEN}APK安装成功!${NC}"

# 步骤6: 启动应用
echo -e "\n${YELLOW}[步骤 6/6] 启动应用...${NC}"
adb shell am start -n "com.example.t4/.MainActivity"

if [ $? -ne 0 ]; then
    echo -e "${RED}启动应用失败!${NC}"
    exit 1
fi

echo -e "${GREEN}应用已启动!${NC}"

echo -e "\n${GREEN}====================================${NC}"
echo -e "${GREEN}    部署完成!    ${NC}"
echo -e "${GREEN}====================================${NC}"

# 提示如何查看日志
echo -e "\n${YELLOW}提示: 要查看应用日志，请运行:${NC}"
echo -e "adb logcat | grep com.example.t4"
