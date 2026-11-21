# Project Rules 鈥?Maple Underground Series

> These rules are read automatically at the start of every session by Claude / Codex / OpenCode.
> Follow them without being asked. `CLAUDE.md` and `AGENTS.md` are kept identical.

---

## 0. Non-negotiable session rules

1. **Reply to the user in Chinese (涓枃).** All explanations, summaries, and questions you send to
   the user must be written in Chinese, regardless of the language of code or comments.
2. **Commit after every conversation's changes 鈥?in Chinese, and do NOT push.**
   When you finish a unit of work that changed files, run:
   ```bash
   git add -A
   git commit -m "<绠€浣撴垨绻佷綋涓枃锛岀畝杩版湰娆℃敼鍔?"
   ```
   - The commit message MUST be in Chinese and briefly describe what changed.
   - **Never** `git push`. Committing locally is the whole intent; pushing is the user's decision.
3. **Never commit real database secrets.** Real credentials live only in `build-secrets.cmd`,
   which is git-ignored. Only the template `build-secrets.cmd.example` is tracked.
4. **Sync preview website changes with actual Minecraft plugin code (MUST).**
   The web preview simulators under `tools/*-preview/` (such as `tools/collect-ui-preview/`) exist
   ONLY to mirror the real in-game GUIs, rewards, items, and recipes without launching Minecraft.
   When the user asks for any gameplay, UI, text, reward, item, or recipe change while viewing or
   commenting on the preview website:
   - **NEVER edit only the website.** Always update the corresponding real Minecraft plugin Java
     code / YAML configs / generator scripts (`underground_state`, `underground_collect`,
     `underground_item`, `tools/generate_professions.py`, etc.) FIRST or simultaneously, and keep
     the preview website in sync with the plugin.
   - The only exception is a purely web-only visual/tooling tweak (e.g. simulator debug panel,
     CSS layout, or texture loader) that does not change game behavior or data. When in doubt,
     update the real plugin code too.

---

## 1. Project overview

A single series of **7 Paper 26.3 plugins + 1 Velocity bridge** (Java 25, Maven, shaded jars) sharing one MySQL backend.

| Plugin | Role | Notes |
|---|---|---|
| `underground_state` | **MAIN plugin** | Owns player stats/skills/bank/etc. **Owns ALL database credentials.** |
| `underground_caves` | sub-plugin | Dungeons, parties, professions. `depend: [underground_state, underground_item]` |
| `underground_item` | sub-plugin | Custom items/gear/weapons. `depend: [underground_state]` |
| `underground_mayor` | sub-plugin | Mayor NPCs (Citizens). `depend: [Citizens, underground_state]` |
| `underground_collect` | sub-plugin | Collections. `depend: [underground_state]` |
| `underground_island` | sub-plugin | OneBlock islands and coop. `depend: [underground_state]` |
| `underground_quest` | sub-plugin | Quests. `depend: [underground_state, underground_caves]` |
| `underground_velocity` | proxy plugin | Cross-server parties, dungeon instances, and island bridge. Install on Velocity. |

**Rule:** every sub-plugin MUST `depend` on `underground_state` in its `plugin.yml` 鈥?the main
plugin must be installed for any sub-plugin to load. Sub-plugins compile against the main plugin's
jar via Maven `system` scope (see each sub-plugin `pom.xml`), and at runtime access its classes
through the Bukkit `depend` relationship.

---

## 2. Database architecture (CORE RULE 鈥?read before touching any DB code)

**All MySQL access goes through the main plugin's central factory:**
`underground_state/src/main/java/maple/underground_state/db/MySqlConnectionFactory.java`

```java
MySqlConnectionFactory.connect();            // default data source -> "state" schema
MySqlConnectionFactory.connect("caves");     // named data source
MySqlConnectionFactory.connect("skyblock");  // named data source
```

- Credentials are **NOT** read from any server-side config file. They are **baked into the main
  plugin jar at build time** via Maven resource filtering of
  `underground_state/src/main/resources/database.properties`, fed from `build-secrets.cmd`.
