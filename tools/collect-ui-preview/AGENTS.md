# Directory Rules - Collection Center Web UI Simulator (MUST READ)

This directory (`tools/collect-ui-preview/`) is a **local web simulator** for the in-game
Minecraft Collection Center. It exists solely to preview the 54-slot chest GUIs, reward claims,
and 3x3 crafting recipes without launching the game.

## Mandatory sync rules

Whenever the user views this website (e.g., `http://localhost:8765/`) or comments on a page
element to request a change:

1. **NEVER edit only the website.** For any change involving gameplay logic, reward values,
   in-game text/Lore, categories/items, tier thresholds, compressed materials, or 3x3 crafting
   recipes, you MUST update the corresponding real Minecraft plugin code and configs in sync:
   - Collection categories, items, detail GUI, and main menu entry:
     `underground_state/src/main/java/maple/underground_state/gui/CollectionInfoGui.java`,
     `MainInfoGui.java`, `BookshelfInfoGui.java`
   - Collection progress tracking and tier-up notifications:
     `underground_state/src/main/java/maple/underground_state/collection/CollectionProgressManager.java`,
     `CollectionProgressListener.java`
   - Collection center menus, reward claiming, leaderboards, and commands:
     `underground_collect/src/main/java/maple/underground_collect/CollectionMenus.java`,
     `CollectionRewardService.java`, `CollectionMath.java`, `CollectCommand.java`
   - Compressed materials and profession 3x3 crafting recipes:
     `tools/generate_professions.py` -> regenerate `underground_item/src/main/resources/professions.yml`
   - Weapons, dungeon armor sets, special helmets, and cave echoes:
     `underground_item/src/main/resources/weapons/*.yml`, `underground_item/src/main/resources/gear/*.yml`
     (and matching managers in `underground_item/src/main/java/maple/underground_caves/{weapons,gear}/`),
     then re-run `ruby tools/generate_equipment_data.rb` -> regenerates `equipment-data.js`
2. Only edit this directory alone when the change is purely web simulator tooling or styling
   (such as the right-side debug panel, CSS layout, or texture loader) and does not affect in-game
   behavior or data. When in doubt, update the real plugin code as well.
3. Always keep `data.js` / `equipment-data.js` / `app.js` / `armory.js` synchronized with the plugin changes, reply to the user in
   Chinese, and commit locally in Chinese without running `git push`.
