# Fuream — Spigot/Paper 移植版

Fuream（"熔炉格子扩容 & 经验累积 & 配方冲突解决，三合一"）原为 **Fabric 1.20.1 服务端模组**（`/tmp/workspace/Fuream`）。本工程是它的移植：引擎 / GUI / 漏斗 / 配置使用 Bukkit API；持久化使用 NBT-API，并在炉子方块实体的读写方法上安装轻量字节码钩子，把数据写入方块实体**真实根 NBT**，NBT 路径与原 Fabric 实现一致。

## 功能（与原模组 1:1）

- **熔炉格子扩容**：每座熔炉拥有可配置的虚似行列（默认 9 输入 / 9 燃料 / 9 输出），实际 27+ 格空间。玩家打开的是自定义 9x6 玻璃面板 GUI（燃料条 / 进度条 / 边框 / 翻页）。
- **经验累积**：每次合成把配方经验累积到熔炉上，不自动发放；点击 GUI 第 6 行第 5 格（绿色经验指示器）或拆炉时作为经验球发放。
- **配方冲突解决**：把原料放进 GUI 第 6 行第 1 格（配方设置槽），用 46/47 两个按钮在冲突配方之间切换，结果预览在第 48 格；选择按原料持久化（`overriddenRecipes`）。
- **漏斗/自动化**：漏斗按列交互——上方喂输入、侧方喂燃料、下方抽取产出（走香草槽作为"通道"），空桶余物会被收回。
- **每世界配置**：`<世界文件夹>/fuream.json`，字段/语义/默认惰性行为与原模组完全一致。
- **破坏掉落**：拆炉掉落全部虚似行物品 + 经验。

## 兼容性与依赖

- Spigot 1.20.1 与 Paper 1.20.1（`paper` 内核为超集，直接可用）。
- 目标字节码 Java 17（`options.release = 17`），可用 JDK 17+ 编译/运行。
- **运行时依赖：NBT-API 插件（de.tr7zw Item-NBT-API ≥ 2.13）**，需放入 `plugins/`。本插件通过 `depend: [NBTAPI]` 声明依赖；Byte Buddy 与 agent 已打入插件 jar，无需另装。
- Spigot/Paper 的香草方块实体会丢弃未知根标签，因此不能仅调用 `NBT.modify(BlockState)`。插件会在 `onLoad` 阶段动态安装炉子 `load/save` 钩子；安装失败时插件会拒绝启用，绝不退回 PDC。Java 21 会打印动态 agent 警告，可在启动参数加入 `-XX:+EnableDynamicAgentLoading` 消除该警告。

## NBT 路径（与原 Fabric 实现一致）

数据存于方块实体（熔炉 TileEntity）的真实 NBT，不用 PDC：

```
(Furnace BlockEntity NBT 根)
├─ FureamData                       复合标签（仅当 data.hasAny() 时写）
│  ├─ Inputs                        List<Compound> { 物品NBT…, Slot:int }
│  ├─ Fuels                         List<Compound> { 物品NBT…, Slot:int }
│  ├─ Outputs                       List<Compound> { 物品NBT…, Slot:int }
│  ├─ RecipeOverridingInput         Compound 物品NBT（空物品时为 {}）
│  ├─ OverriddenRecipes             List<Compound> { 物品NBT…, RecipeId:string }
│  ├─ RunningRecipe                 String（可选，当前熔炼配方 id）
│  └─ Xp                            Float
├─ BurnTime / CookTime / CookTimeTotal   Short（香草 BE 根部字段，与原模组
│                                       ——由香草引擎写入——同路径）
└─ Items                            （香草熔炉原 3 格物品栏，作自动化通道）
```

- `FureamData` 复合标签、内部各键名、item `Slot`/`RecipeId` 键、`Xp` 类型均为原 Fabric `FureamFurnaceData.writeNbt/readNbt` 的原样结构。
- 旧 Spigot 版本误写入 `PublicBukkitValues` 的数据会在首次加载方块时自动迁移到根 NBT，并移除 Fuream 自己的旧 PDC 键；其他插件的 PDC 不受影响。
- 要迁移回 Fabric 时，请先在 Fuream Spigot 插件仍启用的情况下正常停服，然后直接用 Fabric 打开该存档。不要先卸载插件再让 Spigot/Paper 加载并保存区块；香草方块实体不识别 `FureamData`，可能在下次保存时将它丢弃。
- `BurnTotal`（相当于香草 `fuelTime`）按香草行为**不持久化**（重载后重新点燃）。

