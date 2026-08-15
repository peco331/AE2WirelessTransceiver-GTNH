# AE2 Wireless Transceiver (GTNH)

无线收发器 — 将 [ExtendedAE_Plus](https://github.com/GaLicn/ExtendedAE_Plus) 的无线收发器移植到 **GTNH 2.9.0-beta1**（Minecraft 1.7.10 / Forge 10.13.4.1614）。

A GTNH 1.7.10 port of the wireless transceiver from ExtendedAE_Plus.

## 功能 Features

- **单一无线收发器**：命名频段（Band）无线桥接 ME 频道，跨维度（可配置）
- **频段管理 GUI**（EAEP 原版布局）：新建 / 删除 / 设定 / 断开频段、频段列表搜索、实时信息面板
- **频道占用显示**：
  - 本机频道：本地网络通道消费者数 / ME 实际容量（32，无限模式显示 ∞）
  - 本频段频道：整个频段所有收发器的占用总和，超载标红、满载标黄（`32+x/32`）
  - 按方块统计（二合一接口等双节点设备只计 1）
- **Waila 集成**：频段 / 所有者 / 本机频道 / 本频段频道 / 在线收发器数 / 锁定状态 / 设备在线
- **扳手交互**：右键锁定（防盗，挖掘减速 90%）、潜行右键拆卸（AE2 式地面掉落）
- **防串频道**：相邻收发器永不直接互连，只能通过同名频段网络传递
- **游戏内配置**：Mod 列表 → Config，即时生效；或编辑 `config/ae2wtx.cfg`

## 操作 Interactions

| 操作 | 效果 |
|---|---|
| 右键 | 打开频段管理 GUI |
| 扳手 + 右键 | 锁定 / 解锁收发器 |
| 扳手 + 潜行 + 右键 | 拆卸（掉落方块） |

## 配置 Config

| 键 | 默认 | 说明 |
|---|---|---|
| `wireless.wirelessCrossDimEnable` | `true` | 跨维度无线桥接开关 |
| `wireless.wirelessTransceiverIdlePower` | `10.0` | 收发器节点空闲 AE 功耗 (AE/t) |

## 兼容性 Compatibility

- 测试版本：**GTNH 2.9.0-beta1**（Java 17-25 服务器 + Prism 客户端）
- 依赖：Applied Energistics 2（GTNH rv3-beta-977）、GT5-Unofficial
- 语言：简体中文 / 繁體中文 / English

## 构建 Build

```bash
# JDK 25 + Gradle（gtnhconvention）
VERSION=1.0.1 gradlew reobfJar
# 产物：build/libs/ae2wtx-1.0.1.jar
```

## 移植说明 / 许可 Porting notes & License

本项目是 [ExtendedAE_Plus](https://github.com/GaLicn/ExtendedAE_Plus)（作者 GaLicn）无线收发器部分的移植与 GTNH 适配，包括：

- 移植的代码：标签网络注册表（`LabelNetworkRegistry`）、连接器（`LabelLink`）、管理 GUI 与布局、Waila/Jade 显示结构、交互设计（锁定 / 拆卸 / 频段操作）、纹理与 GUI 资源
- GTNH 适配：rv3 网格节点生命周期（`updateState` 手动驱动）、rv3 安全键匹配修复（`setLastSecurityKey` / `setPlayerID`）、防串频道、频道占用统计增强、性能节流

依据上游许可，本项目以 **GNU Lesser General Public License v3.0（LGPL-3.0）** 发布，见 [LICENSE](LICENSE)。上游项目 [ExtendedAE_Plus](https://github.com/GaLicn/ExtendedAE_Plus) 同样以 LGPL-3.0 发布。

**第三方组件声明**：Applied Energistics 2（AE2, MIT License）、GregTech 5 Unofficial、SpongePowered Mixin（MIT License）、ExtendedAE_Plus（LGPL-3.0）。各组件版权归其原作者所有。

## 材质包 Resource Pack

`resourcepacks/ae2wtx-light-mode/`（或 `ae2wtx-light-mode.zip`）提供 **128x 亮色（Light Mode）外观**：

- 收发器方块：128x 高清亮色纹理（放置于游戏 `resourcepacks/` 目录后在游戏内启用；on 状态为提亮版以近似 1.20.1 的 lighting 发光层，1.7.10 无 emissive 渲染）
- 适配自 **ExtendedAE_Plus Light Mode Texture Pack (1.21)**，作者 **C-H716, _leng, fish_旦**，其版权归原作者所有

## 作者 Author

- [peco331](https://github.com/peco331)

## 致谢 Credits

- [GaLicn](https://github.com/GaLicn/ExtendedAE_Plus) — ExtendedAE_Plus 原作者（LGPL-3.0）
- DeepSeek AI — AI 编码辅助（AI-assisted development）
