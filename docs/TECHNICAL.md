# AE2 Wireless Transceiver — 技术文档

面向开发者的详细文档：移植来源、GTNH/rv3 适配细节、架构、性能与已知限制。

支持 GTNH 2.9.0 RC1 与 beta3（Minecraft 1.7.10）；RC1 已由玩家实测正常使用，当前构建依赖仍沿用 beta3 基线。GTNH 2.9.0-beta2 只保留为历史兼容背景。

## 1. 移植来源与许可

- 移植自 [ExtendedAE_Plus](https://github.com/GaLicn/ExtendedAE_Plus)（作者 GaLicn，**LGPL-3.0**），本项目同样以 LGPL-3.0 发布（见根目录 [LICENSE](../LICENSE)）
- 移植的代码与设计：
  - 标签网络注册表 `LabelNetworkRegistry`（WorldSavedData：Key(维度/标签/所有者)、频道从 1,000,000 递增、虚拟节点）
  - 连接器 `LabelLink`（收发器节点 ↔ 频率虚拟节点）
  - 管理 GUI 的交互（新建/删除/设定/断开、搜索、信息面板）；布局与贴图已重做
  - Waila/Jade 显示结构、锁定/拆卸交互
- 第三方组件：AE2（MIT）、GT5-Unofficial、SpongePowered Mixin（MIT）
- 美术资源：方块模型、贴图与 GUI 贴图为原创，由 `tools/art/generate_assets.py` 生成（早期版本使用的 Light Mode 材质已全部移除）

## 2. 构建

```bash
# Linux/macOS：JDK 25 + Jabel，输出仍为 Java 8 字节码
VERSION=1.2.0 ./gradlew clean build
# 产物：build/libs/ae2wtx-1.2.0.jar
```

```powershell
# Windows PowerShell
$env:VERSION = "1.2.0"
.\gradlew.bat clean build
```

- Wrapper 下载包固定 SHA-256；构建使用 JDK 25，Jabel 生成 Java 8（class major `52`）字节码
- GitHub Actions 执行同一 `clean build` 并扫描产物中的 class major；按照当前项目验收策略，自动化测试、构建和静态资源检查是合入与发布依据，不把真实 GTNH 客户端/专服烟雾测试作为阻塞条件
- Mixin 基础设施启用（`usesMixins=true`），注册 `MixinToolNetworkVisualiser` 拦截网络可视化数据包
- 必需依赖：`Applied-Energistics-2-Unofficial:rv3-beta-1050-GTNH`（匹配 GTNH 2.9.0-beta3 目标运行时）、`GT5-Unofficial:5.09.54.133`
- 不依赖 GTNHLib：方块外观由自带的 `ISimpleBlockRenderingHandler` 与 TESR 绘制（见第 5 节）
- Waila `1.19.34` 仅以 `compileOnly` 编译可选兼容，不会被打包或声明为运行时必需依赖
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
- 安全拒绝只会保持断链，并按 100 tick 退避重试；不会用对齐安全键的方式绕过安全站。`LabelLink` 被拒时每 1200 tick 记一条 WARN（含坐标、频率与异常）。
- **玩家 ID 必须用完整 GameProfile 解析**：AE2 `PlayerRegistry.getID` 对名字为空的不完整档案直接返回 `-1`（只按 UUID 存映射，名字不参与）。频率虚拟节点曾用 `new GameProfile(owner, "")`，得到 `-1`；`Platform.securityCheck` 在一侧网格有已启用安全站、另一侧不安全时，检查不安全一侧节点的 playerID 是否有 BUILD，`-1` 必然被拒，导致频率在开服后无法再并入受保护的主网。现统一走 `LabelNetworkRegistry.aeProfile(uuid, name)`（名字为空时用 UUID 字符串占位）。开服瞬间安全缓存未就绪（`startupTicks < 20`），这类合并会侥幸成功，因此症状表现为“重启后好、之后新增连接失败”。

- **公共频率（无所有者）有意不绕过安全站**：其虚拟节点 playerID 为 `-1`，并入有已启用安全站的网络会被 AE2 拒绝，这与 AE2 对无主设备的语义一致。需要接入受保护网络时请使用有所有者的频率。
- 诊断状态 `TransceiverStatus`：与频道快照同节奏（≤10 tick）在服务端计算，按优先级取第一个问题——无频率 → 链路被拒（`LabelLink.lastAttemptFailed()`）/连接中 → `IPathingGrid.getControllerState() == CONTROLLER_CONFLICT` → `isNetworkBooting()` → `IEnergyGrid.isNetworkPowered()`。以 `status` 字节随描述包同步，Waila 与 GUI 只读客户端值。

### 3.4 防串频道与节点可见性
- 收发器 `isWorldAccessible() = true`：确保 AE2 原版 `ToolNetworkVisualiser` 将其识别为真实物理方块节点加入 `VNode` 列表
- 防串频道保护：在 `getGridNode(ForgeDirection dir)` 中检查方向上的邻居方块实体，若为其他收发器则直接返回 `null`，彻底杜绝相邻收发器直接互连
- `VirtualLabelNodeHost` 保持 `isWorldAccessible() = false`，作为内部虚拟中继节点，不暴露在世界物理网格中
- `maintainLocalConnections()` 手动扫描 6 方向 IGridHost 建连，**跳过其他收发器**；建连前按 AE2 `GridNode.canConnect` 的同等规则检查对方 `getConnectableSides()` 是否包含朝向本方块的一面、`AEColor.matches` 颜色是否相容（`acceptsConnection`）。否则 GT ME 仓室（只开放正面）会被我们手动接上，又在它自己的 `FindConnections` 里被拆掉，反复闪断。
- `node.updateState()` 与邻居扫描：首 tick、`onNeighborBlockChange`/`onNeighborChange` 触发时立即执行，其余每 20 tick 兜底一次（以前每 tick 执行）。

### 3.5 频道占用统计（玩家可见口径）
- 从收发器本地节点沿真实 `IGridConnection` 做 BFS，只统计真正接在该导线分支上的节点。
- 每个带 `REQUIRE_CHANNEL` 的普通节点计 1；不检查 `isActive()` 或已分配频道，因此缺频道的第 33 个设备仍会得到精确的红色 `33/32`。
- 带 `MULTIBLOCK` 的 AE2 多方块使用 `IGridMultiblock.getMultiblockNodes()` 与 AE2 路径缓存相同的节点集折叠为一次需求。
- 其他无线收发器、频率虚拟节点和 ME Controller 是不计数且不穿越的边界；无标志线缆节点不计数但继续遍历。
- 单次 BFS 最多访问 `MAX_BRANCH_NODES = 4096` 个节点，超出部分不再计入（仅巨型网络显示会偏小，换取服务端 tick 上限）。
- 全频率频道合计排除 `hasControllerOnLocalBranch()` 的端点：那一侧是频道来源，不占频率频道；在线数仍包含它们。
- 结果缓存 `serverUsedCache`：拓扑事件置 dirty 后 2 tick 合并刷新，稳定状态每 10 tick 错峰校准；Waila/GUI/全网统计只读缓存。
- maxChannels 读 `GridNode.getMaxChannels()`（rv3: `CHANNEL_COUNT[compressedData&3]` = {0, 8, 32, MAX_VALUE}；无限频道模式显示 ∞）
- 收发器节点与频率虚拟节点都固定声明 `DENSE_CAPACITY`，因此所有频率的显示容量统一为 `32`；全局关闭 AE2 频道时统一为 `∞`。周围普通线缆的 8 频道限制不会改变无线节点自身的 `/32` 显示口径。
- 全网占用 = 各端点缓存之和；超载（>max）标红、满载（==max）标黄

### 3.6 性能优化与缓存
- BFS 设备计数：事件驱动 dirty + 2 tick debounce + 10 tick 错峰兜底（Waila/GUI 不重复执行）。
- `LabelNetworkRegistry` 直接使用 overworld `MapStorage` 的实例缓存语义，不再额外持有静态 World 映射。
- `LabelNetwork` 频率统计缓存：全频率频道总数与在线收发器数下沉至 `LabelNetwork` 进行 20-tick 共享缓存，同频率所有收发器共享单次统计结果，将 $O(N^2)$ 全网扫描降低至 $O(N)$
- endpoint 反向索引使注册、解绑和缓存失效为 O(1)；频率列表由服务端搜索并按最多 128 项分页。
- AE2 playerID 按 owner 缓存。
- 视觉状态（IEnergyGrid 查询、meta 更新）10 tick 节流；连接日志 DEBUG 级

### 3.7 AE2 Network Visualiser 无线拓扑合成架构
- **真实网格与可视化解耦**：
  - 底层 AE2 Grid 拓扑：每个收发器仅与该频率的 `VirtualLabelNodeHost` 保持一条真实的 `IGridConnection`；绝不创建 `Transceiver A <-> Transceiver B` 的真实物理连接，避免网格循环回路与频道紊乱。
  - 客户端 3D 拓扑渲染：通过 `MixinToolNetworkVisualiser` 在 `ToolNetworkVisualiser.onUpdate` 服务端发送封包前拦截数据，由 `NetworkVisualiserCompat` 从当前视角 `World` 中已有的 `VNode` 列表过滤出有效频率节点，按坐标字典序升序生成确定的 $N-1$ 阶星型生成树（Spanning Star Tree），连线标记为 `VLinkFlags.DENSE`，并填入各收发器连接虚拟节点的实际无线占用频道数。
  - 星型中心：真实中心是频率虚拟节点，它没有世界坐标，因此用一个收发器代替。优先选本地分支能到达 ME Controller 的收发器（频道来源侧，由频道统计 BFS 顺带缓存为 `hasControllerOnLocalBranch()`），有多个时取坐标最小者；都没有时才退回坐标最小者。若纯按坐标选，中心会随机落在远处多方块旁的收发器上，看起来像主网被隔离（2026-10 玩家实测误报）。

### 3.8 服务端安全与权限模型
- **防盗与权限校验**：`TransceiverSecurity.canManage(player, te)` 保证未锁定收发器可正常交互；锁定收发器仅拥有者（Owner UUID）与服务器管理员（OP）可进行扳手拆卸、切换解锁与 GUI 频率管理。
- **距离校验**：Container 与所有 C2S 操作数据包（`LabelApplyPacket`、`LabelListRequestPacket`）严格校验玩家与目标方块的欧几里得距离平方（$\le 64.0\text{D}$，即 8 格以内）。
- **主线程边界**：Netty handler 只解码并进入有界队列；世界、容器和注册表操作全部在服务端 tick 线程重新解析玩家后执行。
- **限流与容量**：写操作每玩家 4/s（burst 8），列表 2/s（burst 4）；队列同时限制全局、每玩家容量和每 tick 消费量。
- **字节流安全**：`NetworkBufferUtils` 校验字符串长度边界与可读字节数；删除使用最多 128 项的单个批量包，列表响应最多 128 项。

### 3.9 频率删除生命周期与墓碑机制 (Band Deletion Lifecycle & Tombstone)
- **区分临时卸载与永久移除**：
  - 区块卸载（`onChunkUnload`）：仅销毁本地 GridNode 并断开运行时无线连接，**在 `LabelNetworkRegistry` 中保留 `EndpointRef` 索引**，不注销端点；
  - 真正方块拆除（`breakBlock` / 扳手拆卸 / 永久销毁）：执行完整 `cleanup(true)`，从注册表中 `unregister(this)` 注销 `EndpointRef`；
  - 统计遍历安全：`updateStatsIfNeeded()` 使用 `world.blockExists()` 严格跳过未加载区块，绝不强制加载区块。
- **已加载端点立即清除**：删除频率时快照 `net.endpoints`，已加载区块中的收发器立即调用 `clearLabelAfterNetworkDeletion()`，断开真实无线连接并重置频率与显示标签。
- **未加载端点持久化墓碑**：由于未加载端点在区块卸载期间仍保留在 `endpoints` 中，删除频率时可准确将其捕获并记录在 `LabelNetworkRegistry` 的 `pendingClears`（墓碑集合），随世界 NBT 持久化存储。
- **杜绝已删除频率复活**：离线端点在未来区块加载并执行 `refreshLabel()` 时，首先检查并消耗对应的墓碑标记，安全清空本地标签与频率，绝不自动重新注册已删除的频率。
- **所有者与坐标严格隔离**：墓碑包含 `(dim, x, y, z, label, owner)`，相同坐标新建其他频率或不同所有者的同名频率绝不发生误清。

### 3.10 运行时配置语义 (Runtime Config Semantics)
- `wireless.wirelessCrossDimEnable`：参与 `LabelNetwork.Key` 的维度哈希与底层 AE2 网格定位，**标记为 `setRequiresMcRestart(true)`**；游戏内修改保存到配置文件，但运行时活动值仅在游戏/服务端启动时加载一次，避免产生跨维混合死锁。
- `wireless.wirelessTransceiverIdlePower`：默认 `10.0 AE/t`，必须是有限非负数；非法值恢复默认值。支持游戏内 Config 界面即时修改并实时生效。
- `wireless.wirelessMaxBandsPerOwner`：默认 `128`、范围 `0..10000`，限制一位所有者在当前频率作用域内创建的新频率数量。
- `wireless.wirelessMaxBandsPerWorld`：默认 `4096`、范围 `0..100000`，限制该世界存档的频率总量。
- 两个上限都采用“创建时”语义：`0` 禁止新建；连接已有频率、加载/修复已有端点不消耗新配额；降低上限不会删除已有数据。负值与超界整数钳制到边界并记录警告，非整数恢复默认值。
- `wireless.pruneMissingTransceivers`：默认 `true`，见 3.13。
- `client.*`（外观，只在客户端起作用）：`defaultTheme`（`dark`/`light`，缺省 `dark`，非法值恢复 `dark`）、`panelOpacity`（默认 `100`，`0..100`，按 10% 一档取整）、`glassArcs`（默认 `true`）、`renderEffects`（默认 `true`）、`effectRenderDistance`（默认 `32`，范围 `8..128` 格）、`simpleBlockModel`（默认 `false`）、`locateSeconds`（默认 `60`，`5..600`）、`locateThroughWalls`（默认 `true`）。在游戏内 Config 改动 `defaultTheme`、`simpleBlockModel`、`panelOpacity` 或 `glassArcs` 会重建已渲染区块。
- 游戏内 Config 先把修改写进内存中的 `Property`，再发 `OnConfigChangedEvent`；处理器调用 `syncValues(false, false)`，**不能**再 `config.load()`，否则会从文件读回旧值、丢掉刚改的内容（v1.1.0 及以前的问题）。`/ae2wtx reload` 用 `syncValues(false, true)` 从文件读取。`client` 分类直接放在配置界面第一页，`wireless` 分类在子页。
- 客户端 Config 事件只在客户端代理中注册到 FML bus，避免 common/server 类路径引用客户端事件。它可即时刷新本地/集成服的功耗与上限；专用服务器修改 `config/ae2wtx.cfg` 后可用权限等级 2 的 `/ae2wtx reload` 即时刷新这些安全运行时值。跨维设置仍需完整重启。

### 3.11 数据包解码防御与空字符串语义区分 (Packet Decoder Hardening)
- 严格区分“合法空字符串”（代表玩家主动清空标签/断开连接）与“畸形异常数据包”（长度为负数、超过上限或超出缓冲区可读字节数）。
- `NetworkBufferUtils.tryReadUtf8` 在格式异常时返回 `null` 并将数据包标记为 `valid = false`，服务端/客户端 Handler 直接丢弃该数据包，绝不将其误当作断开连接或篡改状态。
- `LabelListRequestPacket` 携带页号、页大小和查询串；服务端把页大小钳制到 `1..128`，响应同时校验分页元数据与 `count` 上限，异常时整包丢弃。

### 3.12 收发器定位 (Locate)

- 请求：`LabelEndpointsRequestPacket`（C2S）与列表请求走同一条校验（打开中的容器、8 格内、`canInteractWith`）和 `LIST` 限流队列；服务端 `LabelNetworkRegistry.listEndpoints` 按收发器所有者取该频率的已保存端点，排序（维度→x→z→y）后最多返回 512 个，同时给出真实总数。
- 维度名在服务端解析（已加载世界的 provider，否则 `DimensionManager.createProviderFor`，失败回退 `DIM n`），随 `LabelEndpointsResponsePacket` 下发，客户端不需要认识其他维度。
- “在线”取自 `LabelNetwork.loadedEndpoints`：区块已加载且持有运行时链接。
- 客户端 `TransceiverLocator` 按维度保存目标，`RenderWorldLastEvent` 中关闭深度测试画脉动线框，仅渲染玩家当前维度的目标；新一次定位替换旧目标，`client.locateSeconds` 秒后清除（默认 60）；`client.locateThroughWalls=false` 时保留深度测试。聊天栏按“本维度按距离、其他维度按 id”排序列出前 8 条，坐标为 `SUGGEST_COMMAND`：客户端装有 `serverutilities` 时为 `/tpl x y+1 z dim`，否则仅本维度给出 `/tp x y+1 z`。命令只填入聊天栏，是否有权限执行由服务端决定。

### 3.13 收发器名字与失效端点清理

- 名字：`TransceiverNames.clean` 去掉 `§` 格式代码（连同后一个字符）、控制字符和 BMP 以外的字符，去首尾空白，最长 32 个字符（网络上限 96 字节）；空名字即无名字。TE 保存在 NBT `name` 并随描述包同步；注册表在每个端点的 NBT 里另存一份 `name`（`endpointNames`），使未加载的收发器也能在定位列表中显示名字。端点被移除、频率被删除时名字一并丢弃。
- 改名：`TransceiverRenamePacket`（C2S）走 `MUTATION` 队列和与主题包相同的校验；锁定时需 `TransceiverSecurity.canManage`。放置时物品有显示名（铁砧改名或扳手拆卸写入）则作为名字。
- 清理：`LabelNetworkRegistry.pruneEndpoints(isMissingInLoadedChunk)` 在处理收发器列表请求前和服务端每 600 tick 执行。只有维度世界已加载、`chunkExists` 为真、且该位置方块不是收发器时才删除记录，因此不会加载区块，也不会误删远处未加载的收发器。存档中的这类记录是旧版本留下的（RC1 存档中 39 条有 17 条，其中 13 条在 2026-08 的备份里就已存在）。

## 4. 架构

```
cn.gtnh.ae2wtx
├── AE2Wtx / CommonProxy / ClientProxy      # 主类与代理
├── command/AE2WtxCommand                   # 专服运行时安全配置重载
├── client
│   ├── config/ClientConfigChangedHandler   # 客户端 FML ConfigChangedEvent
│   ├── gui/                                # Mod 列表配置界面
│   ├── locate/TransceiverLocator           # 定位高亮（RenderWorldLastEvent）与聊天坐标
│   └── screen/LabeledTransceiverGui        # 频率管理 GUI（EAEP 布局）
├── compat/                                 # WailaProvider / WrenchHandler
├── config/ModConfig                        # Forge Configuration + 配置 GUI 支持
├── content/wireless/                       # 方块 + TE（节点生命周期/统计/视觉）
├── gui/                                    # ModGuiHandler / LabeledContainer
├── init/                                   # 注册（方块/TE/创意标签）
├── network/                                # 分页包、服务端任务队列、限流与校验
└── wireless/                               # LabelNetworkRegistry / LabelLink / IWirelessEndpoint
```

## 5. 渲染与外观

外观为“设计 7”：12 条 2 格宽边框；每个面上从边框内沿到玻璃有一圈 45° 浅斜面（宽 1、深 1，外沿亮折边、内沿发光缝），玻璃面板在 1 格深处、跨 3..13；中央 6×6 接线面板（5..11）从表面一直伸到玻璃。机壳内部是黑洞特效与频道光点。

- **机壳：`client/render/TransceiverBlockRenderer`（ISBRH）**。斜面是斜切梯形，JSON 方块模型表达不了，所以不用 GTNHLib，直接用 Tessellator 写四边形
  - 方块 `getRenderBlockPass()=1`、`canRenderInPass` 对 0/1 都返回 true，按 `ForgeHooksClient.getWorldRenderPass()` 分两遍：pass 0 画边框、斜面、接线面板和中心黑色替身方块；pass 1 画玻璃，在线时再在玻璃外 0.02 格叠一层满亮度电弧
  - `QuadWriter` 统一写四边形：坐标为模型单位（每格 16），面纹理投影与 JSON 方块模型一致（u 自外向内看从左到右，v 向下），并按法线自动修正绕序，保证剔除背面时正面保留。世界内按原版平面光照把面阴影（上 1.0、下 0.5、南北 0.8、东西 0.6；斜面按法线分量平方加权）乘进顶点色；物品栏改为设置法线交给 GL 光照
  - 位于方块边界上的面取相邻方块亮度，并按 `shouldSideBeRendered` 剔除；内部面取本方块亮度
  - 玻璃是单面四边形：从外面看只显示朝向镜头的那几块，后面的被剔除，与 mock 一致
  - 电弧动画为现代 AE2 `quantum_ring_light` 风格（32 帧、frametime 2），六个面各转 90° 或镜像，避免同步闪烁
  - 接线面板不透明度：`client.panelOpacity` 四舍五入到 10% 一档。100% 时在 pass 0 不透明绘制；10%~90% 时改到 pass 1，换用贴图本身带透明度的 `port_on/off_aNN.png`，并省去贴在玻璃上的背面；0 时不画。透明度不能放在顶点颜色里：GTNH 的 Angelica（Celeritas）地形渲染器把顶点 alpha 当作明暗系数，半透明的顶点色只会让面板变暗。地形四边形的顶点 alpha 一律为 1。`client.glassArcs=false` 时不画电弧
  - 物品（物品栏、手持、掉落物）没有 TE，画不出黑洞；渲染器在玻璃后 0.1 格处补一个深色内胆，否则物品是空心的，透过玻璃能看到身后的世界
  - 中心 ±1.2 的黑色小方块恰好藏在黑洞阴影球里；特效关闭或超出特效距离时，它代替黑洞，避免能直接看穿方块
- **黑洞与光点：`client/render/TransceiverEffectsRenderer`（TESR）**，几何按 0.95 缩放以免碰到玻璃
  - 朝向镜头构建：黑色阴影球；吸积盘绕镜头右轴倾斜 12°；盘的远侧经引力透镜弯到阴影上方和下方；外加光子环和更暗的次级环
  - 物理效果：多普勒增亮与色移（β=0.35，迎面一侧更白更亮，背离一侧偏红偏暗）、内缘引力红移、开普勒差速旋转（内圈转得快，每 80 tick 一个循环）
  - 8 个光点分布在 3 条倾斜轨道上，代表频率容量：点亮数 = ⌈8 × 频率合计频道 / 容量⌉（容量无限时每 4 个频道一个），其中前 ⌈8 × 本机频道 / 容量⌉ 个为本机份额，尺寸和亮度全开，其余缩小、变暗；颜色表示频率负载（紫色 < 75%，橙色 75%~99%，红色 ≥ 100%，见 `TransceiverVisuals`）
  - GL：关闭纹理与光照、光照图满亮；先写深度画阴影球和未点亮的光点，再以加色混合、不写深度画盘、弧、环、光点和泛光。它在地形 pass 1 之前运行，所以玻璃会叠在黑洞上
  - 超过 `client.effectRenderDistance` 或关闭 `client.renderEffects` 时不画
- **主题**：深色与浅色（L1 珍珠白 + AE 青）两套贴图。每台收发器在 TE 中保存 `theme`（默认 / 深色 / 浅色，NBT `theme`，随描述包同步）；“默认”取客户端配置 `client.defaultTheme`（缺省为 dark）。界面标题栏右侧图标按钮循环切换，经 `TransceiverThemePacket` 由服务端校验（窗口、距离，锁定时需管理权限）后写入。扳手拆卸时非默认主题写进物品 NBT `ae2wtxTheme`，放下时恢复
- **回退**：`client.simpleBlockModel=true` 时用原版 `renderStandardBlock` 画普通方块，不画机壳和特效；`getIcon` 的 `fallback_off/on` 也用于破坏粒子
- **可选的旧外观材质包**：资源包提供 `assets/ae2wtx/models/classic/transceiver_off.json` 和 `_on.json` 时，`ClassicModel` 在贴图集重建时加载，机壳改画该 JSON 模型（支持轴对齐元素、逐面 uv/texture/rotation、`light_emission`；不支持元素旋转；`render_type: translucent` 在 pass 1 画），此时不画特效。`python tools/packs/build_packs.py classic` 从 Git 历史（`31a0e73`）取出 v1.1.0 及以前用的 ExtendedAE_Plus Light Mode 模型和贴图，在 `build/resourcepacks/` 生成 `AE2WTX-Classic-Look.zip`；这些第三方素材不回到仓库或发布包。`python tools/art/preview.py <包>` 可离线预览
- **贴图**：`python tools/art/generate_assets.py` 生成 `textures/blocks/transceiver/{dark,light}/` 下的边框（32px）、斜面与接线面板 on/off（32px）、玻璃（64px）、电弧帧条（64×2048）和普通方块回退图（32px），以及 GUI 贴图；不要手改生成结果
- 自发光：`getLightValue(IBlockAccess,...)` 在线 15/15，离线 0；状态翻转时 `updateLightByType` 重算
- **音效**：扳手拆卸播放 `ae2wtx:wrench.disassemble`（音量 0.7、音调 1.0，与现代 AE2 的 `WrenchHook` 一致）。默认文件由 `tools/art/generate_sounds.py` 合成，模仿现代 AE2 所用的 `entity.item_frame.remove_item`；Mojang 的原声不能随仓库分发，`python tools/packs/build_packs.py sounds <Minecraft assets 目录>` 从玩家本机已有的新版 Minecraft 资源中取出这 4 个文件，生成覆盖用的 `AE2WTX-Modern-Sounds.zip`
- GUI：`textures/gui/transceiver.png`，256×198 面板（三排六个按钮），按钮精灵在 (0,200)/(0,216)/(0,232)/(56,200)（常态/悬停/禁用/待确认删除）；标题栏 12×12 按钮（主题、改名）底图在 (112,200)/(124,200)，8×8 图标在 v=212（u=112 深色、120 浅色、128 默认、136 改名铅笔）。`LabeledTransceiverGui` 的布局常量与生成脚本中的 `SEARCH`/`LIST`/`SCROLL`/`INFO`/`SCREEN_BOX`/`BUTTONS`/`ICON_BTN_SPRITES`/`GLYPHS` 一一对应，改一边必须同步另一边

## 6. 已知限制

- 机壳只有 off/on 两态（meta 0/1）；频道占用和频率负载由黑洞周围的光点表示，精确数字仍看 Waila/GUI
- 网络工具无线连线的 Mixin 是可选的（`required: false`、`require = 0`）：AE2 改动 `onUpdate` 后只会少画无线连线，不影响启动。
- **客户端网格模拟**不可用（rv3 API 禁止第三方客户端建节点）——第三方方块贴线缆的连接点显示与 AppEU 等同类 mod 一致
- 玩家可见频道数采用实际导线拓扑上的需求口径；它有意显示需求而非已分配数，以便超载时仍显示 `33/32`
- CI 覆盖编译、自动化测试、静态检查与 Java 8 字节码检查；当前项目不要求目标 GTNH 客户端或专用服务器烟雾测试作为 beta3 合入与发布前置条件
- 早期一版手写 `ISimpleBlockRenderingHandler` 曾出现缺面、透明和物品栏异常，后来改用 GTNHLib；现在的 ISBRH 统一由 `QuadWriter` 按法线修正绕序、玻璃单独放在 pass 1、物品栏显式开启混合，用来避开这几类问题

## 7. 交互决策记录

- 右键 = 打开 GUI；扳手右键 = 锁定；扳手潜行右键 = 拆卸（AE2 式地面掉落）
- GUI 删除只作用于选中项，需在 60 tick 内对同一选中集合点两次；点击按钮区域不清空选择（否则无法确认）。打开期间每 40 tick 重新请求当前页。
- 所有频率/频道操作均通过 GUI 完成（早期版本的数字频率增删交互已废弃）
- 频道卡（设备级直连）与 plain 数字频率收发器已移除（与从端收发器功能冗余），仅保留单一收发器

## 8. 开发计划

### 8.1 面板形态（AE2 部件）——已确定方向，未开工

动机：只需一两个频道的机器（例如 GT ME 仓室）现在要摆一整格收发器、再拉一根线缆过去，占地且不美观。2026-10-25 与用户讨论后选定做成 AE2 部件，而不是小型节点方块：部件可以直接贴在仓室的 ME 面上，线缆和那一格都省掉；节点方块仍占一整格，只是看起来小。

- **形态**：`IPart` / `IPartItem`（rv3-beta-1050-GTNH 的 `appeng.api.parts` 均已确认存在），用法同 P2P 通道或 ME 接口面板：贴在线缆上，或直接贴在机器面上（AE2 在相邻空格生成线缆总线）。
- **与方块互转**：工作台 1:1 无序合成，方块 ↔ 面板。合成不携带 TE 数据，名字与主题不跨形态保留（与镐子挖掉一致）。
- **共用逻辑**：频率、所有者、锁定、命名、定位、状态诊断与方块形态共用同一套 `LabelNetworkRegistry` 与 GUI；部件实现 `IWirelessEndpoint`。
- **频道**：部件节点随所在线缆计算（普通 8、致密 32）；贴在机器面上时向该面手动建立连接，复用方块形态对 GT ME 仓室的可连接性与颜色检查。
- **存档**：一格线缆总线最多 6 个部件，端点键需要加上朝向（`EndpointRef` 增加 side，旧数据视为方块形态）。须有读旧存档、写新存档、混合形态的单元测试，并在隔离存档上验证。
- **外观**：薄面板加在线指示灯，不做黑洞特效；需单独实现部件渲染、碰撞箱、Waila、扳手拆卸、右键开 GUI。
- **风险**：工作量明显大于此前改动；向机器面手动接线必须进游戏确认。按“离线测试尽量覆盖、一次交付验收”的方式推进。
