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

## 4. 最终处理：删除仓库并重建（已完成）

`git push --force` 只能让旧 commit 从默认分支上消失，**不能删除对象**。实测强制推送后，旧 SHA 仍可通过 API 匿名读取，docx 仍可下载。

因此采取的方式是删除仓库后重建：

1. `gh auth refresh -h github.com -s delete_repo` 取得删除权限
2. `gh repo delete zlw0411-star/java-ee --yes`
3. `gh repo create zlw0411-star/java-ee --public --source=. --remote=origin --push`

### 验证证据

| 检查 | 结果 |
| --- | --- |
| 旧 commit `1155fcf`（匿名） | `422 No commit found for SHA` |
| 旧 commit `7249236`（匿名） | `422 No commit found for SHA` |
| 旧 commit `1e23eb6`（中间态，含敏感记录文件） | `422 No commit found for SHA` |
| 旧 docx 路径直取 | `404` |
| 新 HEAD `965243e`（匿名） | `200`，对照正常 |
| GitHub Code Search：学号 / 姓名 / 旧密钥 | `0` / `0` / `0` |
| 匿名列仓库根目录 | 仅 10 项，无 docx |

结论：旧对象在 GitHub 服务器上已不可达。比提工单更快、更彻底，代价是旧链接失效（该仓库 0 star / 0 fork，无外部引用）。

## 5. 与代码无关但更紧急的一条

如果学校教务 / 图书馆 / 校园网使用「学号 + 默认密码」，**应立即修改密码**。这一项的实际风险高于 GitHub 上的一切，且与本次清理无关。

## 6. 其他同类问题（已处理）

| 仓库 | 问题 | 处理 |
| --- | --- | --- |
| `air-ticket-system` | `APP_JWT_SECRET` 有演示默认值；`start-backend.ps1` 写死 `E:\JDK17`、`E:\Maven` | 密钥无默认值，`JwtService` 拒绝空值与占位值；脚本改读 `JAVA_HOME`/`MAVEN_HOME` |
| `campus-card-system` | `scripts/tools.ps1` 内固定 `E:\JDK17`、`E:\Maven`、`E:\MySQL`、`E:\nodejs` 候选路径 | 只依赖 `PATH` 与 `*_HOME` 环境变量，找不到时报错 |

## 7. 待办

- [x] 删除重建完成，仓库已恢复 public
- [ ] `personal-website` 中两处「上海电力大学」表述需核对真实性（`lib/portfolio-data.ts` 第 38、220 行附近）——属事实性内容，未经确认不应擅自修改
- [ ] 若仓库日后重新公开且需保留敏感历史，改用 `git filter-repo --sensitive-data-removal` + Support 工单，而非 force push

## 8. 本次的一个教训

清理记录文件本身也是泄露面。第一版 `SECURITY-CLEANUP.md` 把姓名和学号原样写进了文档，等于换个地方重新公开。已改为占位描述，只保留可核对的 SHA-256。结论：**记录清理过程时不要复现待清除的值。**
