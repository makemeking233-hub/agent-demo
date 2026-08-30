# Tasks: Skills 系统

## 1. 数据模型：Skill

- [x] 1.1 新增 `Skill` record：`(name, description, content, dir)`

## 2. 发现与解析：SkillCatalog

- [x] 2.1 `SkillCatalog.discover(List<Path> roots)`：一级深度扫描 `<name>/SKILL.md` 或 `<name>.md`，无 frontmatter/缺失 name/description 时跳过（WARN）
- [x] 2.2 frontmatter 解析（`---` 包裹 + `key: value`，取 name/description），正文保留

## 3. Skill 作为工具：SkillTool + 注册

- [x] 3.1 新增 `SkillTool implements Tool<String, String>`：name=skill名，description=frontmatter描述，execute 返回正文；isReadOnly=true，checkPermissions=allow
- [x] 3.2 `ToolRegistry.registerSkillTools(registry, List<Skill>)` 逐个注册

## 4. 接入：AgentLoopFactory

- [x] 4.1 `AgentLoopFactory.buildTools` 调 `ToolRegistry.registerSkillTools(tools, SkillCatalog.discover(roots))`（用户级 + 项目级目录）

## 5. 测试与验证

- [x] 5.1 新增 `SkillCatalogTest`（发现/解析/跳过）与 `SkillToolTest`（工具名/描述/execute 返回正文）
- [x] 5.2 适配 `ToolRegistryTest`
- [x] 5.3 `mvn -pl agent-core verify` 全绿（jacoco 门禁达标）
- [x] 5.4 commit + push（中文 Conventional Commits）
