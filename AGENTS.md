# AE2 Wireless Transceiver (GTNH) 协作规则

- 这是 GTNH 2.9.0（Minecraft 1.7.10）的 Forge Mod 移植工程：支持 RC1 与 beta3，构建依赖沿用 beta3 基线（见 `docs/TECHNICAL.md`）；beta2 仅为历史适配阶段。参考源码克隆仅用于对照，不作为规范源。
- 先读 `README.md`、`docs/TECHNICAL.md` 和当前分支状态，再改代码；方块外观由自带 ISBRH/TESR 绘制、不依赖 GTNHLib，保留 `client.simpleBlockModel` 原版方块回退。
- 构建优先使用仓库内 `gradlew.bat`；`build/`、`run/`、`.gradle/`、`logs/` 只属于本地产物，不提交。
- 不直接修改真实 Minecraft 实例、world 或服务端部署目录；发布前必须有隔离构建和哈希证据。
- 主线是 `master`（跟踪 `origin/master`）；已推送的提交历史不改写。本地 `local-history/pre-rewrite-20260921` 是 2026-10 远端历史重写前的旧 master，只作留存。任何未提交用户修改必须原样保留。
