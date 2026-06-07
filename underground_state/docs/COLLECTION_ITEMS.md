# Collection Items

当前收藏物品来源：`underground_state/src/main/java/maple/underground_state/gui/CollectionInfoGui.java`

说明：
- `collection_key` 格式为 `<page_id>:<material>`。
- 当前数据库表为 `player_collection_progress`。
- 这个文件用于筛选收藏物品；改完筛选结果后，需要同步回 `CollectionInfoGui.java`。

## 分类汇总

| 分类 | page_id | 经验名 | 数量 |
| --- | --- | --- | ---: |
| 锄头收藏 | `collection_hoe` | `Farming Experience` | 27 |
| 镐子收藏 | `collection_pickaxe` | `Mining Experience` | 21 |
| 剑收藏 | `collection_sword` | `Combat Experience` | 16 |
| 斧头收藏 | `collection_axe` | `Foraging Experience` | 10 |
| 钓鱼收藏 | `collection_fishing_rod` | `Fishing Experience` | 6 |

## 锄头收藏

| # | Material | 显示名 | 说明 |
| ---: | --- | --- | --- |
| 5 | `TORCHFLOWER_SEEDS` | 火把花种子 | 收集稀有的远古花种。 |
| 6 | `PITCHER_POD` | 瓶子草荚果 | 收集可以培育瓶子草的荚果。 |
| 1 | `WHEAT_SEEDS` | 小麦种子 | 收集耕地里最基础的作物种子。 |
| 7 | `WHEAT` | 小麦 | 收集成熟小麦并推进农耕收藏。 |
| 9 | `CARROT` | 胡萝卜 | 收集可食用也可种植的胡萝卜。 |
| 10 | `POTATO` | 土豆 | 收集常见的田地作物。 |
| 12 | `BEETROOT` | 甜菜根 | 收集成熟甜菜根作物。 |
| 14 | `MELON` | 西瓜 | 收集完整西瓜方块。 |
| 15 | `PUMPKIN` | 南瓜 | 收集田地里的完整南瓜。 |
| 17 | `SUGAR_CANE` | 甘蔗 | 收集河岸边生长的甘蔗。 |
| 18 | `BAMBOO` | 竹子 | 收集快速生长的竹类作物。 |
| 19 | `CACTUS` | 仙人掌 | 收集沙漠里生长的多刺作物。 |
| 20 | `COCOA_BEANS` | 可可豆 | 收集丛林木上的可可豆。 |
| 21 | `SWEET_BERRIES` | 甜浆果 | 收集浆果丛结出的果实。 |
| 23 | `BROWN_MUSHROOM` | 棕色蘑菇 | 收集可种植和烹饪的蘑菇。 |
| 24 | `RED_MUSHROOM` | 红色蘑菇 | 收集鲜红的蘑菇资源。 |
| 25 | `NETHER_WART` | 地狱疣 | 收集灵魂沙上生长的炼药作物。 |
| 26 | `KELP` | 海带 | 收集水下生长的农作物。 |
| 29 | `LEATHER` | 皮革 | 收集畜牧动物产出的皮革。 |
| 30 | `RABBIT_HIDE` | 兔子皮 | 收集兔子掉落的皮革材料。 |
| 31 | `RABBIT_FOOT` | 兔子脚 | 收集兔子的稀有畜牧掉落。 |
| 32 | `BEEF` | 生牛肉 | 收集牛产出的肉类资源。 |
| 33 | `PORKCHOP` | 生猪排 | 收集猪产出的肉类资源。 |
| 34 | `MUTTON` | 生羊肉 | 收集羊产出的肉类资源。 |
| 35 | `CHICKEN` | 生鸡肉 | 收集鸡产出的肉类资源。 |
| 36 | `RABBIT` | 生兔肉 | 收集兔子产出的肉类资源。 |
| 37 | `FEATHER` | 羽毛 | 收集鸡掉落的畜牧材料。 |

## 镐子收藏

