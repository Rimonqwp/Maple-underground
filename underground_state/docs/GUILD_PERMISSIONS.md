# 公会身份组与权限设计

本文档定义公会身份组、权限、银行额度和后续 GUI 的实现规则。

## 默认身份组

每个新公会只自动创建两个身份组：

- 会长
- 成员

会长是最高权限身份组，默认拥有全部权限。会长身份组不能删除，不能被其他身份组修改权限，不能被其他身份组踢出或降级。

成员是默认普通身份组，默认没有任何管理权限。成员只能加入公会、查看基础公会页面，以及主动离开公会。成员不能邀请玩家，不能操作公会银行，不能踢人，不能编辑资料，不能创建或编辑身份组。

其他身份组全部由会长或拥有身份组管理权限的玩家自己创建和调整。系统不预设“管理员”“精英成员”等身份组，避免固定死每个公会的管理结构。

## 身份组字段

建议数据库表：`player_guild_roles`

- `id`: 身份组 ID
- `guild_id`: 所属公会
- `name`: 身份组显示名，可支持 RGB 名字格式
- `system_role`: 系统身份组类型，`OWNER` / `MEMBER` / `CUSTOM`
- `priority`: 排序和管理优先级，数字越小越高
- `is_default`: 新玩家加入公会时是否默认进入该身份组
- `created_at`: 创建时间
- `updated_at`: 更新时间

每个公会可以单独设置新成员默认身份组。默认情况下是“成员”，之后可以在身份组设定里改成其他自定义身份组。

## 权限存储

建议数据库表：`player_guild_role_permissions`

- `role_id`: 身份组 ID
- `permission`: 权限 ID
- `enabled`: 是否开启
- `updated_at`: 更新时间

会长身份组不需要逐项写权限，逻辑上永远视为全部开启。成员身份组默认所有权限关闭。自定义身份组按数据库里的开关判断。

## 公会银行

公会银行和玩家个人银行完全分开。

个人银行仍然使用玩家自己的银行入口和个人银行数据。公会银行使用公会页面里的“公会银行”入口，数据按 `guild_id` 存储。

建议数据库表：

- `player_guild_bank_accounts`: 存公会银行余额
- `player_guild_bank_logs`: 存公会银行流水
- `player_guild_role_bank_limits`: 存身份组的银行额度

### 银行权限

`guild.bank.deposit`

允许玩家存入公会银行。

`guild.bank.deposit.limited`

允许有限额度存入。额度由当前公会当前身份组的额度表决定。

`guild.bank.deposit.unlimited`

允许无限额度存入。拥有此权限时忽略存入额度。

`guild.bank.withdraw`

允许玩家从公会银行取出。

`guild.bank.withdraw.limited`

允许有限额度取出。额度由当前公会当前身份组的额度表决定。

`guild.bank.withdraw.unlimited`

允许无限额度取出。该权限风险最高，默认只建议会长拥有。

`guild.bank.view_logs`

允许查看公会银行流水。

`guild.bank.set_limits`

允许编辑身份组的公会银行额度。

### 银行额度

额度是每个公会单独配置的，也需要写进数据库。判断权限时先判断该身份组是否能存入/取出，再判断是否无限额度，最后才判断有限额度。

建议额度字段：

- `deposit_daily`: 每日存入上限
- `withdraw_daily`: 每日取出上限
- `deposit_weekly`: 每周存入上限
- `withdraw_weekly`: 每周取出上限

额度为 `0` 时表示没有额度。只有拥有 `*.limited` 权限时才使用额度判断。拥有 `*.unlimited` 权限时直接跳过额度判断。

## 邀请权限

`guild.invite.create`

允许邀请没有公会的玩家加入本公会。

`guild.invite.cancel`

允许取消本公会发出的邀请。

`guild.invite.view`

允许查看待处理邀请列表。

## 成员管理权限

`guild.member.leave`

允许玩家主动离开公会。这个权限可以作为基础行为保留，成员默认允许自己离开。

`guild.member.kick`

允许踢出公会成员。实现时建议不能踢出同级或更高优先级身份组。

`guild.member.blacklist.add`

允许把玩家加入公会黑名单。

`guild.member.blacklist.remove`

允许解除玩家黑名单。

`guild.member.blacklist.view`

允许查看公会黑名单。

`guild.member.role.assign`

允许调整成员身份组。实现时建议只能分配低于自己优先级的身份组。

## 身份组管理权限

`guild.role.create`

允许创建新的身份组。

`guild.role.delete`

允许删除身份组。会长身份组不能删除，默认身份组不能直接删除。

`guild.role.rename`

允许修改身份组名称。

`guild.role.priority`

允许调整身份组排序和管理优先级。

`guild.role.permissions.edit`

允许编辑身份组权限。该权限风险很高，默认只建议会长拥有。

`guild.role.default.set`

允许设置新玩家加入公会后的默认身份组。

## 公会资料权限

`guild.info.name.edit`

允许修改公会名字。公会名字支持 `&#RRGGBB` RGB 格式。

`guild.info.description.edit`

允许修改公会简介、资料或公告。

`guild.info.website.edit`

允许设置或修改公会网站地址。

`guild.info.settings.edit`

允许修改公会基础设定，例如是否允许申请加入、是否公开展示成员。

## 建议预留权限

`guild.application.view`

允许查看加入申请。

`guild.application.accept`

允许接受加入申请。

`guild.application.deny`

允许拒绝加入申请。

`guild.audit.view`

允许查看公会操作日志，包括身份组修改、踢人、黑名单和资料修改。

`guild.transfer_owner`

允许转让会长。建议只允许当前会长使用，并要求二次确认。

`guild.disband`

允许解散公会。建议只允许会长使用，并要求二次确认。

## GUI 规划

公会主页点击红石火把“公会设定”进入设定页。

设定页当前只放“身份组设定”。之后可以继续加公会资料、银行设置、黑名单等入口。

身份组设定页显示当前公会所有身份组，并提供创建身份组入口。身份组列表里应显示：

- 身份组名字
- 是否默认身份组
- 是否系统身份组
- 是否可编辑

点击身份组后进入权限编辑页。权限编辑页使用染料作为开关：

- 绿色染料：权限开启
- 灰色染料：权限关闭
- 红色染料：禁止编辑，例如会长身份组的权限

权限页需要按分类排版，例如银行、邀请、成员管理、身份组管理、公会资料。每个权限项都显示权限 ID、中文说明和当前状态。
