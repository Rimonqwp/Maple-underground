# 目录规则：收集中心网页 UI 模拟器（必读）

本目录 `tools/collect-ui-preview/` 是 Minecraft 收集中心的**本地网页模拟器**，仅用于在不启动游戏的情况下预览游戏内 54 格箱子 GUI、奖励结算与 3x3 合成配方。

## 强制同步规则

当用户在浏览本网站（例如 `http://localhost:8765/`）或对页面元素发表评论并提出修改需求时：

1. **禁止只修改网页代码。** 凡涉及玩法逻辑、奖励数值、文案、分类/物品清单、等级门槛、压缩材料或 3x3 合成配方的改动，**必须同步修改真实的 Minecraft 游戏插件代码与配置**，并保持网页与插件一致：
   - 收藏分类、物品、详情页与主菜单入口：`underground_state/src/main/java/maple/underground_state/gui/CollectionInfoGui.java`、`MainInfoGui.java`、`BookshelfInfoGui.java`
   - 收集进度与升级提示：`underground_state/src/main/java/maple/underground_state/collection/CollectionProgressManager.java`、`CollectionProgressListener.java`
   - 收集中心菜单、奖励发放、排行榜与命令：`underground_collect/src/main/java/maple/underground_collect/CollectionMenus.java`、`CollectionRewardService.java`、`CollectionMath.java`、`CollectCommand.java`
   - 压缩材料与职业 3x3 合成配方：`tools/generate_professions.py` -> 重新生成 `underground_item/src/main/resources/professions.yml`
2. 仅当改动纯属网页模拟器自身展示/调试功能（如右侧调试台、CSS 样式、贴图加载方式）且不涉及游戏行为与数据时，才可只修改本目录。如果不确定，默认一并同步插件代码。
3. 修改后记得同步更新本目录的 `data.js` / `app.js`，并按根目录 `AGENTS.md` 规则用中文提交到本地 Git（不 `git push`）。
