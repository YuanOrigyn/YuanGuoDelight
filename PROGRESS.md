# YuanGuoDelight 进度记录

## 当前任务
无。果冻块弹跳、CraftEngine 26.9.1 升级与 2026-11-20 的问题清单修复均已完成，等待进游戏验证（见文末记录）。

## 已完成（2026-10-18）
### 1. 方块名与液体名拆分
- 方块名保留“锅”：`block.yuanguo.<fruit>_cauldron`
  - 苹果水锅、苹果果酱锅、苹果果冻锅、淡柠檬水锅
- 新增液体名，不带“锅”：`liquid.yuanguo.<fruit>_cauldron`
  - 苹果水、苹果果酱、苹果果冻、淡柠檬水
- 已为全部 100 个语言文件补齐 67 组 `liquid.*` 键。
- 非中文语言直接复用方块名并去掉 `Cauldron` 后缀。

### 2. 液面展示物品切换名称
- 文件：`pack/configuration/items.yml`
- 397 个 `yuanguo:entity_renderer/*_cauldron*` 物品：
  - `material: cauldron`
  - `data.item_name: <!i><lang:liquid.yuanguo...>`
- 准心对着液面时显示液体名，不再显示“下界砖”，也不带“锅”。

### 3. 生成器同步
- 文件：`work/gen/Items.java`
- 新增 `liquidTag(...)`，炼药锅内容物品改用 `liquid.yuanguo.*`。
- 方块物品仍使用 `nameTag(...)`，保持“锅”。

### 4. 构建
- 已执行 `powershell -ExecutionPolicy Bypass -File build.ps1`。
- 产物：`D:\plugins-by\YuanGuoDelight\YuanGuoDelight-1.0.0.jar`
  - 大小：1,511,541 字节
- 已校验 jar 内：
  - 100 个语言文件
  - 中文 67 个 `block.*`、67 个 `liquid.*`
  - 397 个液面物品引用 `liquid.*`

## Bridge 修改备忘
- 文件：`BRIDGE_JADE_CAULDRON.md`
- 记录按 `kind` 状态切换 Jade 锅体标题所需的 Bridge 改动、插入位置、缓存处理和构建命令。
- 当前未修改 `D:\plugins-by\Yuan-CE-Bridge`。

## 已知限制
- CE 26.9.1 的方块描述 ID 是方块级，`BlockStateAppearance` 不含名称字段；`settings.name` 也只作用于整个方块。
- 因此仅靠 `YuanGuoDelight` 无法让同一个方块 ID 按 `kind` 显示不同锅体标题；需要按 `BRIDGE_JADE_CAULDRON.md` 修改 Bridge。
- Jade 客户端对探测结果有约 30 秒缓存，状态变化后标题可能延迟刷新。

## 约束
- 只修改 `D:\plugins-by\YuanGuoDelight`，不动 `Yuan-CE-Bridge` 或其他项目。
- 不改锅身模型、实体渲染方式和已确认正常的状态机。

## 已完成（2026-10-23）：果树自然生成修复
- 根因：`features.yml` 的柠檬 placed feature 使用 `minecraft:simple_block`，只写入 `half: lower`；
  世界生成直接写方块，不触发 CraftEngine 的 `onPlace`，因此不会自动补上半格。
- 旧方案（2026-10-25 已废弃）：自然生成单格幼苗（`age: 0`），等随机生长补上半格。
- 现方案：自然生成成熟的 `age: 4` 两格树，见本文末尾 2026-10-25 记录。
- 当时的 `block_column` 尝试未成功；2026-10-25 重新采用并修正（见本文末尾）。
- 放置判定：上方 `minecraft:air` + 下方 `minecraft:grass_block`，与 mod 的 simple patch 一致；
  判定放在高度图/散布步骤之后。
- 保留 mod 原参数：`rarity 32`、`count 20`、`random_offset xz ±7 / y ±3`、`WORLD_SURFACE_WG`、
  plains / sunflower_plains / meadow。

### 2. 生成器同步（防回归）
- 柠檬改为 `lemonTreeColumn(...)`，输出 `block_column` 两格（`age: 4`），见 2026-10-25 记录。
- 修正榴莲树叶：`leaf: budding` 现在映射为 `type: budding`，不再被错误折叠成 `leaves`，
  野生榴莲树保留 6% / 2% 花芽叶。
- 验证：本次只手工同步柠檬段，未重跑全量生成器（会清空 `pack/`）。

### 3. 其他自然生成审计
- 13 棵果树均为 `minecraft:tree`，树干/树叶方块、`OCEAN_FLOOR` 高度图、稀有度、数量、
  散布与生物群系均与 mod 原始 JSON 一致。
- 蓝莓丛、蔓越莓丛、哈密瓜、野生菠萝的稀有度、数量、散布与生物群系均一致。
- 未发现其他配置缺陷。

### 4. 构建与部署（2026-10-23）
- 已执行 `powershell -ExecutionPolicy Bypass -File build.ps1`。
- 当时校验结果：柠檬为 `age: 0` 单格幼苗（已被 2026-10-25 方案取代）。
- 注意：自然生成只影响新生成区块，旧区块不会回填；需新开区块验证。

## 进行中（2026-10-24）：自然生成果树树叶错乱 —— 诊断结论与当前状态

### 1. 现象
- 沼泽等生物群系里自然生成的果树，部分树叶透明/显示成别的东西，严重时整棵树错乱。
- 沙漠里概率出现杨梅树叶。

