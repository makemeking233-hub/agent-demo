package com.example.agent.session;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 工作区存储 v2（align-dsh-workspace）：对齐 DSH Web workspace 实体模型。
 *
 * <p>数据布局：
 *
 * <pre>
 * &lt;agentDataDir&gt;/
 * ├── sessions/                          # 默认工作区 "agent-demo"（现状不变）
 * ├── workspaces/
 * │   ├── order.json                     # durable order: {version:1, order:[names...]}
 * │   └── &lt;name&gt;/
 * │       ├── meta.json                   # v2: {version:2, name, id, path, title, created_at, updated_at, session_ids}
 * │       └── sessions/&lt;id&gt;.jsonl     # 会话落盘（保持不变）
 * </pre>
 *
 * <p>v1 meta.json 自动迁移到 v2（首次 read 时补默认字段，不写回；迁移在
 * {@link #ensureOrderInitialized} 中实际落盘）。
 *
 * <p>DSH 对齐要点：
 * <ul>
 *   <li>realpath 规范化 + 同 canonical path 最多 1 record
 *   <li>id (UUID) 稳定，title 与 path 完全解耦（同名 title 允许）
 *   <li>durable order 持久化（{@code order.json}）+ {@link #insertBefore} 拖拽重排
 *   <li>{@link Status#MISSING_DIR} 容忍（目录不存在时 record 保留）
 *   <li>{@link #delete} 仅删 record，不动 dir / session log / sessions
 * </ul>
 */
public final class WorkspaceStore {
    private static final Logger log = LoggerFactory.getLogger(WorkspaceStore.class);

    /** 默认工作区名（对应顶层 sessions/）。 */
    public static final String DEFAULT_WORKSPACE = "agent-demo";

    private static final String META = "meta.json";
    private static final String ORDER = "order.json";
    private static final String META_VERSION = "2";
    private static final ObjectMapper JSON = new ObjectMapper();

    /** name 合法性：{@code [A-Za-z0-9._-]}，≤64 字符，且非保留的 {@link #DEFAULT_WORKSPACE}。 */
    private static final Pattern NAME_RE = Pattern.compile("^[A-Za-z0-9._-]{1,64}$");

    private WorkspaceStore() {}

    /** 目录存在性。{@link #MISSING_DIR} 时 record 保留，UI 标红。 */
    public enum Status { OK, MISSING_DIR }

    /**
     * 一个工作区：稳定 id + realpath + display title + sessions 与余项。
     *
     * <p>{@link #path} 是 {@code fs.realpath} 规范化路径；{@link #name} 是目录名（持久化）；
     * {@link #title} 是 display name（可与 name 不同）；{@link #id} 是 stable UUID。
     */
    public record Workspace(
            String name,
            String id,
            Path path,
            String title,
            long createdAt,
            long updatedAt,
            Path sessionsDir,
            List<String> sessionIds,
            Status status) {}

    /** 创建结果：成功携带 {@link Workspace}，失败携带错误码（与 v1 兼容）。 */
    public record CreateResult(boolean ok, Workspace workspace, String error) {
        public static CreateResult ok(Workspace w) {
            return new CreateResult(true, w, null);
        }

        public static CreateResult err(String error) {
            return new CreateResult(false, null, error);
        }
    }

    // ---------- 默认工作区 ----------

    /** 默认工作区（运行目录 = 当前进程 user.dir；存档 = 顶层 sessions/）。 */
    public static Workspace defaultWorkspace(Path agentDataDir) {
        return new Workspace(
                DEFAULT_WORKSPACE,
                DEFAULT_WORKSPACE,
                Paths.get(System.getProperty("user.dir")),
                DEFAULT_WORKSPACE,
                0L,
                0L,
                agentDataDir.resolve("sessions"),
                List.of(),
                Status.OK);
    }

    // ---------- create（v2 单参 + 自动派生 name/title）----------

    /**
     * 创建工作区（v2 单参入口）。{@code dir} → realpath 规范化 → 查同 canonical path 复用；
     * name 与 title 都从 {@code basename(path)} 派生（同名 title 允许）。
     *
     * @return 成功 {@link CreateResult#ok}；失败带错误码（dir_not_absolute / dir_not_found /
     *     invalid_name）
     */
    public static CreateResult create(Path agentDataDir, Path dir) {
        if (dir == null) return CreateResult.err("dir_not_found");
        if (!dir.isAbsolute()) return CreateResult.err("dir_not_absolute");
        Path canonical;
        try {
            canonical = dir.toRealPath();
        } catch (IOException e) {
            return CreateResult.err("dir_not_found");
        }
        if (!Files.isDirectory(canonical)) return CreateResult.err("dir_not_found");

        // 派生 name（basename）
        String basename = canonical.getFileName().toString();
        String nameErr = validateName(basename);
        if (nameErr != null) return CreateResult.err(nameErr);

        Path wsRoot = agentDataDir.resolve("workspaces");
        Path wsDir = wsRoot.resolve(basename);

        // 同 canonical path 复用
        Workspace existing = loadByCanonicalPath(agentDataDir, canonical);
        if (existing != null) return CreateResult.ok(existing);

        // 新建
        try {
            Files.createDirectories(wsDir.resolve("sessions"));
        } catch (IOException e) {
            log.warn("创建 workspace 目录失败: name={}", basename, e);
            return CreateResult.err("workspace_create_failed");
        }

        long now = System.currentTimeMillis();
        String id = UUID.randomUUID().toString();
        Workspace ws = new Workspace(basename, id, canonical, basename, now, now,
                wsDir.resolve("sessions"), List.of(), Status.OK);
        try {
            writeMeta(wsDir, ws);
            appendOrder(agentDataDir, basename);
        } catch (IOException e) {
            log.warn("写入 workspace 元数据失败: name={}", basename, e);
            return CreateResult.err("workspace_create_failed");
        }
        return CreateResult.ok(ws);
    }

    /**
     * 旧 v1 入口（兼容 add-workspaces-and-rename 时期 API）：显式传 name。
     * v2 推荐用 {@link #create(Path, Path)}；此 overload 仍按 v2 schema 落盘。
     */
    public static CreateResult create(Path agentDataDir, String name, String dir) {
        if (name == null || name.isBlank()) return CreateResult.err("name_invalid");
        if (dir == null || dir.isBlank()) return CreateResult.err("dir_not_found");
        String nameErr = validateName(name);
        if (nameErr != null) return CreateResult.err(nameErr);
        Path dirPath;
        try {
            dirPath = Paths.get(dir);
        } catch (Exception e) {
            return CreateResult.err("dir_not_found");
        }
        if (!dirPath.isAbsolute()) return CreateResult.err("dir_not_absolute");

        Path canonical;
        try {
            canonical = dirPath.toRealPath();
        } catch (IOException e) {
            return CreateResult.err("dir_not_found");
        }
        if (!Files.isDirectory(canonical)) return CreateResult.err("dir_not_found");

        // 同 canonical path 复用（不重命名）
        Workspace existing = loadByCanonicalPath(agentDataDir, canonical);
        if (existing != null) return CreateResult.ok(existing);

        Path wsRoot = agentDataDir.resolve("workspaces");
        Path wsDir = wsRoot.resolve(name);
        try {
            Files.createDirectories(wsDir.resolve("sessions"));
        } catch (IOException e) {
            return CreateResult.err("workspace_create_failed");
        }

        long now = System.currentTimeMillis();
        String id = UUID.randomUUID().toString();
        Workspace ws = new Workspace(name, id, canonical, name, now, now,
                wsDir.resolve("sessions"), List.of(), Status.OK);
        try {
            writeMeta(wsDir, ws);
            appendOrder(agentDataDir, name);
        } catch (IOException e) {
            return CreateResult.err("workspace_create_failed");
        }
        return CreateResult.ok(ws);
    }

    // ---------- delete（DSH #2）----------

    /** 删 workspace record（不动 dir / session log）。不在 record 中即返回 false。 */
    public static boolean delete(Path agentDataDir, String name) {
        if (name == null || name.isBlank()) return false;
        if (name.equals(DEFAULT_WORKSPACE)) return false;
        Path wsDir = agentDataDir.resolve("workspaces").resolve(name);
        if (!Files.isDirectory(wsDir)) return false;
        try {
            try (var stream = Files.walk(wsDir)) {
                Iterator<Path> it = stream.sorted((a, b) -> b.compareTo(a)).iterator();
                while (it.hasNext()) Files.delete(it.next());
            }
            removeFromOrder(agentDataDir, name);
            return true;
        } catch (IOException e) {
            log.warn("删除 workspace 失败: name={}", name, e);
            return false;
        }
    }

    // ---------- rename（DSH #3）----------

    /** 改 display title（不动 dir/path/name/id）。失败返回 false。 */
    public static boolean rename(Path agentDataDir, String name, String newTitle) {
        if (name == null || name.isBlank()) return false;
        if (newTitle == null) return false;
        String trimmed = newTitle.trim();
        if (trimmed.isEmpty()) return false;
        Path wsDir = agentDataDir.resolve("workspaces").resolve(name);
        if (!Files.isDirectory(wsDir)) return false;
        Workspace ws = loadMeta(wsDir, false);
        if (ws == null) return false;
        long now = System.currentTimeMillis();
        Workspace updated = new Workspace(ws.name(), ws.id(), ws.path(), trimmed,
                ws.createdAt(), Math.max(now, ws.updatedAt()),
                ws.sessionsDir(), ws.sessionIds(), ws.status());
        try {
            writeMeta(wsDir, updated);
            return true;
        } catch (IOException e) {
            log.warn("rename workspace 失败: name={}", name, e);
            return false;
        }
    }

    // ---------- insertBefore（DSH #4）----------

    /**
     * 把 name 移动到 beforeName 之前；beforeName=null 移到末尾。
     * 同名已在当前位置、name 不存在、beforeName 不存在 → 返回 false。
     */
    public static boolean insertBefore(Path agentDataDir, String name, String beforeName) {
        if (name == null || name.isBlank()) return false;
        List<String> order = durableOrder(agentDataDir);
        if (!order.contains(name)) return false;
        if (beforeName != null && !beforeName.isBlank() && !order.contains(beforeName)) return false;
        if (beforeName != null && beforeName.equals(name)) return true;

        List<String> next = new ArrayList<>(order);
        next.remove(name);
        if (beforeName == null || beforeName.isBlank()) {
            next.add(name);
        } else {
            int idx = next.indexOf(beforeName);
            next.add(idx, name);
        }
        try {
            writeOrder(agentDataDir, next);
            return true;
        } catch (IOException e) {
            log.warn("insertBefore 失败: name={}", name, e);
            return false;
        }
    }

    // ---------- list / get / findById / lookup ----------

    /** 列出全部 workspace（默认在前 + 按 durable order 排序）；含 status。 */
    public static List<Workspace> list(Path agentDataDir) {
        List<Workspace> out = new ArrayList<>();
        out.add(defaultWorkspace(agentDataDir));

        Path wsRoot = agentDataDir.resolve("workspaces");
        if (!Files.isDirectory(wsRoot)) return out;

        // 按 durable order 遍历；order 中不存在的 workspace 追加到末尾
        List<String> order = durableOrder(agentDataDir);
        for (String name : order) {
            Workspace ws = load(agentDataDir, name);
            if (ws != null) out.add(ws);
        }
        // 任何不在 order 中的也列出（按名字母序）
        try (var stream = Files.list(wsRoot)) {
            List<String> extra = new ArrayList<>();
            stream.filter(Files::isDirectory)
                    .map(p -> p.getFileName().toString())
                    .filter(n -> !order.contains(n))
                    .sorted()
                    .forEach(extra::add);
            for (String name : extra) {
                Workspace ws = load(agentDataDir, name);
                if (ws != null) out.add(ws);
            }
        } catch (IOException e) {
            log.warn("枚举 workspace 失败: {}", wsRoot, e);
        }
        return out;
    }

    /** 按名称 lookup；返回的 record 含 status（目录不在则 status='missing_dir'）。 */
    public static Workspace get(Path agentDataDir, String name) {
        if (name == null || name.isBlank()) return null;
        if (name.equals(DEFAULT_WORKSPACE)) return defaultWorkspace(agentDataDir);
        return load(agentDataDir, name);
    }

    /** 同 {@link #get}；保留 v1 list-by-name 入口。 */
    public static Workspace list(Path agentDataDir, String name) {
        return get(agentDataDir, name);
    }

    /** 按 stable id lookup；DSH rename 不改 id。 */
    public static Optional<Workspace> findById(Path agentDataDir, String id) {
        if (id == null || id.isBlank()) return Optional.empty();
        if (id.equals(DEFAULT_WORKSPACE)) return Optional.of(defaultWorkspace(agentDataDir));
        for (Workspace ws : list(agentDataDir)) {
            if (id.equals(ws.id())) return Optional.of(ws);
        }
        return Optional.empty();
    }

    public static Path sessionsDirFor(Path agentDataDir, String name) {
        Workspace ws = get(agentDataDir, name);
        return ws != null ? ws.sessionsDir() : defaultWorkspace(agentDataDir).sessionsDir();
    }

    public static boolean exists(Path agentDataDir, String name) {
        if (name == null || name.isBlank()) return false;
        if (name.equals(DEFAULT_WORKSPACE)) return true;
        return Files.isDirectory(agentDataDir.resolve("workspaces").resolve(name));
    }

    /** 读取当前持久化的 durable order（不含默认 workspace）。 */
    public static List<String> durableOrder(Path agentDataDir) {
        Path orderFile = agentDataDir.resolve("workspaces").resolve(ORDER);
        if (!Files.isRegularFile(orderFile)) return List.of();
        try {
            JsonNode n = JSON.readTree(Files.readString(orderFile, StandardCharsets.UTF_8));
            JsonNode arr = n.path("order");
            if (!arr.isArray()) return List.of();
            List<String> out = new ArrayList<>();
            for (JsonNode e : arr) out.add(e.asText());
            return out;
        } catch (Exception e) {
            log.warn("读取 order.json 失败: {}", orderFile, e);
            return List.of();
        }
    }

    // ---------- 内部 helper ----------

    private static Workspace load(Path agentDataDir, String name) {
        if (name == null || name.isBlank()) return null;
        Path wsDir = agentDataDir.resolve("workspaces").resolve(name);
        if (!Files.isDirectory(wsDir)) return null;
        Workspace ws = loadMeta(wsDir, true);
        if (ws == null) return null;
        Status status = Files.isDirectory(ws.path()) ? Status.OK : Status.MISSING_DIR;
        if (status != ws.status()) {
            ws = new Workspace(ws.name(), ws.id(), ws.path(), ws.title(),
                    ws.createdAt(), ws.updatedAt(), ws.sessionsDir(), ws.sessionIds(), status);
        }
        return ws;
    }

    /** 读 meta.json；缺字段补默认（v1→v2 migration 内存里做）。 */
    private static Workspace loadMeta(Path wsDir, boolean migrate) {
        Path meta = wsDir.resolve(META);
        if (!Files.isRegularFile(meta)) {
            return null;
        }
        try {
            String content = Files.readString(meta, StandardCharsets.UTF_8);
            JsonNode n = JSON.readTree(content);
            String name = n.path("name").asText(wsDir.getFileName().toString());
            String id = n.path("id").asText(name);
            // v2 schema 用 "path"；v1 schema 用 "dir"（兼容旧 meta.json）
            String dirRaw = n.has("path") ? n.path("path").asText(null) : n.path("dir").asText(null);
            if (dirRaw == null) return null;
            Path path;
            try {
                path = Paths.get(dirRaw).toRealPath();
            } catch (IOException e) {
                path = Paths.get(dirRaw);
            }
            String title = n.path("title").asText(name);
            long createdAt = n.path("created_at").asLong(System.currentTimeMillis());
            long updatedAt = n.path("updated_at").asLong(createdAt);
            List<String> sessionIds = new ArrayList<>();
            JsonNode sids = n.path("session_ids");
            if (sids.isArray()) {
                for (JsonNode s : sids) sessionIds.add(s.asText());
            }
            return new Workspace(name, id, path, title, createdAt, updatedAt,
                    wsDir.resolve("sessions"), List.copyOf(sessionIds), Status.OK);
        } catch (Exception e) {
            log.warn("读取 workspace meta.json 失败: {}", meta, e);
            return null;
        }
    }

    /** 写 meta.json（v2 schema）。 */
    private static void writeMeta(Path wsDir, Workspace ws) throws IOException {
        ObjectNode root = JSON.createObjectNode();
        root.put("version", Integer.parseInt(META_VERSION));
        root.put("name", ws.name());
        root.put("id", ws.id());
        root.put("path", ws.path().toString());
        root.put("title", ws.title());
        root.put("created_at", ws.createdAt());
        root.put("updated_at", ws.updatedAt());
        ArrayNode sids = root.putArray("session_ids");
        for (String s : ws.sessionIds()) sids.add(s);
        Files.writeString(wsDir.resolve(META), JSON.writeValueAsString(root), StandardCharsets.UTF_8);
    }

    /** 写 order.json。 */
    private static void writeOrder(Path agentDataDir, List<String> order) throws IOException {
        Path orderFile = agentDataDir.resolve("workspaces").resolve(ORDER);
        Files.createDirectories(orderFile.getParent());
        ObjectNode root = JSON.createObjectNode();
        root.put("version", 1);
        ArrayNode arr = root.putArray("order");
        for (String s : order) arr.add(s);
        Files.writeString(orderFile, JSON.writeValueAsString(root), StandardCharsets.UTF_8);
    }

    private static void appendOrder(Path agentDataDir, String name) {
        List<String> order = new ArrayList<>(durableOrder(agentDataDir));
        if (!order.contains(name)) {
            // DSH 语义：新建 prepend 到 durable order（最新在前）
            order.add(0, name);
            try {
                writeOrder(agentDataDir, order);
            } catch (IOException e) {
                log.warn("appendOrder 失败: name={}", name, e);
            }
        }
    }

    private static void removeFromOrder(Path agentDataDir, String name) {
        List<String> order = new ArrayList<>(durableOrder(agentDataDir));
        if (order.remove(name)) {
            try {
                writeOrder(agentDataDir, order);
            } catch (IOException e) {
                log.warn("removeFromOrder 失败: name={}", name, e);
            }
        }
    }

    /** 查同 canonical path 已有的 workspace（用于 create 复用语义）。直接扫文件系统避免递归调 list()。 */
    private static Workspace loadByCanonicalPath(Path agentDataDir, Path canonical) {
        Path wsRoot = agentDataDir.resolve("workspaces");
        if (!Files.isDirectory(wsRoot)) return null;
        try (var stream = Files.list(wsRoot)) {
            var iter = stream.filter(Files::isDirectory).iterator();
            while (iter.hasNext()) {
                Path wsDir = iter.next();
                Workspace ws = loadMeta(wsDir, true);
                if (ws == null) continue;
                try {
                    Path p = ws.path().toRealPath();
                    if (p.equals(canonical)) return ws;
                } catch (IOException ignored) {
                    // 路径无法 resolve（missing-dir）；跳过
                }
            }
        } catch (IOException e) {
            log.warn("扫描 workspaces/ 失败: {}", wsRoot, e);
        }
        return null;
    }

    /** name 合法性校验：[A-Za-z0-9._-] ≤64，非默认名。 */
    private static String validateName(String name) {
        if (name == null || name.isBlank()) return "name_invalid";
        if (!NAME_RE.matcher(name).matches()) return "name_invalid";
        if (name.equals(DEFAULT_WORKSPACE)) return "name_invalid";
        return null;
    }
}