## 更新日志 — Fuream-update1（跟随上游）

已按 `Fuream-update1`（新版 Fabric 实现）跟进移植的功能：

- **香草物品迁移**：熔炉无可用的 `FureamData` 标签时（升级前/被接管前是普通香草熔炉），把其香草 3 格物品栏并入虚似行（槽0→输入、槽1→燃料、槽2→输出），并清空通道防重复（对应新版 `AbstractFurnaceBlockEntityMixin.readNbt`；实现在 `FureamDataCodec.migrateVanillaLane`，由 `FurnaceManager` 注册时调用）。
- **清空语义**：清除虚似行时同步清空配方设置输入（对应新版 `clear()`；`FureamFurnaceData.clearRows()`，拆炉时使用）。
- **tick 头部保证虚似行可用**：引擎每 tick 在熔炼前 `data.ensureRows(...)` 已覆盖（对应新版 `extendInventories` 提前到 `tick` HEAD；逻辑不变，无需另行移植）。

上游其余改动均为文档/空白/未用 import 清理，无行为差异。

## 更新日志 — Fuream-update2（跟随上游）

- **可配置 GUI 外观 + i18n**（核心功能）：`FureamWorldConfig` 扩展”外观“（10 个物品 id，每类型）与“i18n”（20 条标题/提示，含 `%d`/`%.1f` 占位符）；`fuream.json` 新增 30 个 `gui_*` 键，支持单值作用于所有类型或按类型对象。默认值与 Fabric 新实现一致（含燃料条提示文本由 “Remaining:” 改为 “Available: %d / %d”）。
  - 移植：`api/FureamWorldConfig`（默认值 + 映射）、`api/WorldConfigLoader`（两种形式的解析）、`screen/FurnaceGui`（按配置解析物品 id → `Registry.MATERIAL`，失败回退默认；全部标题/提示经 `String.format`）。
- **配置类重构（对齐上游）**：Fabric 把配置创建/解析迁到新类 `FureamWorldConfigImpl`（替代 `FureamMain.newWorldConfig/readWorldConfig`）；Spigot 侧本身即单一具体配置类，语义等价（默认值/解析行为不变）。
- **`FureamScreenInventory`**：取消静态玻璃片常量，改为每 GUI 从配置解析；XP 显示改用浮点缓存比较。Spigot 侧对应 `FurnaceGui` 同步改造。
- **`FureamScreenHandler.quickMove`**：改为持有原物品引用并返回移动前拷贝 —— 仅返回值语义差异，Spigot 侧为事件式快移，无需移植。
- 新增 `api/package-info.java`（文档）。

## 配置

每个世界的 `world 文件夹/fuream.json`（与原模组格式一致）：

```json
{
  "input_slot_count": { "FURNACE": 9, "SMOKER": 9, "BLAST_FURNACE": 9 },
  "fuel_slot_count":  { "FURNACE": 9, "SMOKER": 9, "BLAST_FURNACE": 9 },
  "output_slot_count": { "FURNACE": 9, "SMOKER": 9, "BLAST_FURNACE": 9 },
  "enabled_furnace_types": ["FURNACE", "SMOKER", "BLAST_FURNACE"]
}
```

- 文件缺失 → 全部惰性（不改任何熔炉，香草行为 100% 保留）。
- 槽位计数只接受正数；未知类型名被忽略；数量超过 9 自动翻页。
- **GUI 外观与 i18n（Fuream-update2）**：`gui_*` 键可整体配置每类熔炉的装饰物品与文案（边框、燃料条左右、进度条完成/剩余、上一/下一配方按钮、经验指示器、上一/下一页按钮；标题/提示含 `%d`、`%.1f` 占位符）。每个键接受两种形式：
  - 单值：`"gui_border_item_id": "minecraft:black_stained_glass_pane"`（作用于所有类型）
  - 按类型对象：`"gui_xp_indicator_item_id": {"FURNACE": "minecraft:emerald"}`（缺省类型保持默认）
  - 非法物品 id / 未知类型名被忽略并回退默认；默认值与 Fabric 实现完全一致（完整示例见 `docs/fuream.example.json`）。
