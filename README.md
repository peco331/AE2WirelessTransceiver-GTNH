# AE2 Wireless Transceiver (GTNH)

将 [ExtendedAE_Plus](https://github.com/GaLicn/ExtendedAE_Plus) 的无线收发器移植到 **GTNH 2.9.0-beta2**（MC 1.7.10）。

A GTNH 1.7.10 port of the ExtendedAE_Plus wireless transceiver.

[下载最新版本](https://github.com/peco331/AE2WirelessTransceiver-GTNH/releases/latest) · [详细更新日志](CHANGELOG.md) · [技术文档](docs/TECHNICAL.md)

## 功能

- **无线桥接 ME 频道**：命名频段（Band），支持跨维度
- **频段管理 GUI**：新建 / 删除 / 设定 / 断开，搜索，实时信息（删除频段时自动断开并清理所有关联收发器，未加载区块中的设备未来加载时自动清理，杜绝已删除频段复活）
- **频道占用显示**：沿真实 AE2 导线连接精确统计所有 `REQUIRE_CHANNEL` 设备；缺少频道的设备也计入，因此 33 台设备会显示红色 `33/32`
- **AE2 网络可视化工具支持**：手持 ToolNetworkVisualiser 查看 3D 拓扑时，在同频段收发器之间正确显示致密拓扑连线与频道负载（仅合成显示数据包，不改变底层 AE2 网络结构）
- **3D 模型外观**：检测到兼容的 GTNHLib 时使用 blockbench 模型（Light Mode 风格）与在线核心呼吸发光；否则自动回退为带 off/on 贴图的原版立方体
- **Waila 支持**：频段、所有者、频道、在线收发器数、锁定、设备状态
- **扳手交互**：右键锁定（防盗/权限保护）、潜行右键拆卸
- **防串频道**：相邻收发器不会直接互连

## 操作

| 操作 | 效果 |
|---|---|
| 右键 | 打开频段管理 GUI |
| 扳手 + 右键 | 锁定 / 解锁（仅所有者与管理员可解锁） |
| 扳手 + 潜行 + 右键 | 拆卸（仅所有者与管理员可拆卸已锁定方块） |

## 配置

游戏内：Mod 列表 → Config；或编辑 `config/ae2wtx.cfg`：

| 键 | 默认 | 说明 |
|---|---|---|
| `wireless.wirelessCrossDimEnable` | `true` | 跨维度无线桥接（**修改后需要完整重启游戏/服务端生效**） |
| `wireless.wirelessTransceiverIdlePower` | `10.0` | 节点空闲 AE 功耗 (AE/t)，必须是有限非负数（即时生效） |
| `wireless.wirelessMaxBandsPerOwner` | `128` | 每位所有者在当前频段作用域内最多可创建的频段数，范围 `0..10000` |
| `wireless.wirelessMaxBandsPerWorld` | `4096` | 整个世界存档最多可创建的频段总数，范围 `0..100000` |

两个频段上限都只在**创建新频段**时检查；连接已有频段、存档恢复不受影响。`0` 表示禁止创建新频段，降低上限不会删除已有频段。负数或超出范围的值会钳制到边界并写入日志；非法功耗会恢复为 `10.0`。游戏内 Config 可即时刷新功耗和频段上限（本地/单人）；专用服务器修改配置文件后可由权限等级 2 的管理员执行 `/ae2wtx reload`。跨维开关仍必须完整重启后生效。

## 兼容性

- 测试版本：GTNH 2.9.0-beta2
- 必需依赖：AE2（Applied-Energistics-2-Unofficial `rv3-beta-1034-GTNH`）、GT5-Unofficial `5.09.52.594`
- 可选依赖：GTNHLib（整合包自带的 `0.11.24` 已验证；未安装或模型 API 不兼容时自动使用原版立方体渲染）、Waila `1.19.29`（未安装时仅不显示 Waila 信息）
- 支持环境：客户端、单人游戏、专用服务器（Dedicated Server）
- 语言：简体中文 / 繁體中文 / English

## 模型与材质来源

- 3D 方块模型与贴图适配自 **ExtendedAE_Plus Light Mode Texture Pack (1.21)**（作者 **C-H716, _leng, fish_旦**），其版权归原作者所有
- 安装兼容版本时，方块模型由 [GTNHLib](https://github.com/GTNewHorizons/GTNHLib) 现代模型系统渲染；否则使用内置回退贴图

## 许可

LGPL-3.0。移植自 [ExtendedAE_Plus](https://github.com/GaLicn/ExtendedAE_Plus)（作者 GaLicn，LGPL-3.0）。发布 JAR 在 `META-INF` 中附带本项目许可证和第三方归属说明；详细移植说明见 [docs/TECHNICAL.md](docs/TECHNICAL.md)。

