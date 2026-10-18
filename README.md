# Tingere

知道你不想看介绍，所以我就放一点例子了。  
这个文档挺多 AI 生成的，我实在懒得改什么了。

---

## 配方文件

配方存放在插件数据目录下的 `recipes` 文件夹中：

```
plugins/tingere/
└── recipes/
    ├── anything.yml
    └── subfolder/          # 支持嵌套，会递归扫描
        └── nested.yml
```

- 支持 `.yml` 与 `.yaml` 后缀，文件名任意，不影响结果。
- 一个文件可以只写一个配方，也可以按顶级键分组写多个配方，便于归类：

```yaml
sword_upgrade:
  type: transmute
  id: sword_upgrade
  input:
    material: DIAMOND_SWORD
  material:
    material: NETHERITE_INGOT
  result:
    material: NETHERITE_SWORD

apple_smelting:
  type: shapeless
  id: apple_to_gold
  ingredients:
    - material: APPLE
    - material: GOLD_INGOT
  result:
    material: GOLDEN_APPLE
```

单个文件或单个配方解析失败时只会记录警告并跳过，不影响其余文件。  
未知字段会报错，字段名拼写错误会 log 进后台。

---

## 通用字段

| 字段     | 必填 | 说明                                       |
| -------- | ---- | ------------------------------------------ |
| `type`   | 是   | 配方类型，见下方各小节                     |
| `id`     | 是   | 配方唯一标识，同时作为 `NamespacedKey` 的 path |
| `result` | 是   | 结果物品，格式见「原料与物品」一节         |

`id` 只能包含小写字母、数字、下划线、连字符与点，且在同一插件内不可重复。  
早期版本使用的 `key` 仍被接受，会在解析时视为 `id`，但是不允许两者同时出现。

---

## 原料与物品

`ingredients` 列表项、`input`、`material`、`ingredient`与`result`使用同一种格式：

| 字段         | 必填 | 默认值  | 说明                                    |
| ------------ | ---- | ------- | --------------------------------------- |
| `material`   | 是   | —       | 物品材质                                |
| `amount`     | 否   | `1`     | 数量；默认上限 64，写了 `max_stack_size` 组件时按组件上限（最高 99） |
| `match-mode` | 否   | contain | 匹配模式，见「匹配模式」一节            |
| `components` | 否   | —       | 物品组件，见「物品组件」一节            |

```yaml
material: DIAMOND_SWORD
amount: 1
components:
  display-name: "<gold>烈焰剑"
```

`material` 使用 `Material.matchMaterial` 解析。蛇形命名和 Bukkit item 枚举名均可。  
例如钻石剑可以表示为 `minecraft:diamond_sword` 或者 `DIAMOND_SWORD`。

### 匹配模式

每条原料可以单独设置 `match-mode`：

- `contain`（默认）：材质对上，并且物品带着 `components` 里写的那些组件；物品上多出来的组件不影响匹配。
- `exact`：材质与组件都必须和配置完全一致。
- `material`：只认材质，忽略物品上的组件（附魔、名称等一律不管）。

```yaml
ingredients:
  A:
    material: DIAMOND_SWORD
    match-mode: material   # 任何钻石剑均可，无论附魔、名称
  B:
    material: DIAMOND_SWORD
    components:
      enchantments:
        - minecraft:sharpness:3
    # 默认 contain：带锋利 III 的钻石剑即可，额外附魔不扣分
```

有序与无序配方中，每个原料可以**独立**设置匹配模式。

---

## 配方类型

### 有序配方

```yaml
type: shaped
id: example_sword
pattern:
  - " A "
  - " B "
  - " C "
ingredients:
  A:
    material: BLAZE_ROD
  B:
    material: DIAMOND
  C:
    material: STICK
result:
  material: DIAMOND_SWORD
  components:
    display-name: "<gold>烈焰剑"
```

`pattern` 每行一个字符串，最多 3 行、每行最多 3 个字符，各行长度必须一致。  
空格表示空位。pattern 中出现的每个非空格字符都必须在 `ingredients` 中有对应映射。

### 无序配方

```yaml
type: shapeless
id: example_apple
ingredients:
  - material: APPLE
  - material: GOLD_INGOT
result:
  material: GOLDEN_APPLE
  amount: 1
```

`ingredients` 为列表，顺序不影响匹配。

### 转化配方

用于需要保留输入物品组件的场景，例如潜影盒染色、工具升级。主物品的全部组件会被复制到结果。

