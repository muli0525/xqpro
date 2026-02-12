# 象棋 Pro - Pikafish引擎驱动的象棋辅助APP

## 项目简介

**象棋 Pro** 是一款集成Pikafish开源象棋引擎的Android辅助应用，支持手动摆棋、引擎分析和悬浮窗提示。

## 核心功能

### Pikafish引擎分析
- 集成Pikafish开源象棋引擎（ARM64原生二进制）
- UCI协议通信，支持深度1-30的搜索
- 实时局面评估和最佳走法推荐
- 支持NNUE神经网络评估（需额外下载权重文件）

### 悬浮窗
- 可自由拖拽移动的分析悬浮窗
- 双击放大/缩小
- 实时显示引擎分析结果（评估值、最佳走法、搜索深度）
- 可在其他象棋APP上方悬浮显示

### 摆棋与走棋
- 手动摆放棋子，支持所有棋子类型
- 从初始局面或空白棋盘开始
- 支持FEN格式导入/导出棋局
- 走棋模式自动验证走法合法性
- 走棋后自动触发引擎分析

### 棋盘交互
- 点击选子+点击目标位置走棋
- 拖拽走棋
- 走法箭头指示
- 将军提示

## 技术栈

| 技术 | 用途 |
|------|------|
| **Kotlin** | 主要开发语言 |
| **Jetpack Compose** | UI框架 |
| **Pikafish** | 象棋引擎（UCI协议） |
| **MVVM** | 架构模式 |
| **Kotlin Coroutines + Flow** | 异步处理和响应式编程 |

## 项目结构

```
app/src/main/
├── assets/                         # 引擎二进制文件
│   ├── pikafish-armv8              # ARM v8引擎
│   └── pikafish-armv8-dotprod      # ARM v8 dotprod引擎
├── kotlin/com/chesspro/app/
│   ├── core/
│   │   ├── chess/                  # 棋盘逻辑
│   │   │   ├── ChessBoard.kt      # 棋盘（走法验证、FEN转换）
│   │   │   ├── ChessPiece.kt      # 棋子模型
│   │   │   ├── Move.kt            # 走法模型
│   │   │   ├── Position.kt        # 位置坐标
│   │   │   ├── PieceType.kt       # 棋子类型枚举
│   │   │   └── PieceColor.kt      # 棋子颜色枚举
│   │   ├── engine/                 # Pikafish引擎
│   │   │   ├── PikafishEngine.kt  # 引擎进程管理+UCI通信
│   │   │   └── FenConverter.kt    # FEN格式转换
│   │   └── overlay/                # 悬浮窗
│   │       ├── OverlayService.kt   # 悬浮窗前台服务
│   │       └── OverlayContent.kt   # 悬浮窗Compose UI
│   ├── ui/
│   │   ├── screens/
│   │   │   └── ChessMainScreen.kt  # 主界面
│   │   ├── components/
│   │   │   ├── ChessBoardView.kt   # 棋盘绘制
│   │   │   └── UIComponents.kt     # 通用组件
│   │   ├── theme/
│   │   └── ChessViewModel.kt       # 视图模型
│   ├── ChineseChessProApp.kt       # Application
│   └── MainActivity.kt             # 主Activity
```

## 构建

### 前置条件
- Android Studio Hedgehog (2023.1.1)+
- JDK 17
- Android SDK 34

### 构建步骤

```bash
git clone https://github.com/your-username/ChineseChessPro.git
cd ChineseChessPro
./gradlew assembleDebug
```

APK输出: `app/build/outputs/apk/debug/app-debug.apk`

> **注意**: 仅支持 **ARM64 (arm64-v8a)** 设备，因为Pikafish引擎是ARM64原生二进制文件。

### NNUE权重文件（可选）

Pikafish可使用NNUE神经网络评估函数提升棋力。下载 `pikafish.nnue` 放入应用内部存储即可自动加载。

下载地址: https://github.com/official-pikafish/Pikafish/releases

## 权限说明

| 权限 | 用途 |
|------|------|
| `SYSTEM_ALERT_WINDOW` | 显示分析悬浮窗 |
| `FOREGROUND_SERVICE` | 保持悬浮窗服务运行 |
| `INTERNET` | 下载NNUE权重文件（可选） |
| `VIBRATE` | 走棋振动反馈 |

## CI/CD

项目配置了GitHub Actions自动构建：
- 推送到 `main`/`master` 分支自动构建Debug APK
- 创建 `v*` 标签自动发布Release

## 致谢

- [Pikafish](https://github.com/official-pikafish/Pikafish) - 开源象棋引擎
- [Jetpack Compose](https://developer.android.com/jetpack/compose) - Android UI框架
