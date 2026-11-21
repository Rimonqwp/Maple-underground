# Paper 26.3 升级说明

本系列的 7 个 Paper 插件统一针对 `26.3.build.140-beta` 编译，
`plugin.yml` 的 `api-version` 为 `26.3`。Velocity 桥接模块保留
`3.5.0-SNAPSHOT` API，全部模块使用 Java 25 编译。

## 构建

1. 安装 JDK 25 或更新版本，将 `JAVA_HOME` 指向 JDK 目录。
   `build-all.cmd` 优先使用符合要求的 `JAVA_HOME`，否则尝试
   `C:\Program Files\Zulu\zulu-25`。
2. 安装 Maven，配置 `MAVEN_HOME`，或使用脚本能找到的 IntelliJ 内置 Maven。
3. 保留本机被 Git 忽略的 `build-secrets.cmd`，执行 `build-all.cmd`。
   脚本按依赖顺序执行 `clean package`，将新 JAR 复制到 `dist/`。

Paper API 从 26.1 开始使用新的构建版本格式，不能使用
`26.3-R0.1-SNAPSHOT`。这里固定构建号，避免不同模块解析出不同 API。
Citizens 编译依赖更新至 `2.0.44-SNAPSHOT`，Lombok 更新至 `1.18.48`，
并显式启用地城模块的注解处理，以适应新版 JDK。

## 部署

- Paper 后端需要 Java 25 或更新版本以及 Paper 26.3。
- 将需要的 Paper 插件 JAR 放入后端 `plugins/`；所有子插件都需要
  `underground_state`，地城还需要 `underground_item`，任务还需要地城。
- 市长模块需要服务端安装支持 26.3 的 Citizens。
- `underground_velocity-26.3.jar` 放入 Velocity 代理的 `plugins/`，
  代理也使用 Java 25 或更新版本启动。
- 已有 Velocity 配置不会自动覆盖。升级时检查代理插件 `config.yml`
  中的 `java-command` 和 `paper-jar`，以及副本模板目录里的服务端 JAR。
  新配置默认使用 Zulu 25 与 `paper-26.3.jar`；将下载的 Paper JAR 改成
  这个文件名，或把 `paper-jar` 设置为实际文件名。

## 验证与限制

- 8 个模块分别通过新版 API 的编译打包。
- 收集奖励计算的 2 个现有测试通过。
- 当前 Maven Central 尚无 `mockbukkit-v26.3`，测试依赖采用
  `mockbukkit-v26.2:4.116.1`。核心模块的 4 个 MockBukkit 测试在
  注册表初始化时失败（缺少 `minecraft:decorated_pot_pattern` 数据），
  因此不能作为 26.3 游戏行为已验证的依据。
- 尚未进行真实 Paper 26.3 服务端启动和玩法联调；上线前需要验证
  NPC、地城、装备、岛屿传送和跨服实例。
- 当前固定的 Paper 26.3 API 属于 Beta 构建。

参考：[Paper 项目配置](https://docs.papermc.io/paper/dev/project-setup/)、
[Paper 26.3 发布说明](https://papermc.io/news/26-3/)、
[Citizens 下载](https://wiki.citizensnpcs.co/Downloads)。
