# AE2 Wireless Transceiver (GTNH)

将 [ExtendedAE_Plus](https://github.com/GaLicn/ExtendedAE_Plus) 的无线收发器移植到 **GTNH 2.9.0-beta1**（MC 1.7.10）。

A GTNH 1.7.10 port of the ExtendedAE_Plus wireless transceiver.

## 功能

- **无线桥接 ME 频道**：命名频段（Band），支持跨维度
- **频段管理 GUI**：新建 / 删除 / 设定 / 断开，搜索，实时信息
- **频道占用显示**：本机频道与全频段占用（超载红色 / 满载黄色），按设备统计
- **3D 模型外观**：blockbench 模型（Light Mode 风格），在线时核心呼吸发光
- **Waila 支持**：频段、所有者、频道、在线收发器数、锁定、设备状态
- **扳手交互**：右键锁定（防盗）、潜行右键拆卸
- **防串频道**：相邻收发器不会直接互连

## 操作

| 操作 | 效果 |
|---|---|
| 右键 | 打开频段管理 GUI |
| 扳手 + 右键 | 锁定 / 解锁 |
| 扳手 + 潜行 + 右键 | 拆卸 |

## 配置

游戏内：Mod 列表 → Config（即时生效）；或编辑 `config/ae2wtx.cfg`：

| 键 | 默认 | 说明 |
|---|---|---|
| `wireless.wirelessCrossDimEnable` | `true` | 跨维度无线桥接 |
| `wireless.wirelessTransceiverIdlePower` | `10.0` | 节点空闲 AE 功耗 (AE/t) |

## 兼容性

- 测试版本：GTNH 2.9.0-beta1
- 依赖：AE2（GTNH rv3）、GT5-Unofficial、GTNHLib
- 语言：简体中文 / 繁體中文 / English

## 模型与材质来源

- 3D 方块模型与贴图适配自 **ExtendedAE_Plus Light Mode Texture Pack (1.21)**（作者 **C-H716, _leng, fish_旦**），其版权归原作者所有
- 方块模型由 [GTNHLib](https://github.com/GTNewHorizons/GTNHLib) 现代模型系统渲染

## 许可

LGPL-3.0。移植自 [ExtendedAE_Plus](https://github.com/GaLicn/ExtendedAE_Plus)（作者 GaLicn，LGPL-3.0）。详细移植说明见 [docs/TECHNICAL.md](docs/TECHNICAL.md)。

## 作者

- [peco331](https://github.com/peco331)
- DeepSeek AI — AI 编码辅助
