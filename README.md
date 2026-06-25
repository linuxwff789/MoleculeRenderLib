# MoleculeRenderLib

MoleculeRenderLib 是一个 Android 分子结构渲染库，用于解析 `moldraw` JSON 数据并在 Jetpack Compose 画布中绘制二维分子结构。它可以被 ChemELN、MoleculeDrawer 或其他 Android 化学信息应用集成，用于结构式预览、实验记录展示和化学图形渲染。

## 主要能力

- `moldraw` JSON 解析：读取原子、化学键、标注和渲染参数。
- 二维结构渲染：绘制单键、双键、三键、芳香键和立体键。
- 原子显示：支持常见元素、碳点、杂原子样式和功能团标签。
- 标注渲染：支持文本、箭头等画布注释。
- Compose 集成：面向 Jetpack Compose 的 Android UI 场景。
- 轻量集成：作为 Android Library 模块被其他项目依赖。

## 项目结构

```text
.
├── MoleculeRenderLib/   # Android Library 模块
├── gradle/              # Gradle Wrapper 配置
├── build.gradle.kts     # 根 Gradle 配置
└── settings.gradle.kts  # 工程模块配置
```

## 构建方式

环境要求：

- JDK 17
- Android SDK
- Gradle Wrapper（仓库已内置）

构建 Debug AAR：

```bash
./gradlew :MoleculeRenderLib:assembleDebug
```

构建产物位置：

```text
MoleculeRenderLib/build/outputs/aar/MoleculeRenderLib-debug.aar
```

如果仓库中包含 `release/MoleculeRenderLib-debug.aar`，该文件是已构建好的 Debug AAR，便于其他 Android 项目快速集成测试。

## moldraw JSON 简介

库使用 `moldraw` JSON 作为输入格式，核心数据包括：

- `atoms`：原子 id、坐标、元素、芳香性、手性、功能团标签。
- `bonds`：键 id、连接原子、键类型。
- `annotations`：文字、箭头等辅助标注。
- 渲染参数：键长、线宽、字体大小、芳香环样式等。

## 与其他仓库的关系

MoleculeRenderLib 已从原始 monorepo 中拆分为独立仓库。相关项目：

- ChemELN：化学电子实验记录本，使用本库展示结构式。
- MoleculeDrawer：分子结构编辑器，可生成兼容的 `moldraw` JSON。
- moleculedrawer-monorepo：历史总仓库备份。

## 版权

Copyright (c) 2026 linuxwff789 and MoleculeRenderLib contributors.

本项目代码和资源的使用、复制、修改与分发须遵循本仓库 `LICENSE` 文件。第三方依赖仍遵循其各自许可证。