- 运行中改配置用 `/fuream reload`（权限 `fuream.reload`）。
- `/fuream status` 查看各世界启用情况。

## 构建

```bash
cd FureamSpigot
./gradlew build          # 产物: build/libs/Fuream-1.0.0.jar (+ sources jar)
./gradlew test           # 单元测试（引擎/配置/布局；NBT 持久化需实机验证）
```

构建使用工程内 wrapper（Gradle 8.7，`gradle.properties` 指定 JDK 21 路径；若你的 JDK 21 路径不同请修改 `org.gradle.java.home`，或用系统 JDK 17+ 移除该行）。NBT-API 通过 `repo.codemc.io` 下载（`compileOnly`）。

## 安装 / 实机验收

1. 安装 NBT-API 插件（≥ 2.13）到 `plugins/`。
2. 把 `Fuream-1.0.0.jar` 放入 `plugins/`。
3. 启动 1.20.1 Spigot/Paper，在默认世界根目录放入 `fuream.json`（见上）。
4. `/fuream status` 应显示已启用的类型与槽位数。

验收清单见 `docs/acceptance.md`。

## 与原 Fabric 工程的文件对照

| Fabric（Fuream） | Spigot 移植 |
| --- | --- |
| `FureamMain` | `FureamPlugin` + `hook/FurnaceManager` |
| `api/FureamWorldConfig` / `WorldConfigLoader` | 同（Gson 解析 `fuream.json`，语义逐条一致） |
| `data/FureamFurnaceData` | 同（结构/字段一致） |
| `data/FureamFurnaceData.writeNbt/readNbt`（存于 BE 的 `FureamData` 标签） | `data/FureamDataCodec`（经 de.tr7zw NBT-API 写入真实 BE NBT，路径逐字节一致） |
| `mixin` 中 `FureamData` NBT 持久化注入（`readNbt`/`writeNbt` @Inject） | `nbt/FurnaceNbtInstrumentation` + `FurnaceRootNbtBridge` + `FurnaceManager` |
| `logic/FureamFurnaceLogic`（引擎部分） | `logic/FureamFurnaceEngine`（自研引擎，注：原模组把引擎注释保留为规范，此处按其复刻） |
| `logic/FureamFurnaceLogic`（燃料/配方） | `logic/FuelTable`（香草 1.20.1 燃料表，自字节码逐条导出）+ `logic/BukkitCookingRecipes` |
| `screen/FureamScreenHandler` / `FureamScreenInventory` | `screen/FurnaceGuiLayout` + `screen/FurnaceGui` + `hook/GuiListener`（Bukkit 事件版） |
| `util/MaskedInventory` / `FurnaceForHopperInventory` | 不需要（引擎直接读写虚似行；漏斗走 `hook/HopperListener`） |
| mixin（`AbstractFurnaceBlockEntity` / `HopperBlockEntity` / `AbstractFurnaceBlock` / `MinecraftServer`） | `hook/*` 监听器 + 每 tick 调度 |

## 已知取舍

- GUI 双击拾取（Double-Click 汇总）、创造模式中键取整格在自定义视图上不生效（按原模组规则已被取消）。
- 漏斗只做整格投递拦截（避免部分移动的记账复杂度）；少量不匹配物品会留在漏斗内，符合香草拒绝行为。

## 目录结构

```
src/main/java/io/github/eat_ram/fuream/
  api/       FurnaceType, FureamWorldConfig, WorldConfigLoader
  data/      FureamFurnaceData（Fabric 兼容的 NBT 数据结构）
  nbt/       炉子方块实体 load/save 字节码钩子与根 NBT 桥接
  logic/     FuelTable, CookingRecipeQuery(+Bukkit), FureamFurnaceEngine
  screen/    FurnaceGuiLayout, FurnaceGui
  hook/      FurnaceManager, GuiListener, FurnaceOpenListener, HopperListener, LifecycleListener
  util/      StackUtil, ItemStackIo(+Bukkit), KeyableItemStack, DefaultableItemList, FurnacePos
  FureamPlugin.java
src/main/resources/plugin.yml
src/test/java/...  JUnit 5 测试（无服务端依赖，全部离线可跑）
```