| # | Material | 显示名 | 说明 |
| ---: | --- | --- | --- |
| 1 | `SAND` | 沙子 | 收集最常见的松散方块。 |
| 3 | `GRAVEL` | 砂砾 | 收集会掉落燧石的松散方块。 |
| 5 | `DIRT` | 泥土 | 收集地表最基础的方块。 |
| 9 | `CLAY` | 黏土 | 收集水边生成的黏土块。 |
| 10 | `STONE` | 石头 | 收集地下最基础的岩石。 |
| 21 | `OBSIDIAN` | 黑曜石 | 收集高硬度火山玻璃。 |
| 22 | `GLOWSTONE` | 荧石 | 收集下界会发光的矿物。 |
| 23 | `COAL` | 煤炭 | 收集基础燃料矿物。 |
| 24 | `RAW_COPPER` | 粗铜 | 收集铜矿石产出的原矿。 |
| 25 | `COPPER_INGOT` | 铜锭 | 收集熔炼后的铜。 |
| 26 | `RAW_IRON` | 粗铁 | 收集铁矿石产出的原矿。 |
| 27 | `IRON_INGOT` | 铁锭 | 收集熔炼后的铁。 |
| 28 | `RAW_GOLD` | 粗金 | 收集金矿石产出的原矿。 |
| 29 | `GOLD_INGOT` | 金锭 | 收集熔炼后的金。 |
| 30 | `REDSTONE` | 红石粉 | 收集红石矿产出的粉末。 |
| 31 | `LAPIS_LAZULI` | 青金石 | 收集附魔常用的矿物。 |
| 32 | `QUARTZ` | 下界石英 | 收集下界石英矿产物。 |
| 33 | `AMETHYST_SHARD` | 紫水晶碎片 | 收集紫晶簇产出的碎片。 |
| 34 | `EMERALD` | 绿宝石 | 收集稀有交易矿物。 |
| 35 | `DIAMOND` | 钻石 | 收集稀有而坚硬的矿物。 |
| 37 | `NETHERITE_SCRAP` | 下界合金碎片 | 收集远古残骸加工后的材料。 |

## 剑收藏

| # | Material | 显示名 | 说明 |
| ---: | --- | --- | --- |
| 1 | `ROTTEN_FLESH` | 腐肉 | 收集僵尸类怪物掉落的战利品。 |
| 2 | `BONE` | 骨头 | 收集骷髅类怪物掉落的骨头。 |
| 3 | `ARROW` | 箭 | 收集远程怪物留下的箭矢。 |
| 4 | `STRING` | 线 | 收集蜘蛛类怪物掉落的丝线。 |
| 5 | `SPIDER_EYE` | 蜘蛛眼 | 收集蜘蛛掉落的炼药材料。 |
| 6 | `GUNPOWDER` | 火药 | 收集苦力怕和女巫相关掉落。 |
| 7 | `SLIME_BALL` | 黏液球 | 收集史莱姆掉落的材料。 |
| 8 | `ENDER_PEARL` | 末影珍珠 | 收集末影人掉落的珍珠。 |
| 9 | `PHANTOM_MEMBRANE` | 幻翼膜 | 收集幻翼掉落的材料。 |
| 10 | `INK_SAC` | 墨囊 | 收集鱿鱼掉落的墨囊。 |
| 14 | `BLAZE_ROD` | 烈焰棒 | 收集烈焰人掉落的核心材料。 |
| 15 | `MAGMA_CREAM` | 岩浆膏 | 收集岩浆怪相关战利品。 |
| 16 | `GHAST_TEAR` | 恶魂之泪 | 收集恶魂掉落的稀有材料。 |
| 18 | `SHULKER_SHELL` | 潜影壳 | 收集潜影贝掉落的外壳。 |
| 20 | `BREEZE_ROD` | 旋风棒 | 收集试炼密室里的稀有掉落。 |
| 21 | `NETHER_STAR` | 下界之星 | 收集强大 Boss 掉落的核心。 |

## 斧头收藏

| # | Material | 显示名 | 说明 |
| ---: | --- | --- | --- |
| 1 | `OAK_LOG` | 橡木原木 | 收集最常见的橡木原木。 |
| 2 | `SPRUCE_LOG` | 云杉原木 | 收集寒冷群系常见的云杉原木。 |
| 3 | `BIRCH_LOG` | 白桦原木 | 收集浅色的白桦原木。 |
| 4 | `JUNGLE_LOG` | 丛林原木 | 收集丛林树木的原木。 |
| 5 | `ACACIA_LOG` | 金合欢原木 | 收集热带草原的原木。 |
| 6 | `DARK_OAK_LOG` | 深色橡木原木 | 收集深色橡木森林的原木。 |
| 7 | `MANGROVE_LOG` | 红树原木 | 收集红树林里的原木。 |
| 8 | `CHERRY_LOG` | 樱花原木 | 收集樱花树的粉色原木。 |
| 25 | `CRIMSON_STEM` | 绯红菌柄 | 收集下界绯红森林的菌柄。 |
| 26 | `WARPED_STEM` | 诡异菌柄 | 收集下界诡异森林的菌柄。 |

## 钓鱼收藏

| # | Material | 显示名 | 说明 |
| ---: | --- | --- | --- |
| 1 | `COD` | 鳕鱼 | 收集最常见的钓鱼收获。 |
| 2 | `SALMON` | 鲑鱼 | 收集河流与海洋里的鲑鱼。 |
| 3 | `TROPICAL_FISH` | 热带鱼 | 收集颜色丰富的热带鱼。 |
| 4 | `PUFFERFISH` | 河豚 | 收集危险但稀有的鱼类。 |
| 5 | `COOKED_COD` | 熟鳕鱼 | 收集加工后的鳕鱼。 |
| 6 | `COOKED_SALMON` | 熟鲑鱼 | 收集加工后的鲑鱼。 |
