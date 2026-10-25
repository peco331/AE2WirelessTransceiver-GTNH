# AE2 无线收发器 · AE2 Wireless Transceiver (GTNH)

<p align="center">
  <img src="docs/images/hero.webp" alt="深色与浅色两台无线收发器，玻璃机壳里各有一个小黑洞" width="720">
</p>

给 GT New Horizons 用的 ME 无线连接方块。两台收发器设成同一个**频率名**，它们背后的 ME 网络就连到了一起：不限距离，可以跨维度，也可以多台互连。

适用 Minecraft 1.7.10，支持 GTNH 2.9.0 RC1 与 beta3。移植自 [ExtendedAE_Plus](https://github.com/GaLicn/ExtendedAE_Plus) 的无线收发器，外观为本项目原创。

[下载](https://github.com/peco331/AE2WirelessTransceiver-GTNH/releases/latest) · [更新日志](CHANGELOG.md) · [技术文档](docs/TECHNICAL.md) · [反馈问题](https://github.com/peco331/AE2WirelessTransceiver-GTNH/issues)

## 它能做什么

- **按名字配对**：不分主从，不用配对奇点。同一玩家的收发器选同一个频率即可互连。
- **32 频道**：每台收发器可承载 32 个频道，本身不占频道，默认 10 AE/t，与距离无关。
- **找得到**：列出一个频率下的全部收发器，在世界里透视高亮，并给出可点击的传送坐标。
- **起名字**：每台收发器可以单独命名，列表、定位和 Waila 都会显示。
- **看得见**：Waila 和界面会直接写明收发器为什么不工作，比如未设频率、被安全终端拒绝、网络无电。AE2 网络工具也能显示无线连线。
- **外观**：玻璃机壳里是一个小黑洞，周围的光点显示频率的频道用量。有深色、浅色两套配色，每台单独选择。

## 安装

把 `ae2wtx-*.jar` 放进 `mods` 文件夹（更新时移走旧版）。联机时客户端和服务端都要装。

需要 AE2（GTNH 分支）和 GregTech 5 Unofficial，整合包自带。
## 上手

**合成**：8 个 ME 量子环围一圈，中间放 1 个 ME 量子链接仓。

1. 在基地和远处各放一台收发器，分别用 ME 线缆接上各自的网络。
2. 右键基地那台，输入一个名字（例如 `mining`），点 **新建**。
3. 右键远处那台，选中 `mining`，点 **设为当前**。

完成。两边的区块需要保持加载，网络需要有电。

**断开**只让这一台退出频率；**删除**会删掉整个频率，仅所有者或管理员可用，需要点两次确认。

## 界面里的其他功能

- **定位**：点 **收发器** 切换到当前频率的收发器列表，选中一台或多台（Ctrl 多选）后点 **定位**，它们会被透视高亮 60 秒；不选则高亮全部。聊天栏的坐标可以点击，装有 ServerUtilities 时生成可跨维度的 `/tpl`。其他维度的收发器同样会被标记，进入该维度即可看到。
- **命名**：标题栏的铅笔按钮。回车保存，Esc 取消，清空即删除名字，最多 32 字。铁砧改过名的收发器放下时直接使用该名字。
- **外观切换**：标题栏的另一个小按钮，在“跟随默认 → 深色 → 浅色”之间切换。

用扳手右键锁定 / 解锁，潜行右键拆卸。扳手拆下时名字和外观跟着物品走；用镐挖掉则不保留。锁定后只有所有者或管理员能改名、换外观或拆卸。

## 频道占用率

黑洞周围的 8 个光点代表这个频率的全部容量，32 频道时每个光点 4 个频道。

- 亮起的数量 = 整个频率已用的频道，其中本机承担的那部分更大更亮。
- 颜色 = 频率负载：紫色正常，橙色超过 75%，红色已满。
- 离线时黑洞变暗，光点和玻璃上的电弧熄灭。

想看清内部，可以在配置里调低接线面板的不透明度（`panelOpacity`）。

## 使用须知

- 无线连接照常遵守 AE2 的频道、供电和安全终端规则。普通线缆仍是 8 频道，需要跑满 32 频道请用致密线缆。
- 本模组不加载区块。
- 不同玩家的同名频率互不相通，目前没有队伍共享。关闭跨维连接后，频率按维度分开。
- 频道显示的是设备需求，缺频道的设备也算在内，所以可能出现红色的 `33/32`；这不代表真的分到了 33 个频道。
- 两台相邻的收发器不会直接互连，但仍可能通过共用的线缆接入同一网络。

## 配置

`config/ae2wtx.cfg`，单人游戏也可以在模组列表的 Config 里改。

**画面（`client`）**：只影响本机，在 Config 第一页修改，点“完成”立即生效。

| 选项 | 默认 | 作用 |
|---|---|---|
| `defaultTheme` | `dark` | 没单独选外观的收发器用深色（`dark`）还是浅色（`light`） |
| `panelOpacity` | `100` | 接线面板不透明度，0~100%，按 10% 一档；0 为隐藏 |
| `glassArcs` | `true` | 在线时在玻璃上画电弧 |
| `renderEffects` | `true` | 画黑洞和光点 |
| `effectRenderDistance` | `32` | 多少格内画黑洞和光点 |
| `simpleBlockModel` | `false` | 改用普通方块外观，不画机壳和特效 |
| `locateSeconds` | `60` | 定位高亮持续秒数（5~600） |
| `locateThroughWalls` | `true` | 定位高亮是否透过方块显示 |

**无线（`wireless`）**：在 Config 的“无线”子页，或编辑文件后重启。专服管理员可用 `/ae2wtx reload` 立即应用功耗和数量上限；跨维开关需要重启。

| 选项 | 默认 | 作用 |
|---|---|---|
| `wirelessCrossDimEnable` | `true` | 允许跨维度连接 |
| `wirelessTransceiverIdlePower` | `10.0` | 每台收发器的功耗，AE/t |
| `wirelessMaxBandsPerOwner` | `128` | 每位玩家的频率数量上限；关闭跨维后按维度计算 |
| `wirelessMaxBandsPerWorld` | `4096` | 整个存档的频率总数上限 |
| `pruneMissingTransceivers` | `true` | 自动清除已经不存在的收发器记录（只检查已加载的区块） |

数量上限只限制新建，`0` 为禁止新建；调低上限不会删掉已有频率。

## 来源与许可

移植自 [ExtendedAE_Plus](https://github.com/GaLicn/ExtendedAE_Plus)（GaLicn），以 **LGPL-3.0** 发布。方块外观、贴图、GUI 和音效为本项目原创。早期版本使用过 ExtendedAE_Plus Light Mode 材质包的资源，现已全部移除。详见 [LICENSE](LICENSE) 和 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。

术语参考：[GTNH 中文 Wiki](https://gtnh.huijiwiki.com/wiki/%E5%BA%94%E7%94%A8%E8%83%BD%E6%BA%902/%E8%BF%9E%E6%8E%A5ME%E7%BD%91%E7%BB%9C) · [MC 百科：无线收发器](https://www.mcmod.cn/item/890807.html)。操作方式以本页为准。

---

### English

A wireless ME connector for GT New Horizons (Minecraft 1.7.10, GTNH 2.9.0 RC1 and beta3). Give two or more transceivers the same **frequency** name and their ME networks join: no range limit, across dimensions, 32 channels each, 10 AE/t.

Craft one from 8 ME Quantum Rings around an ME Quantum Link Chamber. Right-click a transceiver, type a name and press **New**. Then open the other transceiver, select the name and press **Apply**. The GUI can also list and highlight every transceiver of a frequency and give each one a name. It shows why a transceiver is offline (no frequency, denied by a security terminal, no power…). The block is a glass casing with a small black hole inside; the sparks around it show the frequency's channel use. It comes in dark and light themes.

Install on both client and server. It requires AE2 (GTNH fork) and GregTech 5 Unofficial. Licensed LGPL-3.0, ported from ExtendedAE_Plus by GaLicn.
