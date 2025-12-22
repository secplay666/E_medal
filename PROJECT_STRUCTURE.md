# 分析 E_medal 项目结构

本文档详细分析了 `E_medal` Android 项目的目录结构和整体架构。

## 概览

这是一个标准的 Android Gradle 项目，遵循现代 Android 应用的典型结构。项目使用 Git 进行版本控制，并利用 Gradle Wrapper 来确保构建环境的一致性。

## 根目录结构

项目的根目录包含以下关键文件和文件夹：

- **`.idea/`**: Android Studio 的项目配置文件。存储了 IDE 的特定设置，通常不建议手动修改。
- **`app/`**: 项目的核心模块，包含了应用的所有源代码、资源和构建配置。
- **`gradle/`**: 存放 Gradle Wrapper 的相关文件 (`gradle-wrapper.jar`, `gradle-wrapper.properties`)。它保证了项目在任何机器上都使用统一的 Gradle 版本进行构建。
- **`.git/`**: Git 版本控制系统的目录，存储了所有的提交历史和分支信息。
- **`build.gradle`**: 项目级的 Gradle 构建脚本。用于配置适用于项目中所有模块的构建规则，例如定义插件仓库和依赖版本。
- **`settings.gradle`**: Gradle 的设置文件。用于声明项目中包含的所有子模块，例如 `:app` 模块。
- **`gradle.properties`**: 全局 Gradle 配置文件。用于设置项目范围的属性，例如 Gradle 的 JVM 参数或一些特性开关。
- **`gradlew` & `gradlew.bat`**: Gradle Wrapper 的可执行脚本，分别用于在 `*nix` 系统和 Windows 系统上运行 Gradle 命令。
- **`.gitignore`**: Git 的忽略列表文件。定义了哪些文件或目录不应被纳入版本控制（例如，构建生成的文件、本地配置文件等）。
- **`test_images/`**: 根据命名推测，此目录用于存放测试相关的图片资源。

## `app` 模块详解

`app` 模块是应用的实际载体，其内部结构如下：

- **`build.gradle`**: `app` 模块专属的构建脚本。在这里配置应用 ID (`applicationId`)、SDK 版本 (`minSdk`, `targetSdk`)、应用签名以及模块的依赖项。
- **`src/`**: 包含了项目的所有源代码和资源文件。
    - **`main/`**: 主代码集，构成了应用最终发布版本的主要内容。
        - **`java/`** (或 `kotlin/`): 存放应用的 Kotlin 或 Java 源代码，通常会按照包名进行组织。
        - **`res/`**: 存放所有的应用资源。
            - `drawable/`: 图片资源。
            - `layout/`: UI 布局的 XML 文件。
            - `mipmap/`: 不同密度的应用启动图标。
            - `values/`: 各种值资源，如字符串 (`strings.xml`)、颜色 (`colors.xml`)、样式 (`styles.xml`) 等。
        - **`AndroidManifest.xml`**: 应用的清单文件。这是 Android 系统了解应用信息的入口，声明了应用的组件（Activity, Service 等）、所需权限、设备特性等。
    - **`test/`**: 存放本地单元测试代码（Unit Tests），在 JVM 上运行。
    - **`androidTest/`**: 存放仪器测试代码（Instrumented Tests），需要在 Android 设备或模拟器上运行。

## 整体架构

- **构建系统**: 项目采用 **Gradle** 作为自动化构建工具。
- **模块化**: 当前为 **单模块架构**。所有的功能都集中在 `:app` 模块中。随着项目复杂度的增加，未来可以考虑将功能拆分为独立的库模块（Library Modules）以提升代码复用性和编译速度。
- **代码组织**: 遵循标准的 Android 项目结构，便于开发者快速上手和维护。

## 总结

`E_medal` 是一个结构清晰、配置标准的 Android 项目。其核心逻辑和资源都位于 `app` 模块。开发者应主要关注 `app/src/main` 目录下的代码和资源编写，并通过 `app/build.gradle` 文件管理应用配置和依赖。