- All data sources share ONE host (`DB_HOST`); each schema has its own user/password.
- There is also a local **SQLite** store (`DungeonRoomRewardDB`, `jdbc:sqlite:`) 鈥?that one is a
  local file and is intentionally NOT part of the central MySQL factory.

### Hard prohibitions
- 鉂?Do **not** add `DriverManager.getConnection(...)` for MySQL anywhere outside the factory.
- 鉂?Do **not** hardcode hosts, usernames, or passwords in any `.java` file.
- 鉂?Do **not** create a user-editable `database.yml` / config folder for DB credentials.

### Recipe 鈥?adding a feature that needs the database
1. **Reuse an existing data source** (`state` / `caves` / `skyblock`) when possible 鈥?just call
   `MySqlConnectionFactory.connect("<name>")` and write your `CREATE TABLE IF NOT EXISTS` + queries.
   Put the DB logic in the main plugin when the data is cross-plugin; otherwise in the sub-plugin,
   but still go through the factory.
2. **Only if a genuinely new schema is required**, register a new named data source by editing
   FOUR places consistently (keyword `<name>`):
   - `underground_state/src/main/resources/database.properties` 鈫?add `db.<name>.database/user/password`
   - `underground_state/pom.xml` `<properties>` 鈫?add matching defaults
   - `MySqlConnectionFactory.loadSources()` 鈫?add one `putSource(..., "<name>", "<db>", "<user>")` line
   - `build-secrets.cmd.example` (and your local `build-secrets.cmd`) + `build-all.cmd` `DB_PROPS`
     block 鈫?add `DB_<NAME>_*` variables and their `-D` pass-through
   Then call `MySqlConnectionFactory.connect("<name>")`.

---

## 3. Build process

- **Build everything with `build-all.cmd`** (Windows, Maven + JDK 25 or newer).
  It builds all 8 modules in dependency order and copies shaded jars to `dist/`.
- Real secrets: copy `build-secrets.cmd.example` 鈫?`build-secrets.cmd` (git-ignored) and fill in
  passwords. `build-all.cmd` loads it and injects values into the `underground_state` build only.
- If `build-secrets.cmd` is missing, the build still succeeds using `pom.xml` defaults (blank
  passwords; at runtime the factory falls back to `UNDERGROUND_<KEY>_MYSQL_PASSWORD` env vars).
- **To change any DB credential or host: edit `build-secrets.cmd`, then re-run `build-all.cmd`.**
  There is no runtime config to edit on the server.

---

## 4. Conventions

- Keep changes minimal and match surrounding code style (existing comments are often Traditional
  Chinese 鈥?that's fine; keep them).
- Preserve public method signatures of DB classes (constructors like `(File dataFolder, Logger)`
  are kept for caller compatibility even when the folder is no longer used).
- After finishing work: verify it compiles (`build-all.cmd` or per-module Maven), then commit in
  Chinese per rule 搂0.2.

---

## 5. Web UI preview simulator sync map

When the user points at an element or requests a change from the browser preview
(`tools/collect-ui-preview/`), always sync the real plugin source files below:

| Change requested on the website | Real plugin files that MUST also be updated |
|---|---|
| Collection categories, items, tier thresholds, Lore text, detail pane | `underground_state/src/main/java/maple/underground_state/gui/CollectionInfoGui.java` (and `MainInfoGui.java` / `BookshelfInfoGui.java`) |
| Collection center hub, leaderboard, missing list, guide, commands | `underground_collect/src/main/java/maple/underground_collect/CollectionMenus.java`, `CollectCommand.java` |
| Collection rewards (Maple XP, skill XP, claims, upgrade notifications) | `underground_collect/src/main/java/maple/underground_collect/CollectionRewardService.java`, `CollectionMath.java`, `underground_state/src/main/java/maple/underground_state/collection/CollectionProgressManager.java` |
| Compressed materials, profession gear, 3x3 crafting recipes | `tools/generate_professions.py` -> regenerate `underground_item/src/main/resources/professions.yml` (and check `ProfessionCatalog.java` / `ProfessionCrafting.java` / `ProfessionBook.java`) |
| Preview site data & UI | Regenerate `tools/collect-ui-preview/data.js` and update `tools/collect-ui-preview/app.js` / `index.html` / `styles.css` |
