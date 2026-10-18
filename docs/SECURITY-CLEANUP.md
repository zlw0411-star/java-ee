# 安全清理记录

本文件记录 2026-10-18 针对公开仓库做的隐私与密钥清理，包含改了什么、如何验证、还剩什么风险。保留此文件是为了让后续维护者（包括未来的我）知道当时的判断依据。

## 1. 问题

`java-ee` 仓库曾公开包含三个课程报告文件：

| 文件 | 问题 |
| --- | --- |
| `<姓名学号>-JavaEE.docx`（已移出） | 文件名含真实姓名与学号；`word/document.xml` 正文中也有 |
| `数据库设计文档.docx` | 课程报告正文 |
| `系统总结.docx` | 课程报告正文 |

学号与姓名同时出现，可被用于针对性的社工，也可能被用于尝试登录学校系统（若使用「学号 + 默认密码」）。

同一时期还发现两处配置问题：

- `app.token.secret` 存在真实可用的默认值（`java-ee` 两个服务共 4 处），任何人都能用它伪造跨服务令牌
- `APP_JWT_SECRET` 存在演示用默认值（`air-ticket-system`）
- `.vscode/tasks.json`、`.vscode/settings.json`、`start-backend.ps1` 写有本机绝对路径

## 2. 处理

**已归档（未删除）**：三份 docx 复制到仓库外目录 `学业资料/_归档_课程报告/`，SHA-256 校验与源文件一致：

```
1782112d850551ee8fa2002ff670af627b715678698ed576c08bb08865642515  <姓名学号>-JavaEE.docx
2ecce79998c639e4039674d464e9b7a43b7bb4bd321cd572576a0a41bb8fc72a  数据库设计文档.docx
56f4f73fd1011461b685abb30ba3a73229839cc1a1bb134502a7f5adbe554b01  系统总结.docx
```

**仓库内**：

- `.gitignore` 增加 `*.docx`、`*.doc`、`*.pdf`、`.env`、`*.local.properties`
- `TokenUtil`（两个服务）：`app.token.secret` 不再有默认值。缺失或仍为占位值时**启动即抛异常**，不是静默降级
- `JwtService`（`air-ticket-system`）：同样拒绝空密钥与含 `change-me` / `please-change` 的占位值
- `.vscode/tasks.json`、`settings.json`、`start-backend.ps1` 改为读 `JAVA_HOME` / `MAVEN_HOME` / `NACOS_HOME` / `MYSQL_HOME` / `MYSQL_DATA`，缺失时报错而不是回退到别的安装

**Git 历史**：`java-ee` 删除 `.git` 后重建仓库，按真实模块分成 8 次提交（忽略规则 → 构建 → 数据库 → 用户服务 → 订单服务 → 订单服务测试 → 前端 → 文档）。提交时间是当前真实时间，没有改写为过去时间。

## 3. 验证

| 检查 | 方法 | 结果 |
| --- | --- | --- |
| 工作区无敏感字符串 | 按姓名、学号、旧密钥逐项 `grep -rnF` | 无命中 |
| 所有 Git 对象无敏感字符串 | `git rev-list --all` 后逐 commit `git grep` | 无命中 |
| 其他 7 个仓库历史无姓名/学号 | 同上，逐仓库跑 | 均无命中 |
| 全新克隆可构建 | `git clone` 到空目录后 `mvn package` | BUILD SUCCESS，8 项测试通过 |
| 旧 commit 页面 | 直接访问旧 SHA | 私有化后返回 404 |
| 匿名 API 访问 | 不带 token 请求 commits API | 私有化后返回 404 |

**重要发现**：仅做 `git push --force` **不足以**删除数据。强制推送后，旧 commit 对象仍可通过 GitHub API 匿名读取，docx 内容仍可下载。第一次验证时确实拿到了 `HTTP 200` 和文件内容。

## 4. 当前状态与剩余风险

**仓库已设为 private**，外部无法访问。这解决了「公开可见」的问题，但**没有**解决「GitHub 服务器上仍有旧对象」的问题。

要彻底移除，需要在以下两种方式中选一种：

1. **删除仓库后重建**（推荐，用于 0 star / 0 fork 的课程仓库）——立即、彻底，代价是链接失效
2. **向 GitHub Support 提工单**，提供仓库名、受影响 PR 数、`git-filter-repo` 输出的 First Changed Commit——GitHub 才会做服务器端 GC 并清除缓存视图

判断依据：该仓库 0 star、0 fork、0 PR，删除重建的外部影响接近于零。

## 5. 与代码无关但更紧急的一条

如果学校教务 / 图书馆 / 校园网使用「学号 + 默认密码」，**应立即修改密码**。这一项的实际风险高于 GitHub 上的一切，且与本次清理无关。

## 6. 待办

- [ ] 决定删除重建还是提工单（见第 4 节）
- [ ] 决定后把仓库重新设为 public，并确认新历史正常
- [ ] `personal-website` 中两处「上海电力大学」表述需核对真实性（见 `lib/portfolio-data.ts`）
- [ ] 其他仓库的 README 描述与实际内容不一致的问题（另行处理）
