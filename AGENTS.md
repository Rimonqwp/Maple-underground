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

---

## 1. Project overview

A single series of **5 Spigot/Paper plugins** (Java 21, Maven, shaded jars) sharing one MySQL backend.

| Plugin | Role | Notes |
|---|---|---|
| `underground_state` | **MAIN plugin** | Owns player stats/skills/bank/etc. **Owns ALL database credentials.** |
| `underground_caves` | sub-plugin | Dungeons, parties, professions. `depend: [underground_state, underground_item]` |
| `underground_item` | sub-plugin | Custom items/gear/weapons. `depend: [underground_state]` |
| `underground_mayor` | sub-plugin | Mayor NPCs (Citizens). `depend: [Citizens, underground_state]` |
| `underground_collect` | sub-plugin | Collections. `depend: [underground_state]` |

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

- **Build everything with `build-all.cmd`** (Windows, IntelliJ-bundled Maven + Zulu JDK 21).
  It builds all 5 plugins in dependency order and copies shaded jars to `dist/`.
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