```yaml
type: transmute
id: dye_shulker
input:
  material: WHITE_SHULKER_BOX
material:
  material: RED_DYE
result:
  material: RED_SHULKER_BOX
  amount: 1
  components:
    display-name: "<red>染色的潜影盒"
```

| 字段       | 说明                                   |
| ---------- | -------------------------------------- |
| `input`    | 被转化的主物品，其组件会被保留到输出   |
| `material` | 被消耗的辅助材料                       |
| `result`   | 结果物品，只需 `material`（物品类型）  |

原版转化配方不允许输入与输出为同一物品 ID。  
若要实现同 ID 转化（如钻石剑升级为钻石剑），见「特殊配方」一节。


`result.components` 与 `result.amount` 对转化配方有效，但由插件在合成瞬间应用。
原因是 Bukkit 的 `TransmuteRecipe` 构造器只接受 `Material`，且结果数量固定为 1，
注册阶段无法携带这两项信息。

### 烹饪配方

```yaml
type: furnace
id: rotten_flesh_to_leather
ingredient:
  material: ROTTEN_FLESH
result:
  material: LEATHER
time: 10
experience: 10
```

| 字段         | 必填 | 默认值       | 说明                          |
| ------------ | ---- | ------------ | ----------------------------- |
| `ingredient` | 是   | —            | 输入原料，`amount` **必须为 1**   |
| `result`     | 是   | —            | 结果物品                      |
| `time`       | 否   | 见下表       | 耗时，单位为秒                |
| `experience` | 否   | `0.1`        | 每次熔炼给予的经验值          |

`type` 决定烹饪设备：

| `type`    | 设备   | `time` 默认值 |
| --------- | ------ | ------------- |
| `furnace` | 熔炉   | 10 秒         |
| `blast`   | 高炉   | 5 秒          |
| `smoker`  | 烟熏炉 | 5 秒          |

与转化配方不同，烹饪配方的结果组件与数量在注册阶段即生效。

烹饪、切石与锻造没有合成事件可以补判组件，因此这些配方里若原料写了 `components`，
会按 `exact`（与原版 `ExactChoice` 一致）处理，而不是合成台那种 `contain` 的超集匹配。
`match-mode: material` 仍然表示忽略组件。

---

## 物品组件

`components` 小节可写在结果、原料或 `special` 中。

| 组件                 | 格式                | 说明                                                       |
| -------------------- | ------------------- | ---------------------------------------------------------- |
| `display-name`       | MiniMessage 字符串  | 物品自定义名称，优先级最高                                 |
| `prefix`             | MiniMessage 字符串  | 在原有名称前添加前缀，与 `display-name` 共存时忽略         |
| `suffix`             | MiniMessage 字符串  | 在原有名称后添加后缀                                       |
| `lore`               | MiniMessage 字符串列表 | 物品描述文字                                            |
| `item-model`         | `命名空间:路径`     | 物品模型；不含 `:` 时使用插件命名空间 `tingere`            |
| `custom-model-data`  | 整数                | 写入为 `floats[0]`；也接受原版 `{floats: [...], flags: [...]}` 形态 |
| `enchantments`       | 字符串列表          | 附魔，格式 `附魔ID:等级`，支持 `minecraft:sharpness:3`；也接受原版映射 |
| `stored-enchantments`| 字符串列表          | 同上，写入 `stored_enchantments` 组件                      |
| `attributes`         | 映射列表            | 属性修饰符，见下方说明                                     |
| `maxdamage`          | 整数                | 自定义最大耐久值；亦接受 `max-damage`                      |
| `unbreakable`        | 布尔                | 是否无法破坏                                               |
| `glint`              | 布尔                | 附魔光效开关，设为 `false` 可让附魔物品不发光              |
| `equippable-on-head` | 布尔                | 允许该物品戴在头上，默认关闭；也可写原版 `equippable` 对象 |

上面这种简写和原版组件 JSON 可以混用：已经写成原版形态的值（例如
`enchantments: {minecraft:sharpness: 3}`）会原样交给原版 codec 解析。

生物变体这类注册表 key 带 `/` 的组件（`minecraft:wolf/variant`、`minecraft:cat/variant`、
`minecraft:painting/variant` 等）也可以按字段名写法使用下划线别名：`wolf_variant`、
`cat_variant`、`painting_variant`、`tropical_fish_base_color`…… 插件会自动补成原版 key；
`sheep_color`、`wolf_collar` 这类同理。注意 `mooshroom_variant`、`parrot_variant`、
`axolotl_variant`、`rabbit_variant`、`horse_variant`、`llama_variant`、`fox_variant` 在
1.21.11 走枚举 codec，值要写 `red` 这样的裸名，而不是 `minecraft:red`。