### 2. 根因（已定位，两次推翻后的最终结论）
#### 2.1 主因：CE 生成的 leaves blockstate 覆盖了原版整份文件
- CE 生成 `assets/minecraft/blockstates/<树叶>.json` 时（`AbstractPackManager.generateBlockOverrides`）：
  先看目标路径是否已有文件，有则读出 `variants` 作为底稿，删掉 `multipart`，再把自定义方块占用的状态合并进去；
  没有则以空对象开始。
- 我们包里没有提供原版底稿，所以最终产物**只包含我们占用的那些变体**：
  - `cherry_leaves` 13 个、`pale_oak_leaves` 13 个、`flowering_azalea_leaves` 20 个、`azalea_leaves` 26 个、`spruce_leaves` 13 个（原版各 28 个）。
- 原版树叶落到这些"没有模型映射"的状态上 → 半透明/不显示，这就是"半棵树透明"的直接原因。
- 对照：`cut_copper_stairs` 产物是完整 80 个变体，但**全部**指向 palm 模型 —— 说明它同样是被整份接管，只是楼梯原本就是 80 个变体全被占满，看不出缺失。
- 结论：**必须由我们自己在 `pack/resourcepack/assets/minecraft/blockstates/` 提供完整 28 变体的原版底稿**，
  CE 才会把未占用的变体保留为 `minecraft:block/<name>`。

#### 2.2 次因：与其它包 / 原版世界生成共用同一个视觉状态（串台）
- 实测 `visual_block_states.json` 中我们的树叶大量落在 `azalea_leaves` / `flowering_azalea_leaves`，
  而这些状态同时被 FarmersDelight 的 basket / jug / keg 等占用。
- Bridge 启动日志：`Found 38 visual block state(s) claimed by more than one CraftEngine block`。
- 结果：同一个状态在客户端只能映射到一个模型，谁最后写入谁生效 → 我们的树叶可能显示成篮子，反之亦然。
- 可用状态实测（`persistent=true,waterlogged=false` 非染色树叶）只有 53 个，而外观需求 51 个，几乎占满；
  `mangrove/jungle/acacia/dark_oak/oak` 是染色树叶，会叠加群系颜色，**不能**给自定义树叶用。

#### 2.3 已证伪的两个旧结论（不要再走）
- "waterlogged=true 的树叶不会渲染水" —— 错。原版 `LeavesBlock.getFluidState` 在 waterlogged 时返回水源，
  会真的渲染一层水。之前把 51 处改成 wet 池导致池溢出（156 槽 < 51 需求冲突）并让树叶带水，**已全部回退**。
- "状态池容量不足" —— 只是表象；真正的问题是整份 blockstate 被覆盖 + 状态被多方共用。

### 3. 当前磁盘状态（D:\plugins-by\YuanGuoDelight）
- `pack/configuration/blocks/leaves.yml`、`plants.yml`：已从 `work/*.bak` 回退，51 处恢复为
  `auto_state: non_tintable_leaves`（38 + 13）。
- `pack/configuration/mappings.yml`：未改，仍是 spruce / birch 的 13×2 个 `waterlogged=false` 状态声明。
- 新增（尚未构建、未验证）：`pack/resourcepack/assets/minecraft/blockstates/` 下 11 份完整 28 变体底稿，
  全部指向 `minecraft:block/<name>`：
  acacia / azalea / birch / cherry / dark_oak / flowering_azalea / jungle / mangrove / oak / pale_oak / spruce leaves。
- 备份：`work/mappings.yml.bak`、`work/leaves.yml.bak`、`work/plants.yml.bak`、`work/Blocks.java.bak`、
  `work/PROGRESS.md.bak`。

### 4. 服务端现状（重要）
- 测试服上跑的仍是**旧的 waterlogged 版本**（坏的），日志里能看到
  `视觉状态组"waterlogged_non_tintable_leaves" 已达到最大容量` 和
  `configured_feature.yuanguo:tree/kiwi_tree / durian_tree 无效的已配置的地物`。
- 也就是说：**必须先重新构建并部署，才可能看到修复效果。**

