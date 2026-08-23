# AE2 Wireless Transceiver — 技术文档

面向开发者的详细文档：移植来源、GTNH/rv3 适配细节、架构、性能与已知限制。

## 1. 移植来源与许可

- 移植自 [ExtendedAE_Plus](https://github.com/GaLicn/ExtendedAE_Plus)（作者 GaLicn，**LGPL-3.0**），本项目同样以 LGPL-3.0 发布（见根目录 [LICENSE](../LICENSE)）
- 移植的代码与设计：
  - 标签网络注册表 `LabelNetworkRegistry`（WorldSavedData：Key(维度/标签/所有者)、频道从 1,000,000 递增、虚拟节点）
  - 连接器 `LabelLink`（收发器节点 ↔ 频段虚拟节点）
  - 管理 GUI 与布局（EAEP 原版纹理与交互：新建/删除/设定/断开、搜索、信息面板）
  - Waila/Jade 显示结构、锁定/拆卸交互、纹理与 GUI 资源
- 第三方组件：AE2（MIT）、GT5-Unofficial、SpongePowered Mixin（MIT）；Light Mode 纹理版权归 C-H716, _leng, fish_旦（见下）

## 2. 构建

```bash
# Linux/macOS：JDK 25 + Jabel，输出仍为 Java 8 字节码
VERSION=1.0.6 ./gradlew clean build
# 产物：build/libs/ae2wtx-1.0.6.jar
```

```powershell
# Windows PowerShell
$env:VERSION = "1.0.6"
.\gradlew.bat clean build
```

- Wrapper 下载包固定 SHA-256；构建使用 JDK 25，Jabel 生成 Java 8（class major `52`）字节码
- GitHub Actions 执行同一 `clean build` 并扫描产物中的 class major；这属于构建期检查，不替代真实 GTNH 客户端/专服烟雾测试
- Mixin 基础设施启用（`usesMixins=true`），注册 `MixinToolNetworkVisualiser` 拦截网络可视化数据包
- 必需依赖：`Applied-Energistics-2-Unofficial:rv3-beta-1034-GTNH`（匹配 GTNH 2.9.0-beta2 整合包实际版本）、`GT5-Unofficial:5.09.52.594`
- GTNHLib 是可选的增强渲染集成：构建脚本仅把整合包版本 `0.11.24` 放入开发运行环境；发布 MOD 不声明最低版本，也不包含对其类的硬链接。缺失或模型 API 不兼容时使用原版立方体渲染
- Waila `1.19.29` 仅以 `compileOnly` 编译可选兼容，不会被打包或声明为运行时必需依赖
- `processResources` 将根目录 `LICENSE` 与 `THIRD_PARTY_NOTICES.md` 收入发布 JAR 的 `META-INF/`

## 3. GTNH/rv3 适配要点（踩坑记录）

rv3 与 1.20.1 的 AE2 API 差异巨大，以下均为移植时确认并修复的关键点：

### 3.1 节点生命周期
- rv3 的 `Grid.update()` **不会**调用 `GridNode.updateState()`——节点必须自行周期性调用 `updateState()` 才能加入/保持在本地网格
- `AEApi.createGridNode()` 在**客户端被禁止**（抛 "Grid features are server side only"）——第三方 mod 无法做客户端网格模拟
- 新建节点在任何连接与 `updateState()` 前写入放置者的 AE2 player ID；连接权限由 AE2 自身校验——见 3.3

### 3.2 连接建立
- rv3 API 只有无方向的 `createGridConnection(a, b)`（内部 `dir=UNKNOWN`）→ `hasDirection()=false` → `GridNode.addConnection` **跳过 `ConnectionsChanged` 通知** → 线缆视觉永不刷新、`getConnectedSides()` 恒为空
- 修复：手动连接使用 **带方向的构造** `new appeng.me.GridConnection(node, other, dir)`（与 FindConnections 内部一致）→ 双方节点收到 `onGridNotification(ConnectionsChanged)` → 线缆连接外观自动更新
- **禁止**对线缆调用 `CableBusContainer.updateConnections()` 或线缆节点 `updateState()`：会触发线缆 FindConnections 重复建连 → `ExistingConnectionException` → CableBus 的失败处理 `removeFromWorld` → **线缆掉落**

### 3.3 AE2 安全模型
- `applyNodeIdentity()` 在标签连接、物理邻接扫描和 `updateState()` 之前设置放置者的 AE2 `playerID`。
- 不读取、复制或覆写私有 SecurityKey；无线连接统一通过 AE2 正常的 `createGridConnection` 安全检查。
- 安全拒绝只会保持断链，并按 100 tick 退避重试；不会用对齐安全键的方式绕过安全站。

### 3.4 防串频道与节点可见性
- 收发器 `isWorldAccessible() = true`：确保 AE2 原版 `ToolNetworkVisualiser` 将其识别为真实物理方块节点加入 `VNode` 列表
- 防串频道保护：在 `getGridNode(ForgeDirection dir)` 中检查方向上的邻居方块实体，若为其他收发器则直接返回 `null`，彻底杜绝相邻收发器直接互连
- `VirtualLabelNodeHost` 保持 `isWorldAccessible() = false`，作为内部虚拟中继节点，不暴露在世界物理网格中
- `maintainLocalConnections()`（每 5 tick）手动扫描 6 方向 IGridHost 建连，**跳过其他收发器**
- 每 tick 保留 `node.updateState()`（网格接入关键）

### 3.5 频道占用统计（玩家可见口径）
- 从收发器本地节点沿真实 `IGridConnection` 做 BFS，只统计真正接在该导线分支上的节点。
- 每个带 `REQUIRE_CHANNEL` 的普通节点计 1；不检查 `isActive()` 或已分配频道，因此缺频道的第 33 个设备仍会得到精确的红色 `33/32`。
- 带 `MULTIBLOCK` 的 AE2 多方块使用 `IGridMultiblock.getMultiblockNodes()` 与 AE2 路径缓存相同的节点集折叠为一次需求。
- 其他无线收发器、频段虚拟节点和 ME Controller 是不计数且不穿越的边界；无标志线缆节点不计数但继续遍历。
- 结果缓存 `serverUsedCache`：拓扑事件置 dirty 后 2 tick 合并刷新，稳定状态每 10 tick 错峰校准；Waila/GUI/全网统计只读缓存。
- maxChannels 读 `GridNode.getMaxChannels()`（rv3: `CHANNEL_COUNT[compressedData&3]` = {0, 8, 32, MAX_VALUE}；无限频道模式显示 ∞）
- 全网占用 = 各端点缓存之和；超载（>max）标红、满载（==max）标黄

### 3.6 性能优化与缓存
- BFS 设备计数：事件驱动 dirty + 2 tick debounce + 10 tick 错峰兜底（Waila/GUI 不重复执行）。
- `LabelNetworkRegistry` 直接使用 overworld `MapStorage` 的实例缓存语义，不再额外持有静态 World 映射。
- `LabelNetwork` 频段统计缓存：全频段频道总数与在线收发器数下沉至 `LabelNetwork` 进行 20-tick 共享缓存，同频段所有收发器共享单次统计结果，将 $O(N^2)$ 全网扫描降低至 $O(N)$
- endpoint 反向索引使注册、解绑和缓存失效为 O(1)；频段列表由服务端搜索并按最多 128 项分页。
- AE2 playerID 按 owner 缓存。
- 视觉状态（IEnergyGrid 查询、meta 更新）10 tick 节流；连接日志 DEBUG 级

### 3.7 AE2 Network Visualiser 无线拓扑合成架构
- **真实网格与可视化解耦**：
  - 底层 AE2 Grid 拓扑：每个收发器仅与该频段的 `VirtualLabelNodeHost` 保持一条真实的 `IGridConnection`；绝不创建 `Transceiver A <-> Transceiver B` 的真实物理连接，避免网格循环回路与频道紊乱。
  - 客户端 3D 拓扑渲染：通过 `MixinToolNetworkVisualiser` 在 `ToolNetworkVisualiser.onUpdate` 服务端发送封包前拦截数据，由 `NetworkVisualiserCompat` 从当前视角 `World` 中已有的 `VNode` 列表过滤出有效频段节点，按坐标字典序升序生成确定的 $N-1$ 阶星型生成树（Spanning Star Tree），连线标记为 `VLinkFlags.DENSE`，并填入各收发器连接虚拟节点的实际无线占用频道数。

### 3.8 服务端安全与权限模型
- **防盗与权限校验**：`TransceiverSecurity.canManage(player, te)` 保证未锁定收发器可正常交互；锁定收发器仅拥有者（Owner UUID）与服务器管理员（OP）可进行扳手拆卸、切换解锁与 GUI 频段管理。
- **距离校验**：Container 与所有 C2S 操作数据包（`LabelApplyPacket`、`LabelListRequestPacket`）严格校验玩家与目标方块的欧几里得距离平方（$\le 64.0\text{D}$，即 8 格以内）。
- **主线程边界**：Netty handler 只解码并进入有界队列；世界、容器和注册表操作全部在服务端 tick 线程重新解析玩家后执行。
- **限流与容量**：写操作每玩家 4/s（burst 8），列表 2/s（burst 4）；队列同时限制全局、每玩家容量和每 tick 消费量。
- **字节流安全**：`NetworkBufferUtils` 校验字符串长度边界与可读字节数；删除使用最多 128 项的单个批量包，列表响应最多 128 项。

### 3.9 频段删除生命周期与墓碑机制 (Band Deletion Lifecycle & Tombstone)
- **区分临时卸载与永久移除**：
  - 区块卸载（`onChunkUnload`）：仅销毁本地 GridNode 并断开运行时无线连接，**在 `LabelNetworkRegistry` 中保留 `EndpointRef` 索引**，不注销端点；
  - 真正方块拆除（`breakBlock` / 扳手拆卸 / 永久销毁）：执行完整 `cleanup(true)`，从注册表中 `unregister(this)` 注销 `EndpointRef`；
  - 统计遍历安全：`updateStatsIfNeeded()` 使用 `world.blockExists()` 严格跳过未加载区块，绝不强制加载区块。
- **已加载端点立即清除**：删除频段时快照 `net.endpoints`，已加载区块中的收发器立即调用 `clearLabelAfterNetworkDeletion()`，断开真实无线连接并重置频率与显示标签。
- **未加载端点持久化墓碑**：由于未加载端点在区块卸载期间仍保留在 `endpoints` 中，删除频段时可准确将其捕获并记录在 `LabelNetworkRegistry` 的 `pendingClears`（墓碑集合），随世界 NBT 持久化存储。
- **杜绝已删除频段复活**：离线端点在未来区块加载并执行 `refreshLabel()` 时，首先检查并消耗对应的墓碑标记，安全清空本地标签与频率，绝不自动重新注册已删除的频段。
- **所有者与坐标严格隔离**：墓碑包含 `(dim, x, y, z, label, owner)`，相同坐标新建其他频段或不同所有者的同名频段绝不发生误清。

### 3.10 运行时配置语义 (Runtime Config Semantics)
- `wireless.wirelessCrossDimEnable`：参与 `LabelNetwork.Key` 的维度哈希与底层 AE2 网格定位，**标记为 `setRequiresMcRestart(true)`**；游戏内修改保存到配置文件，但运行时活动值仅在游戏/服务端启动时加载一次，避免产生跨维混合死锁。
- `wireless.wirelessTransceiverIdlePower`：默认 `10.0 AE/t`，必须是有限非负数；非法值恢复默认值。支持游戏内 Config 界面即时修改并实时生效。
- `wireless.wirelessMaxBandsPerOwner`：默认 `128`、范围 `0..10000`，限制一位所有者在当前频段作用域内创建的新频段数量。
- `wireless.wirelessMaxBandsPerWorld`：默认 `4096`、范围 `0..100000`，限制该世界存档的频段总量。
- 两个上限都采用“创建时”语义：`0` 禁止新建；连接已有频段、加载/修复已有端点不消耗新配额；降低上限不会删除已有数据。负值与超界整数钳制到边界并记录警告，非整数恢复默认值。
- 客户端 Config 事件只在客户端代理中注册到 FML bus，避免 common/server 类路径引用客户端事件。它可即时刷新本地/集成服的功耗与上限；专用服务器修改 `config/ae2wtx.cfg` 后可用权限等级 2 的 `/ae2wtx reload` 即时刷新这些安全运行时值。跨维设置仍需完整重启。

### 3.11 数据包解码防御与空字符串语义区分 (Packet Decoder Hardening)
- 严格区分“合法空字符串”（代表玩家主动清空标签/断开连接）与“畸形异常数据包”（长度为负数、超过上限或超出缓冲区可读字节数）。
- `NetworkBufferUtils.tryReadUtf8` 在格式异常时返回 `null` 并将数据包标记为 `valid = false`，服务端/客户端 Handler 直接丢弃该数据包，绝不将其误当作断开连接或篡改状态。
- `LabelListRequestPacket` 携带页号、页大小和查询串；服务端把页大小钳制到 `1..128`，响应同时校验分页元数据与 `count` 上限，异常时整包丢弃。

## 4. 架构

```
cn.gtnh.ae2wtx
├── AE2Wtx / CommonProxy / ClientProxy      # 主类与代理
├── command/AE2WtxCommand                   # 专服运行时安全配置重载
├── client
│   ├── config/ClientConfigChangedHandler   # 客户端 FML ConfigChangedEvent
│   ├── gui/                                # Mod 列表配置界面
│   └── screen/LabeledTransceiverGui        # 频段管理 GUI（EAEP 布局）
├── compat/                                 # WailaProvider / WrenchHandler
├── config/ModConfig                        # Forge Configuration + 配置 GUI 支持
├── content/wireless/                       # 方块 + TE（节点生命周期/统计/视觉）
├── gui/                                    # ModGuiHandler / LabeledContainer
├── init/                                   # 注册（方块/TE/创意标签）
├── network/                                # 分页包、服务端任务队列、限流与校验
└── wireless/                               # LabelNetworkRegistry / LabelLink / IWirelessEndpoint
```

## 5. 渲染与外观

- **3D 模型（可选 GTNHLib 现代模型系统）**：检测到兼容 API 时，方块渲染采用 blockbench 模型（Light Mode plain 版，31 元素）
  - 资源：`assets/ae2wtx/blockstates/labeled_wireless_transceiver.json`（variants: meta=0/1）+ `models/blocks/lable_off.json`（channel0 状态）/ `lable_on.json`（channel5 状态 + 发光核心元素）
  - 客户端 init 仅在检测到 `gtnhlib` 后以反射探测 `ModelRegistry.registerModid(String)` 与 `ModelISBRH.JSON_ISBRH_ID`；成功后启用 JSON render type，避免专服或无库客户端产生类链接错误
  - 缺少 GTNHLib 或 API 不匹配时，`getRenderType()` 保持为原版 standard block render type `0`，使用 `registerBlockIcons/getIcon` 已注册的 off/on 贴图渲染完整立方体；核心逻辑、频道状态和发光等级不受影响
  - **纹理路径陷阱**：gtnhlib 的模型纹理从 **`textures/blocks/`（1.7.10 复数）** 加载（TEXEX 正则 `^([^:]+:)blocks?/` 剥离模型引用中的 `block/` 前缀），不是 1.8 的 `textures/block/`
  - **动画**：on 模型发光核心引用 lighting 帧条（16x432 + mcmeta frametime 2），gtnhlib `AnimatedTexture` 自动播放（呼吸发光）；off 模型隐藏核心用透明贴图（避免 z-fighting 闪烁）
  - 渲染性能：gtnhlib 自带模型缓存（BLOCKSTATE_MODEL_CACHE/JSON_MODEL_CACHE），烘焙一次性、渲染走 DirectTessellator/CEL；纹理仅 128x，影响可忽略
- 自发光：`getLightValue(IBlockAccess,...)` 在线 15/15，离线 0；状态翻转时 `updateLightByType` 重算
- 默认方块贴图（`textures/blocks/wireless_transceiver/lable_*`）作为 getIcon/粒子 fallback

## 6. 已知限制

- **channel 0-5 全部六态**未复刻（本 mod 方块仅 off/on 两态，off=channel0 外观 / on=channel5 外观；频道实时信息由 Waila/GUI 呈现）
- **客户端网格模拟**不可用（rv3 API 禁止第三方客户端建节点）——第三方方块贴线缆的连接点显示与 AppEU 等同类 mod 一致
- 玩家可见频道数采用实际导线拓扑上的需求口径；它有意显示需求而非已分配数，以便超载时仍显示 `33/32`
- CI 只覆盖编译、静态检查与 Java 8 字节码检查；发布前仍需在目标 GTNH 版本执行客户端和专用服务器烟雾测试
- 早期手写 `ISimpleBlockRenderingHandler` 方案已废弃（缺面/透明/物品栏异常）；兼容 GTNHLib 时由其官方模型系统承担增强渲染，无库时使用稳定的原版立方体回退

## 7. 交互决策记录

- 右键 = 打开 GUI；扳手右键 = 锁定；扳手潜行右键 = 拆卸（AE2 式地面掉落）
- 所有频段/频道操作均通过 GUI 完成（早期版本的数字频率增删交互已废弃）
- 频道卡（设备级直连）与 plain 数字频率收发器已移除（与从端收发器功能冗余），仅保留单一收发器