`enchantments` 如果写在 transmute 或者 special 里，结果物品的附魔将会和原有物品附魔合并。

`attributes`：

```yaml
attributes:
  - type: attack_damage     # 亦接受旧名 generic.attack_damage
    amount: 3.0
    operation: ADD_NUMBER   # 省略则 ADD_NUMBER
    slot: mainhand          # 省略则 any
    # id 可省略，插件会自动生成 tingere:modifier_N
```

- `type` 使用 1.21.2 起的属性名，旧名会自动兼容。
- `operation` 可选 `ADD_NUMBER`、`ADD_SCALAR`、`MULTIPLY_SCALAR_1`，也接受 1.21.2 起的新名
  `ADD_VALUE`、`ADD_MULTIPLIED_BASE`、`ADD_MULTIPLIED_TOTAL`。
- `slot` 可选 `mainhand`、`offhand`、`hand`、`feet`、`legs`、`chest`、`head`、`armor`、`body`、`any` 等。
- 每一项都可以单独写 `id`（`命名空间:路径`）；省略时自动生成。
- `type` 无法识别或 `amount` 非数字时会记录警告并跳过该项，不影响配方的其余部分。

---

## 特殊配方

`special` 小节用于绕过「输出物品不能与输入物品 ID 相同」的限制。

```yaml
type: transmute
id: upgrade_same_sword
input:
  material: DIAMOND_SWORD
material:
  material: NETHERITE_INGOT
result:
  material: BARRIER                 # 占位物品，实际不会被使用
  amount: 1                         # 最终数量以此为准
  components:                       # 先应用
    lore:
      - "<gray>来自 result.components"
special:
  target-material: DIAMOND_SWORD    # 最终物品材质，可与输入相同
  copy-input: true                  # 是否复制输入物品的全部组件
  source-character: "A"             # 指定源物品在 pattern 中的字符
  # 或 source-slot: 0               # 指定源物品的工作台槽位（0-8）
  components:                       # 后应用，同名键覆盖 result.components
    prefix: "<gold>[强化] "
    attributes:
      - type: attack_damage
        amount: 12.0
```

| 字段               | 必填 | 默认值 | 说明                                       |
| ------------------ | ---- | ------ | ------------------------------------------ |
| `target-material`  | 是   | —      | 最终物品材质，必须为有效材质               |
| `copy-input`       | 否   | `true` | 是否复制输入物品的全部组件                 |
| `source-character` | 否   | —      | 源物品在 pattern 中的字符，仅限有序配方    |
| `source-slot`      | 否   | —      | 源物品的工作台槽位，取值 0 到 8            |
| `components`       | 否   | —      | 应用于结果的组件                           |

- `special` 节点会取代原本的配方结果逻辑，与配方类型无关。
- `target-material` 缺失或无效时，`special` 段会被忽略并记录警告，该配方退化为普通转化配方。
- 组件应用顺序为 `result.components` 再 `special.components`，后者覆盖同名键，因此两处可以分工，例如基础描述写在 `result`、强化属性写在 `special`。
- `source-character` 与 `source-slot` 用于精确指定从哪个槽位取源物品。`source-character` 仅限有序配方；
  两者都缺省时，transmute 会回退到它的 `input`，其他配方则回退到第一个非空槽位。
- `copy-input: false` 时不复制源物品数据，也就不需要指定源物品。
- GUI 编辑器只覆盖 `shaped` / `shapeless` 的第一版功能，`special.components` 等未在界面里暴露的字段
  在编辑保存时会原样保留，但新增字段仍需手改文件。

---

## 命令与权限

| 命令                                         | 说明                                          |
| -------------------------------------------- | --------------------------------------------- |
| `/tingere`                                   | 列出全部可用命令                              |
| `/tingere help [query]`                      | 显示命令帮助，可按关键字过滤                  |
| `/tingere reload`                            | 重载全部配方文件                              |
| `/tingere get result <id> [amount]`          | 获取配方的结果物品，仅限玩家                  |
| `/tingere get ingredient <id> [index]`       | 获取配方的第 `index` 个原料，序号从 1 开始    |
| `/tingere get recipes <player> <pattern>`    | 为玩家解锁配方，`pattern` 为 `*` 时表示全部   |

别名：`/tg`、`/trecipes`。