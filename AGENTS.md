# AE2 Wireless Transceiver (GTNH) 协作规则

- 这是 GTNH 2.9.0-beta2 / Minecraft 1.7.10 的 Forge Mod 移植工程；不要把它与 `D:\AI\codex\project1` 或 `D:\AI\deepseekharness\project0` 的参考源码克隆混为一谈。
- 先读 `README.md`、`docs/TECHNICAL.md` 和当前分支状态，再改代码；保留 GTNHLib 可选兼容与原版方块渲染回退。
- 构建优先使用仓库内 `gradlew.bat`；`build/`、`run/`、`.gradle/`、`logs/` 只属于本地产物，不提交。
- 不直接修改真实 Minecraft 实例、world 或服务端部署目录；发布前必须有隔离构建和哈希证据。
- 当前分支 `beta3-gtnhlib-0.11.46-texture-fix` 的提交历史不改写；任何未提交用户修改必须原样保留。