### 5. 下次继续的步骤
1. 构建：`powershell -ExecutionPolicy Bypass -File build.ps1`（产物 `YuanGuoDelight-1.0.0.jar`）。
2. 停服 → 复制 jar 到 `D:\服务器测试\yfh.zip\yfh\plugins\` → 删除
   `plugins\CraftEngine\cache\visual_block_states.json` → 起服。
3. 检查：
   - 日志无"最大容量"、无"无效的已配置的地物"；
   - `resource_pack.zip` 里 `assets/minecraft/blockstates/cherry_leaves.json` 等应为 **28 个变体**
     （原版变体指回 `minecraft:block/cherry_leaves`，我们占用的指向 `yuanguo:block/*`）；
   - Bridge 的 `claimed by more than one` 数量应下降。
4. 若底稿方案不被尊重（生成结果仍只有十几个变体），备选：
   - 让 CE 生成后再补写：把完整底稿放到 CE 会读到的位置（资源包合并目录），或
   - 改用显式 `state:` 指定状态，绕开自动分配，并配合完整底稿。
5. 沙漠出现杨梅树叶：怀疑仍是 2.2 的串台（状态被别的方块占用）或 bayberry 树叶状态与沙漠方块重叠，
   待第 3 步结果出来后再复现确认；不要在没验证前乱改 `features.yml` 的生物群系列表。

### 6. 约束
- 只改 `D:\plugins-by\YuanGuoDelight`；不动 `Yuan-CE-Bridge`、CraftEngine 本体或其它内容包。
- 用户自行跑测试；不要重复劳动，速度优先。
## 结论（2026-10-25 已验证）：底稿方案生效，问题已修

### 1. 之前"底稿不生效"的判断是错的
- 原因：CE 的资源包 zip **只在 workflow 运行时重新生成**（`config.yml` 里 `trigger: reload_pack`），
  普通重启 / `/ce reload` 都不会重写 `cache/hosted/default/resource_pack.zip`。
- 当时看到的 zip 还是 10-23 的旧产物，所以看到的是旧结果。
- 正确验证方式：`/ce workflow default`（或控制台执行），会依次 generate → validate → zip → upload。

### 2. 实测结果（执行 workflow 后）
`assets/minecraft/blockstates/*.json` 全部变成 **28 变体**，未占用的变体指回原版模型：

| blockstate | 变体数 | 指回原版的数量 |
|---|---|---|
| cherry_leaves | 28 | 15 |
| pale_oak_leaves | 28 | 15 |
| flowering_azalea_leaves | 28 | 8 |
| azalea_leaves | 28 | 2 |
| spruce_leaves | 28 | 15 |
| oak / acacia / birch / dark_oak / jungle / mangrove | 28 | 28（全原版） |

- 说明 CE 的 `generateBlockOverrides` 确实会**读取已存在的 blockstate 作为底稿再合并**，
  前提是我们自己把完整底稿放进 `pack/resourcepack/assets/minecraft/blockstates/`。
- 这就是"半棵树透明 / 显示成别的东西"的根因修复：
  以前整份文件只剩我们占用的十几个变体，原版树叶落到没有模型的状态就不可见；
  同时我们自己的模型会"泄漏"到原版状态上（沙漠里看到杨梅叶就是这一类）。

### 3. 已交付
- `YuanGuoDelight-1.0.0.jar`（1,566,750 字节，2026-10-25 09:18 构建）
  内含 `pack.zip` → `resourcepack/assets/minecraft/blockstates/` 11 份完整 28 变体底稿。
- 底稿清单：acacia / azalea / birch / cherry / dark_oak / flowering_azalea / jungle / mangrove / oak / pale_oak / spruce leaves。
- 配置侧未改：51 处仍是 `auto_state: non_tintable_leaves`（38 + 13），状态池容量足够，启动无"最大容量"报错。

### 4. 测试步骤（用户自行跑）
1. 复制 jar 到 `D:\服务器测试\yfh.zip\yfh\plugins\`。
2. 起服，进游戏执行 `/ce workflow default`（重要：否则客户端拿到的还是旧资源包）。
3. 开新区块去沼泽 / 森林验证果树树叶是否完整、无透明缺口。
4. 若客户端仍显示旧贴图，执行 `/ce feature send-pack` 或让玩家重新接受资源包。

### 5. 后续可选（本次未做）
- 我们的树叶有少量外观落在 `distance=7,persistent=false`（如 kiwi fruits、lemon upper_age=4）。
  若实测出现树叶自行消失，再考虑把它们固定到 `persistent=true` 的状态。
  目前 `seasonal_fruit_leaves` 行为自己实现了 `RandomTickBlock`，原版随机刻大概率不会走到，
  先不动，避免又引入状态池冲突。

## 已完成（2026-10-25）：柠檬树自然生成改为成熟两格树
### 1. 需求
- 不再生成 age=0 幼苗，自然生成直接是 mod 原版的成熟柠檬树（age=4，两格）。

### 2. 实现
- 文件：`pack/configuration/features.yml` → `yuanguo:lemon_tree`
- 结构：`minecraft:block_column`
  - `allowed_placement: minecraft:replaceable`
  - `direction: up`、`prioritize_tip: false`
  - layer 1：`yuanguo:lemon_tree [age=4, half=lower]`
  - layer 2：`yuanguo:lemon_tree [age=4, half=upper]`
- 放置判定（放在 heightmap / count / random_offset / biome 之后）：
  - 本位 air、上方一格 air、下方一格 grass_block
- 保留 mod 原参数：rarity 32、count 20、xz ±7 / y ±3、WORLD_SURFACE_WG、plains / sunflower_plains / meadow。

### 3. 为什么两格一起写
- mod 的 configured feature 是 `simple_block`，只写 `half=lower`；原版 `DoublePlantBlock` 靠 `setPlacedBy`
  补上半格，而世界生成不会触发放置回调，所以之前只剩下半格。
- 成熟树 `SeasonalCropBlockBehavior.updateUpperHalf` 只补/同步上半格，不会删除已存在的上半格，
  因此一次写入两格是安全的。
- 写法参照农夫乐事野生水稻：`block_column` + 两层 provider（该内容包在游戏内已验证可生成两格）。

### 4. 生成器同步
- `work/gen/Features.java`：`lemonSapling(...)` → `lemonTreeColumn(...)`，输出 `block_column`；
  `lemonPredicate()` 增加“上方一格也是 air”的判定。
- 未运行全量生成器（`Gen.main` 会清空并重写整个 `pack/`），只手工同步了 `features.yml` 的柠檬树段。
- 备份：`work/gen/Features.java.bak-lemon`、`work/features.yml.bak-lemon`（均为改前版本）。

### 5. 构建与部署
- `build.ps1` 构建：`YuanGuoDelight-1.0.0.jar`（1,566,850 字节，2026-10-25 11:55）。
- 已校验 jar 内 `pack.zip → configuration/features.yml` 的柠檬段为 `block_column` + 两格 `age: '4'`。
- 已部署到 `D:\服务器测试\yfh.zip\yfh\plugins\YuanGuoDelight-1.0.0.jar`，与构建产物 SHA-256 一致。
- 测试服启动无解析错误：`已加载 placed features …（42）`，随后已停服。

### 6. 验证注意
- 世界生成只影响新生成区块；旧区块不会回填，需要新开区块验证。
- 若实测上半格仍缺失，备选方案：
  - 改回 `minecraft:simple_block` 写 `age=4, half=lower`，由随机刻在下次 tick 补上半格（会短暂缺上半格）；
  - 或把 provider 换成 `minecraft:simple_state_provider`（农夫乐事野生水稻同款写法）。

## 已完成（2026-10-25）：果酱装入农夫乐事液罐
### 1. 问题
- 果酱瓶（`yuanguo:<fruit>_jelly`）右键 `farmersdelight:jug` / `glass_jug` 无反应，倒不进去。
- 液罐自身配置正常（`settings.libuid:fluid_container.capacity: 16000`），问题在果酱瓶这一侧。

### 2. 根因
- `JugManager.interact` 的匹配顺序：
  1. `emptyHeld` / `fillHeld`：按 `papersdelight_recipes` 的 `fluid_emptying` / `fluid_filling` 配方匹配手持物品；
  2. `emptyHeldGeneric` / `fillHeldGeneric`：走 Libuid `FluidUtil` 通用容器路径；
  3. 都不成功才打开液罐 GUI。
- 果酱瓶既没有 `fluid_emptying` 配方，也没有 `libuid:fluid_container` 设置，
  因此两条路径都匹配不到（空果酱瓶也没有 PDC 流体数据，通用路径更不可能成功）。
- 农夫乐事自己的牛奶瓶 / 蜂蜜瓶走的就是第 1 条：`fluid_emptying/milk_bottle` 等。

### 3. 实现（只注册果酱一种流体）
- `src/main/java/dev/yuan/yuanguo/cauldron/LibuidBridge.java`
  - `registerFluids` 现在只注册 22 个 `<fruit>_jam`（不再注册 `_juice` / `_jello` / `lemonade`）。
  - `FLUID_BY_LIQUID` 只保留 `<fruit>_jelly` → `<fruit>_jam`。
  - 果酱流体纹理用 `water`（原版水贴图 + 颜色染色），与农夫乐事液罐的 `jug_fluid_water` 模型一致；
    之前用的 `jelly` 贴图在液罐里没有对应模型，会显示不对。
- `pack/configuration/papersdelight.yml`
  - 新增 44 条配方（22 水果 × 倒出 / 灌装）：
    - `yuanguo:jug/jam_emptying/<fruit>`：`filled_input: yuanguo:<fruit>_jelly` → `empty_result: minecraft:glass_bottle`，250 mB
    - `yuanguo:jug/jam_filling/<fruit>`：`empty_input: minecraft:glass_bottle` → `filled_result: yuanguo:<fruit>_jelly`，250 mB
  - 与牛奶瓶完全同一条代码路径，不依赖物品 PDC。
- `work/gen/Recipes.java`
  - 新增 `JUG` 表与 `jamJugRecipes()`，生成器重跑时也会写出这 44 条配方（防回归）。

### 4. 构建与部署
- `build.ps1`：`YuanGuoDelight-1.0.0.jar`（1,567,236 字节）。
- 已校验 jar 内 `pack.zip → configuration/papersdelight.yml` 含 44 条果酱配方。
- 已部署到测试服：
  - `plugins/YuanGuoDelight-1.0.0.jar`
  - `plugins/CraftEngine/resources/YuanGuoDelight/configuration/papersdelight.yml`
- 测试服未启动，等待游戏内验证。

### 5. 游戏内验证步骤
- 重启服务器（或 `/ce reload` 后再 `/reload confirm`，以让插件重新注册流体）。
- 果酱瓶右键液罐：应倒入 250 mB 果酱，瓶子变空玻璃瓶，液罐液体显示对应水果颜色。
- 空玻璃瓶右键装着果酱的液罐：应装回对应 `<fruit>_jelly`。
- 液罐 GUI 输入槽同样应支持这两个方向。

### 6. 注意
- 只注册果酱后，`LibuidBridge.fluid(...)` 对果汁 / 果冻 / 柠檬水返回 `null`；
  炼药锅交互本身不查 Libuid（`CauldronInteractions` 只按物品 ID 处理），因此不影响现有炼药锅玩法。
- 液罐液体贴图走农夫乐事的 `jug_fluid_water` + 染色；若想用 mod 的 `jelly` 贴图，
  需要另建 `farmersdelight:jug_fluid_jelly` 物品/模型（属于农夫乐事内容包，本项目未改）。
## 已完成（2026-10-25）：液罐泡面包 → 果酱面包
### 1. 设计（用户自定义，mod 没有）
- mod 只有工作台合成：1 个面包 + 1 整瓶果酱 → 1 个果酱面包。
- 现在液罐可以浸泡：面包 + 125 mB 果酱 → 对应水果的果酱面包。
- 125 mB 取自 mod 的 Create 灌注配方（`filling/*_jelly_bread.json`，`amount: 125`），
  所以一瓶 250 mB 果酱能做两个面包，比合成省一半。

### 2. 实现
- `pack/configuration/papersdelight.yml`：新增 22 条 `soaking` 配方
  ```yaml
  yuanguo:jug/jam_bread/<fruit>:
    type: soaking
    ingredient: minecraft:bread
    fluid: yuanguo:<fruit>_jam
    amount: 125
    result: yuanguo:<fruit>_jelly_bread
    time: 20
    consume_fluid: true
  ```
- 果酱面包是 22 个独立物品（`yuanguo:<fruit>_jelly_bread`），所以按水果各写一条，
  产出会带上对应水果的效果（`effects.yml` 已有 `*_jelly_bread` 条目）。
- `work/gen/Recipes.java`：新增 `SOAKING` 表与 `jamBreadSoaking()`，生成器重跑不会丢。

### 3. 构建与部署
- `build.ps1`：`YuanGuoDelight-1.0.0.jar`（1,567,624 字节）。
- 已校验 jar 内 `pack.zip → configuration/papersdelight.yml`：
  - 44 条 `jam_emptying` / `jam_filling`
  - 22 条 `jam_bread` soaking
- 已部署到测试服 jar 与 `resources/YuanGuoDelight/configuration/papersdelight.yml`。

### 4. 游戏内验证
- 液罐 GUI 输入槽放面包，罐内有对应果酱时，进度条走完输出该水果的果酱面包，消耗 125 mB。
- 浸泡时间已按用户要求从 20 tick 调整为 40 tick（2 秒）。
- 液罐里果酱不足 125 mB 时不应产出。
- 不同水果果酱应产出各自水果的面包（不是固定苹果味）。

## 已完成（2026-10-25）：水果可直接加入炼药锅

### 1. 问题
- 满水的原版炼药锅只接受柠檬片和果酱，手持水果右键无反应，必须先做果酱才能起锅。
- README 里原本写了「水果 → 水锅」，与实际实现不一致。

### 2. 实现
- 文件：`src/main/java/dev/yuan/yuanguo/cauldron/CauldronInteractions.java`
- `vanillaWaterCauldron(...)` 新增水果分支：用 `CauldronFruits.fruitForItem(id)` 识别水果，
  直接转成 `yuanguo:<fruit>_cauldron`，初始等级为 `CauldronFruits.advance(index)`（即 `4 / jellyCost`）。
- 该分支 `heat = false`，与果酱起锅一致，不要求热源。
- 已有水果锅 / 淡柠檬水锅里的水果逻辑不变，仍然需要热源。

### 3. 文档
- `README.md` 炼药锅表格按实际行为重写：
  - 柠檬片 → 满水锅：淡柠檬水锅
  - 果酱瓶 → 满水锅：对应水果锅（等级 4）
  - 水果 → 同种水果锅 / 淡柠檬水锅：需热源
  - 果酱瓶 → 同种水果锅：浓度 +4
  - 糖 / 粘液球：需热源

### 4. 构建与部署
- `build.ps1`：`YuanGuoDelight-1.0.0.jar`（1,567,647 字节）。
- 已部署到测试服 `plugins/YuanGuoDelight-1.0.0.jar`。

### 5. 游戏内验证
- 满水原版炼药锅 + 任意水果：应立即变成对应水果锅，等级按该水果的 `4 / jellyCost`。
- 苹果（jellyCost 2）→ 等级 2；蓝莓（jellyCost 4）→ 等级 1。
- 水果加入已有同种水果锅仍需热源，与之前一致。
## 已完成（2026-10-25）：流体库 Libuid → NyanaFluid 迁移

### 1. 背景
- 测试服的农夫乐事升级到 PapersDelight 1.2.4，流体库从 Libuid 换成了 NyanaFluid。
- 新版 `[内容包] 农夫乐事 1.2.4` 的 `configuration/fluids.yml` 只声明流体（牛奶），
  没有 `jug` 灌装 / 倒出配方；液罐改为 `net.nyana.fluid.api.block.FluidTankController`，
  容器识别靠 `bottle:` / `bucket:` 声明的 `BucketSpec`。
- 测试服 `plugins/` 里已经没有 Libuid，旧实现会让果酱瓶完全无法与液罐交互。

### 2. 实现
- 新增 `pack/configuration/fluids.yml`：22 种 `yuanguo:<fruit>_jam`。
  - `display_name`、`color`（水果色）、`texture: minecraft:block/water_still`
  - `display_item` 与 `bottle` 都指向 `yuanguo:<fruit>_jelly`
  - `sounds.fill/empty` 用原版瓶装音效
  - `bottle:` 会自动注册 `minecraft:glass_bottle ↔ 果酱瓶`、250 mB 的 `BucketSpec`，
    因此液罐的手持交互和 GUI 输入槽都能直接识别，不需要 `fluid_filling` / `fluid_emptying` 配方。
- `work/gen/Recipes.java`：新增 `jamFluids()`，生成器重跑时仍会产出同一份 `fluids.yml`；
  删除 `JUG` 字段、`jamJugRecipes()` 及其调用（旧的 44 条灌装 / 倒出配方）。
- `pack/configuration/papersdelight.yml`：只保留 22 条 `jam_bread` soaking（40 tick、125 mB）。
- `src/main/java/dev/yuan/yuanguo/cauldron/FluidSupport.java`：用插件名 + `Class.forName`
  探测 NyanaFluid，不引入编译期依赖；删除 `LibuidBridge.java`。
- `YuanGuoDelight.java`、`CauldronInteractions.java` 的注释与日志改为 NyanaFluid。
- `src/main/resources/paper-plugin.yml`：可选依赖 `Libuid` 改为 `NyanaFluid`（BEFORE、非必需）。
- `build.ps1`：移除 `libuid-1.0.1.jar` 编译依赖。
- `README.md`：依赖表、液罐说明、内容规模、已知限制、授权改为 NyanaFluid（MIT）；
  PapersDelight 版本标注升到 1.2.4。

### 3. 构建与部署
- `build.ps1`：44 个类，`YuanGuoDelight-1.0.0.jar`（1,565,618 字节）。
- 已部署 jar 到 `D:\服务器测试\yfh.zip\yfh\plugins\`。
- 已同步 `resources/YuanGuoDelight/configuration/{fluids.yml,papersdelight.yml}`，
  因为测试服上旧的 `papersdelight.yml` 还带 44 条已废弃配方。

### 4. 校验
- jar 内 `pack.zip → configuration/fluids.yml`：22 个流体、22 个 `bottle`。
- `papersdelight.yml`：22 条 soaking，0 条 `fluid_filling` / `fluid_emptying`。
- jar 内无任何 Libuid 条目；`lang/zh_cn.yml` 的 22 个 `fluid.yuanguo.<fruit>_jam` 键齐全。

### 5. 游戏内验证
- 果酱瓶右键液罐：倒入 250 mB，瓶子变空玻璃瓶，液体显示对应水果颜色。
- 空玻璃瓶右键果酱液罐：装回对应 `<fruit>_jelly`。
- 液罐 GUI 输入槽放面包：40 tick 后产出对应果酱面包，消耗 125 mB；不足 125 mB 不产出。
- 控制台启动日志应出现 `NyanaFluid found`；缺库时为 `NyanaFluid was not found`，炼药锅玩法不受影响。

## 进行中（2026-10-25）：果冻块弹跳延迟 / 站上自弹

### 1. 现象
- 果冻块（`*_jello_block`）落地能弹，但**明显延迟**（大约 1 tick 的“先落地再弹”）。
- 玩家**站上去/踩上去**也会被弹起，不是原版粘液块的手感。
- 果酱块（`*_jelly_block`）行为正常，本次不动。

### 2. 术语对应（避免再次搞混）
- `yuanguo:<fruit>_jelly_block` = **果酱块**，行为 `yuanguo:jelly_surface`（蜂蜜块手感 + 减速），**不弹**。
- `yuanguo:<fruit>_jello_block` = **果冻块**，行为 `bouncing_block`（CE 内置），**会弹** ← 用户报的是这个。
- 两类各 22 个，配置都在 `pack/configuration/blocks/misc.yml`。

### 3. 已查明的根因（本轮已反编译核对）
- 测试服：Leaf 26.1.2（`version.json` id = 26.1.2），CE `craft-engine-paper-plugin-26.9.1.jar`。
- CE 26.9.1 的 `BouncingBlockBehavior` 只重写两个钩子：
  - `fallOn`：仅在 `fall_damage_multiplier > 0` 时结算摔落伤害（当前为 0，直接 return）。
  - `updateEntityMovementAfterFallOn`：`delta.y < 0` 时把 y 取反乘 `bounce_height`，生物 ×1.0、其他 ×0.8。
  - **没有 `stepOn` / `entityInside`**，所以“站上自弹”不可能来自该行为本身。
- 延迟来源：`bounceUp` 之后调用 `syncBounceUp`，用 `runLater(..., 1L, ...)` **延迟 1 tick** 才 `setHurtMarked(true)`；
  原版 `SlimeBlock.bounceUp` 不设置 hurtMarked。CE 需要它来给客户端补发速度包（自定义方块客户端没有本地物理），
  但延迟 1 tick 就表现为“先落地、再弹”。
- `settings.overrides.bounce_restitution: 1.0` 在 26.1.2 上**完全不生效**：
  `BukkitBlockManager` 只在 `VersionHelper.isOrAbove26_2` 为真时调用 `BlockBehaviourProxy.setBounceRestitution`，
  该常量判定的是 CE/游戏版本号 `version >= 260200`，26.1.2 → `260102` → false。
  因此这个字段是死配置（保留也无害，但容易误导，建议删除）。

### 4. 原版粘液块对照（26.1.2 反编译）
- `SlimeBlock.stepOn`：站立且 `|delta.y| < 0.1` 且非潜行时，水平速度 ×(0.4 + 0.2×|delta.y|)，y 不变。
  → 这是“站立减速”，**不会把人弹起来**。
- `SlimeBlock.fallOn`：`isSuppressingBounce()` 为假时 `causeFallDamage(distance, 0.0F, fall)`（即不掉血）。
- `SlimeBlock.bounceUp`：`delta.y < 0` 时 y 取反（生物 ×1.0 / 其他 ×0.8），**不调用 hurtMarked**。
- 结论：要让果冻块“完全按粘液块逻辑”，需要自定义行为，而不是继续依赖 CE 的 `bouncing_block`。

### 5. 明天的修复方案（未实施）
1. `src/main/java/dev/yuan/yuanguo/behavior/JelloBlockBehavior.java`
   - 删除现有 `stepOn` 里的水平减速（那是上一版“果酱块手感”的残留，会导致站上去被减速/异常）。
   - 改为原版粘液块语义：
     - `stepOn`：`|delta.y| < 0.1` 且非潜行 → 水平 ×(0.4 + 0.2×|delta.y|)，y 不变。
     - `fallOn`：`fall_damage_multiplier` 默认 0，不掉血（可读配置）。
     - `updateEntityMovementAfterFallOn`：`delta.y < 0` → `newY = -delta.y × bounce_height × (living ? 1.0 : 0.8)`。
   - 关键：**不要** `runLater(1 tick)`，在反弹时**同 tick** `setHurtMarked(true)`；
     若同 tick 仍不生效，再尝试 `getBukkitEntity().setVelocity(...)`（CraftEntity 内部会 setDeltaMovement + hurtMarked）。
   - 潜行抑制反弹：`EntityProxy.getSharedFlag(entity, 1)`（原版 `isSuppressingBounce`）。
2. `work/gen/Blocks.java` 的 `jellyBlock()`：
   - `type: bouncing_block` → `type: yuanguo:jello_bounce`
   - 去掉 `overrides.bounce_restitution`
   - 行为参数改为 `bounce_height: 1.0`、`fall_damage_multiplier: 0.0`
3. `pack/configuration/blocks/misc.yml`：22 处 `_jello_block` 同步改（批量替换 `type: bouncing_block` → `type: yuanguo:jello_bounce`，并删除 `bounce_restitution: 1.0`）。
   - 注意 `sync_player_position` 参数在自定义行为里不再使用，可删。
4. 用 SnakeYAML 校验 `misc.yml`（`libraries/org/yaml/snakeyaml/2.6/snakeyaml-2.6.jar`）。
5. `.\gradlew.bat clean jar --no-daemon --console=plain`，确认 jar 内 `pack.zip` 的 `misc.yml` 正确。
6. 部署到测试服，验证：
   - 从高处落下：落地同 tick 弹起，无“先停顿”感；不摔伤。
   - 站在块上不动：不弹、不被减速。
   - 潜行落下：不弹。
   - 生物 ×1.0、掉落物/箭等 ×0.8。
7. 通过后重新构建并更新 GitHub Release `v1.0.0` 的附件（旧附件 SHA256 `DD458C44...B840`）。

### 6. 本轮未改动
- 工作区干净（`git status` 无输出），没有半成品代码。
- 只做了反编译排查，未改 `pack/` 与 `src/`。

## 已完成（2026-11-20）：CraftEngine 依赖升级 26.8.2 → 26.9.1

### 1. 选版依据
- Maven（repo.momirealms.net）最新 API 为 `26.10`，但 GitHub 最新 tag 是 `26.9`，Modrinth 公开运行时最新是 `26.9.2`；
  `26.9.2` 没有发布 API（`craft-engine-core-26.9.2.jar` 为 404），`26.10` 也没有公开运行时。
- 测试服实际运行 `26.9.1`，因此编译依赖对齐到 **26.9.1**（与运行时一致，避免编译到运行时不存在的新 API）。
- `26.9` 起 CE 引入资源包 workflow，测试时仍需 `/ce workflow default` 让客户端拿到新包。

### 2. 改动
- `build.gradle.kts`：`craft-engine-core` / `-bukkit` / `-bukkit-proxy` 三处 `26.8.2` → `26.9.1`。
- `build.ps1`：`$DEPENDENCIES` 三个文件名与下载 URL 同步改为 `26.9.1`。
- `libs/`：新增 `craft-engine-*-26.9.1.jar`（本地缓存，`libs/` 已被 .gitignore）。
- `README.md`：徽章与依赖表 `26.8.2+` → `26.9.1+`。

### 3. 兼容性核对
- `javac --release 21 -Xlint:all` 与 `.\gradlew.bat compileJava` 均通过（仅原有 10 条过时 API 警告，与升级前一致）。
- 源码用到的 `CraftEngineBlocks` / `BlockBehavior` / `ItemBehaviors` / proxy 类签名在 26.9.1 下不需要改代码。
- `PapersDelightSupport` 反射的 `AbstractPackManager#sectionParsers` 字段在 26.9.1 中仍然存在，PD 章节探测不受影响。

### 4. 构建与产物
- `build.ps1` 重新构建：`YuanGuoDelight-1.0.0.jar`（1,565,525 字节）。
- 产物内 `pack.zip` 与磁盘 `pack/` 完全一致（2377 个文件，无缺失/无多余/大小一致），
  顺带把此前未进 jar 的 blueberry loot 修复（`item: yuanguo:blueberry_bush`）带进了产物。
- `libs/` 中旧的 `craft-engine-*-26.8.2.jar` 已不再被任何构建脚本引用，可随时删除。

## 已完成（2026-11-20）：问题清单修复

### 1. 果冻块
- `JelloBlockBehavior` 重写为原 mod 语义：不再实现 `stepOn`（原 `JelloBlock` 把它覆写成空）；
  `updateEntityMovementAfterFallOn` 反转 y，生物 ×1.0、其他 ×0.8；**同 tick** 调
  `EntityProxy.setHurtMarked(entity, true)` 让客户端立刻拿到速度包；`fallOn` 默认不掉血（可配）。
- 22 个 `*_jello_block` 从 CE `bouncing_block` 改为 `yuanguo:jello_bounce`，
  删除 `sync_player_position` 与 `settings.overrides.bounce_restitution`（26.1.2 上无效）。
- `work/gen/Blocks.java` 同步，重跑生成器不会回退。

### 2. Folia
- `paper-plugin.yml` 改为 `folia-supported: false`；README 徽章、简介、依赖表改为仅 Paper，
  已知限制里写明原因（效果循环与光环会跨 region 操作实体）。

### 3. 炼药锅 / 发射器
- `CauldronListener.onDispense`：先克隆 1 个输入，配方成立后从容器 `removeItem` 再取消事件，
  修复“发射器里的瓶子/糖永远不消耗、可无限灌果酱”。
- `CauldronBlockBehavior`：按 `context.getHand()` 取主手或副手物品，并把剩余物品写回同一只手；
  `YuanGuoDelight.instance()` 判空，插件被运行时禁用时不再 NPE。

### 4. 内容部署 / 配置
- `PackInstaller` 新增 `ce-pack.files` 清单：更新时删除“上次部署过、新包已不含”的文件，
  同时清理空目录；管理员手工新增的文件不会被动。
- `PluginConfig` 不再把合并默认值后的配置 `save()` 回磁盘（原先每次加载/重载都会丢掉
  config.yml 的全部注释）；合并失败改为 WARNING 日志。

### 5. 效果系统
- `EffectState` 持久化新增 total（`remaining:amplifier:total`），重登后 Boss 条从正确进度开始，
  旧的两段格式仍可读。
- 每 200 次 tick 清理过期 `disgustedUntil`，不再长期累积。
- 可疑气味扫描从每 10 tick 一次改为每 40 tick（2 秒）一次，并清理冷却表。
- 属性修饰符改用 `NamespacedKey` 重载；`PotionEffectType` 改用 `Registry.MOB_EFFECT`；
  `isFolia()` 结果静态缓存（不再每次 `Class.forName`）。
- “每个效果一条 Boss 条”未改，写入 README 已知限制。

### 6. 其它代码
- `SeasonalCropBlockBehavior` 尊重 `handleBlockGrowEvent` 返回值：事件被别的插件取消时
  不再补上半格。
- 盆栽树苗、碗装盛取在数量为 1 时改为清空对应手，避免留下 amount=0 的空物品。
- `onDisable` 清空静态 `instance`，运行时重载不会留下指向已停止插件的引用。
- `BehaviorConfig.season` 对无法识别的季节值告警一次（原先静默当成全年生长）。
- 删除未使用代码：`EffectRegistry.style/all`、`EffectService.runNow/describeItem`、
  `BehaviorRegistration.forEach`、`EffectState.plugin`、`EffectTimerBar.remove`、
  锅行为的 `liquid/kind/kindProperty` getter；修正 `YuanGuoDelight` 里重复/错误的注释。
- 启用日志与 `/yuanguo status` 的“季节联动”改为读取服务实际状态，不再与“联动生效”自相矛盾。

### 7. 内容包
- `categories.yml` 补齐 `yuanguo:potted`、`yuanguo:cauldrons` 两个分类与根列表项，
  `crops` 补 `wild_pineapple`、`blueberry_bush`。现在除 3 个技术性物品
  （`attached_hamimelon_stem`、`hanging_durian`、`dummy_cauldron`）外全部进创造菜单。
- `attached_hamimelon_stem` 的物品名改用 `block.yuanguo.attached_hamimelon_stem`
  （100 个语言都有这个 key），并删掉生成器里只写 en/zh 的 item key。

### 8. 校验
- `javac --release 21 -Xlint:all`：0 错误、0 警告（原来 10 条过时 API 警告已清）。
- pack 校验：157 方块、710 物品、13 分类引用全部成立；22 个 jello 块全部使用
  `yuanguo:jello_bounce`；`bounce_restitution` 0 处；未分类的非技术物品 0 个。
- `build.ps1` 重建 jar，`pack.zip` 与磁盘 `pack/` 完全一致。

### 9. 未做 / 待进游戏验证
- 与原 mod 的效果差异（山竹糕无效果、贝里尼少反胃、8 道菜缺 Nourishment）按要求只记录在
  README 已知限制，未改 items.yml。
- Folia 未实测（已声明不支持）。
- 果冻块手感、发射器炼药锅、副手锅交互需要进游戏验证。

## 已完成（2026-11-20）：果冻块弹跳开关

### 1. 背景
- 果冻块弹跳在测试服和生产环境表现不一致（服务端核心/版本差异），需要一个不重编译的开关。

### 2. 实现
- `config.yml`：新增 `pack.jello-bounce`（默认 `true`），带注释说明关掉后方块/模型/配方不受影响。
- `PluginSettings`：新增 `jelloBounce` 字段，读取 `pack.jello-bounce`，默认 `true`。
- `JelloBlockBehavior.updateEntityMovementAfterFallOn`：每次落地先读开关，关闭时直接返回
  （不弹、不 `setHurtMarked`）；`fallOn` 的摔伤仍由内容包 `fall_damage_multiplier` 决定（默认 0）。
  开关是实时读取的，`/yuanguo reload` 后立即生效，不需要重启。
- `/yuanguo status` 增加“果冻弹跳”一行，方便确认当前生效值。
- `README.md`：配置表补 `pack.jello-bounce`。

### 3. 校验
- `javac --release 21 -Xlint:all`：0 错误、0 警告。
- `build.ps1` 重建 jar，`pack.zip` 与磁盘 `pack/` 一致。

### 4. 测试建议
- `jello-bounce: false` + `/yuanguo reload`：从高处落下应正常落地、不弹起。
- 再改回 `true` + `/yuanguo reload`：应恢复弹起（不必重启）。
