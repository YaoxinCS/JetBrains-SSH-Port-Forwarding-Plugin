# Port Forwarding

[English](README.md)

Port Forwarding 是一个 IntelliJ Platform 插件，用于在 JetBrains IDE 中直接管理基于 SSH 的端口转发。

它提供了原生设置页来管理 SSH 会话和 SSH 隧道，支持本地转发（`-L`）和远程转发（`-R`）。

## 功能特性

- 在 `设置 | 工具 | 端口转发` 中管理 SSH 会话
- 创建和编辑以下类型的 SSH 隧道：
  - 本地 -> 远端（`-L`）
  - 远端 -> 本地（`-R`）
- 启动或停止单个隧道
- 启动或停止当前会话下的全部隧道
- 支持全局会话和项目级会话
- 支持按隧道配置自动启动和断线自动重连
- 保存前可测试 SSH 连接
- 支持密码、密钥对和 OpenSSH Agent 认证
- 使用 IDE Password Safe 安全存储密码和口令
- 自动跟随 IDE 当前语言切换界面文案

## 环境要求

- 任意基于 IntelliJ Platform 的 JetBrains IDE
- IntelliJ Platform `252+`
- 可用的 SSH 服务端
- 使用 OpenSSH Agent 模式时，需要系统安装 `ssh`

## 安装方式

### 从磁盘安装

1. 下载仓库发布页中的插件 ZIP，或自行本地构建。
2. 在 IDE 中打开 `设置/偏好设置 | 插件`。
3. 点击右上角齿轮按钮。
4. 选择 `从磁盘安装插件...`。
5. 选中插件 ZIP 并重启 IDE。

### 本地构建

```powershell
./gradlew.bat buildPlugin
```

构建产物位于：

```text
build/distributions/Port Forwarding-<version>.zip
```

## 使用方法

1. 打开 `设置 | 工具 | 端口转发`。
2. 创建一个 SSH 会话。
3. 在该会话下新增一个或多个 `Local` 或 `Remote` 模式的隧道。
4. 启动单个隧道，或启动当前会话下的全部隧道。
5. 按需启用以下选项：
   - `Visible only in this project`
   - `Auto start when the IDE launches`
   - `Auto reconnect after disconnect`

## 认证方式

当前支持：

- 密码认证
- 密钥对认证
- OpenSSH Agent / OpenSSH Config 认证

## 项目级作用域

会话支持两种存储范围：

- Global：在整个 IDE 中可见
- Project-only：仅在当前项目中可见

项目级会话会存储在项目级配置中，并可参与项目启动时的自动启动逻辑。

## 开发

常用任务：

```powershell
./gradlew.bat buildPlugin
./gradlew.bat test
```

## 贡献

欢迎提交 Issue 和 Pull Request。

如果你计划进行较大的改动，建议先开一个 Issue 讨论需求和方向，再开始实现。

## 许可证

本项目基于 [MIT License](LICENSE) 开源。
