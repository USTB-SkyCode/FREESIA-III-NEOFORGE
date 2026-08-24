# Freesia NeoForge (Velocity)

类 **Freesia II** 的 YSM 代理，面向 **NeoForge 1.21.1 + YSM 2.6.5**。它把 `yes_steve_model` 流量从后端改道到一个专门的 **Worker 节点**，并在两侧做实体 ID 重映射，从而绕过 YSM 模型同步对"服务端直连 Netty 连接"的依赖。

> 纯转发插件修不了 YSM 模型同步（YSM 用服务端 `connection.channel().unsafe().outboundBuffer()` + 严格连接处理器类名检查做模型流控）。本插件复刻 Freesia II 的"代理终结协议 + 路由到 Worker + 实体 ID 重映射"思路。

---

## 架构

```
真实 NeoForge 客户端 (YSM 2.6.5)
        │  MC 协议
        ▼
┌───────────────────────────────────────────────────────────┐
│                      Velocity 3.5.1                        │
│  ┌──────────────────────────────────────────────────────┐  │
│  │  freesianeo 插件                                     │  │
│  │   · 拦截 yes_steve_model:2.6.0（handled）            │  │
│  │   · 每个玩家一条 MapperSession（连 Worker）           │  │
│  │   · 实体 ID 重映射（Worker ↔ 后端）                   │  │
│  │   · PacketEvents 读后端 JOIN_GAME 的 entityId        │  │
│  └──────────────────────────────────────────────────────┘  │
└──────────────┬──────────────────────────────┬─────────────┘
      MCPRotocolLib 假客户端连接               正常 MC 协议
               ▼                               ▼
   ┌───────────────────┐            ┌────────────────────────┐
   │ Worker 节点        │            │ 你的 NeoForge 主服       │
   │ NeoForge 1.21.1    │            │ (1.21.1，玩家实际游玩)    │
   │ + YSM 2.6.5        │            │ (卸掉 YSM / 关闭其同步)   │
   │ 纯算模型，不进列表   │            │ 保留 NeoVelocity         │
   └───────────────────┘            └────────────────────────┘
```

---

## 目录结构

```
src/main/java/com/nguyendevs/freesia/neoforge/
├── FreesiaNeoForge.java      主类：装配、拦截 PluginMessage、PacketEvents 读 entityId
├── FreesiaConfig.java        worker_address / debug
├── YsmConstants.java         通道 yes_steve_model:2.6.0 + 包 ID
├── mapper/
│   ├── MapperManager.java    会话管理 + worker↔backend 实体 ID 映射表
│   └── MapperSession.java    假客户端连 Worker（MCPRotocolLib）
├── proxy/
│   └── YsmPacketProxy.java   包改写（S2C 实体 ID 重映射、握手版本记录）
└── util/BufUtil.java         VarInt/UTF/UUID 读写
```

---

## 构建

```shell
gradlew.bat build        # 产物: build/libs/Freesia-NeoForge-Velocity-1.0.0.jar
```

依赖：
- `velocity-api`（运行时由 Velocity 提供）
- `packetevents-velocity`（**需另外安装 PacketEvents 这个 Velocity 插件**，用于读后端 JOIN_GAME 的 entityId）
- `mcprotocollib`（打包进 jar，用于连 Worker）
- `netty` / `slf4j`（运行时提供）

---

## 部署

### 1. Worker 节点（纯 NeoForge 1.21.1 服务端，**无需任何 mod**）

> 原 Freesia-Worker 是一个 NeoForge/Fabric mod，但当前 NeoForge 开发环境依赖的 `minecraft-dependencies` 构件已被官方删除，mod 无法编译。测试阶段改用"服务器配置 + 代理保活"顶替它。

1. 新建一个 NeoForge 1.21.1 服务端目录，**只装 YSM 2.6.5**；
2. `server.properties` 关键配置：

   ```properties
   online-mode=false
   difficulty=peaceful
   spawn-monsters=false
   level-type=minecraft:flat
   view-distance=2
   simulation-distance=2
   spawn-protection=0
   ```

3. **不要**把它加进 Velocity 的 `servers` 列表（代理会主动直连它）；
4. 启动它。假客户端会以旁观/和平环境待在平坦出生点，靠代理侧 keep-alive 保持连接。

### 2. 主服（后端）

1. **卸掉 YSM**（或至少让它不再对外同步模型），避免和 Worker 双重计算；
2. 保留 NeoVelocity（现代转发，管 UUID/皮肤）。

### 3. Velocity

1. 安装 **PacketEvents** 插件；
2. 把本插件 jar 放进 `plugins/`；
3. 启动后编辑 `plugins/freesianeo/config.properties`：

   ```properties
   worker_address=localhost:25566
   debug=false
   ```

4. 重启 Velocity，日志出现 `[Freesia] Enabled. Worker = ...` 即正常。

---

## 当前实现范围（第一版骨架）

| 能力 | 状态 |
|------|------|
| 连 Worker（假客户端，MCPRotocolLib） | ✅ |
| 拦截 `yes_steve_model:2.6.0` 双向 | ✅ |
| 后端 JOIN_GAME entityId 读取（PacketEvents） | ✅ |
| S2C 实体 ID 重映射（set model 4 / animation 21 / molang 3） | ✅ |
| 握手版本记录（51/52） | ✅（透传） |
| C2S 实体 ID 重映射（animation 7 / molang req 17） | ⬜ 未做（先 PASS） |
| 跨服多后端可见性路由（tracker） | ⬜ 未做（单后端场景暂用不到） |

> ⚠️ 这是**未编译验证的第一版骨架**。实体 ID 重映射的字节偏移按 Forge 1.20.1（OpenYSM）同款格式推断，NeoForge 1.21.1 若有差异需按实际抓包微调 `YsmPacketProxy` 里的偏移。

---

## 下一步

1. 编译并跑起来，开 `debug=true` 看日志确认：Worker 连接成功、两边 entityId 都拿到、模型包在转发；
2. 如果模型仍不显示，抓 Worker/客户端两侧 `yes_steve_model:2.6.0` 原始包，核对 `YsmPacketProxy` 的实体 ID 偏移；
3. 再补 C2S 重映射和（多后端时的）tracker 可见性路由。
