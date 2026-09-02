package com.example.agent.session;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 工作区存储（add-workspaces-and-rename）：把工作区作为"真实运行目录"持久化到
 * {@code <agentDataDir>/workspaces/<name>/{meta.json, sessions/}}。
 *
 * <p>默认工作区 {@value #DEFAULT_WORKSPACE} 映射到顶层 {@code <agentDataDir>/sessions/}（其运行目录为
 * {@code user.dir}），不迁移既有会话。其余工作区按 {@code meta.json{name,dir,created_at}} 记录运行目录。
 *
 * <p>数据布局：
 *
 * <pre>
 * &lt;agentDataDir&gt;/
 * ├── sessions/                # 默认工作区 "agent-demo"（现状不变）
 * └── workspaces/&lt;name&gt;/
 *     ├── meta.json            # { name, dir, created_at }
 *     └── sessions/&lt;id&gt;.jsonl  # 该工作区会话
 * </pre>
 */
public final class WorkspaceStore {
    private static final Logger log = LoggerFactory.getLogger(WorkspaceStore.class);

    /** 默认工作区名（对应顶层 sessions/）。 */
    public static final String DEFAULT_WORKSPACE = "agent-demo";

    private static final String META = "meta.json";
    private static final ObjectMapper JSON = new ObjectMapper();

    private WorkspaceStore() {}

    /** 一个工作区：名称、运行目录、该工作区的会话存储目录。 */
    public record Workspace(String name, Path dir, Path sessionsDir) {}

    /** 创建结果：成功携带 {@link Workspace}，失败携带错误码。 */
    public record CreateResult(boolean ok, Workspace workspace, String error) {
        static CreateResult ok(Workspace w) {
            return new CreateResult(true, w, null);
        }

        static CreateResult err(String error) {
            return new CreateResult(false, null, error);
        }
    }

    /** 默认工作区（运行目录 = 当前进程 user.dir；存档 = 顶层 sessions/）。 */
    public static Workspace defaultWorkspace(Path agentDataDir) {
        return new Workspace(
                DEFAULT_WORKSPACE,
                Paths.get(System.getProperty("user.dir")),
                agentDataDir.resolve("sessions"));
    }

    /** 列出所有工作区（默认工作区在前，其余按名称排序）。 */
    public static List<Workspace> list(Path agentDataDir) {
        List<Workspace> out = new ArrayList<>();
        out.add(defaultWorkspace(agentDataDir));
        Path wsRoot = agentDataDir.resolve("workspaces");
        if (!Files.isDirectory(wsRoot)) return out;
        try (var stream = Files.list(wsRoot)) {
            stream.filter(Files::isDirectory)
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .map(WorkspaceStore::loadWorkspace)
                    .filter(java.util.Objects::nonNull)
                    .forEach(out::add);
        } catch (IOException e) {
            log.warn("枚举工作区失败: {}", wsRoot, e);
        }
        return out;
    }

    /**
     * 按名称 lookup 工作区；默认工作区返回 {@link #defaultWorkspace}；不存在返回 {@code null}。
     */
    public static Workspace get(Path agentDataDir, String name) {
        if (name == null || name.isBlank()) return null;
        if (name.equals(DEFAULT_WORKSPACE)) return defaultWorkspace(agentDataDir);
        return loadWorkspace(agentDataDir.resolve("workspaces").resolve(name));
    }

    /** 某工作区的会话存储目录（未知工作区回退默认）。 */
    public static Path sessionsDirFor(Path agentDataDir, String name) {
        Workspace ws = get(agentDataDir, name);
        return ws != null ? ws.sessionsDir() : defaultWorkspace(agentDataDir).sessionsDir();
    }

    /** 某工作区是否已登记（默认工作区恒 true）。 */
    public static boolean exists(Path agentDataDir, String name) {
        if (name == null || name.isBlank()) return false;
        if (name.equals(DEFAULT_WORKSPACE)) return true;
        return Files.isDirectory(agentDataDir.resolve("workspaces").resolve(name));
    }

    /**
     * 创建工作区：校验 name 合法唯一、dir 为存在的绝对目录，然后写
     * {@code workspaces/<name>/meta.json} + 建 {@code sessions/}。
     *
     * @return 成功 {@link CreateResult#ok}；失败带错误码（name_invalid / dir_not_found / dir_not_absolute /
     *     workspace_exists / workspace_create_failed）
     */
    public static CreateResult create(Path agentDataDir, String name, String dir) {
        String nameErr = validateName(name);
        if (nameErr != null) return CreateResult.err(nameErr);
        if (dir == null || dir.isBlank()) return CreateResult.err("dir_not_found");
        Path dirPath;
        try {
            dirPath = Paths.get(dir);
        } catch (Exception e) {
            return CreateResult.err("dir_not_found");
        }
        if (!dirPath.isAbsolute()) return CreateResult.err("dir_not_absolute");
        if (!Files.isDirectory(dirPath)) return CreateResult.err("dir_not_found");
        Path wsDir = agentDataDir.resolve("workspaces").resolve(name);
        if (Files.exists(wsDir)) return CreateResult.err("workspace_exists");
        try {
            Files.createDirectories(wsDir.resolve("sessions"));
            Files.writeString(
                    wsDir.resolve(META),
                    JSON.writeValueAsString(
                            Map.of(
                                    "name", name,
                                    "dir", dirPath.toString(),
                                    "created_at", System.currentTimeMillis())),
                    StandardCharsets.UTF_8);
            return CreateResult.ok(new Workspace(name, dirPath, wsDir.resolve("sessions")));
        } catch (IOException e) {
            log.warn("创建工作区失败: name={}", name, e);
            return CreateResult.err("workspace_create_failed");
        }
    }

    /** name 合法性校验：{@code [A-Za-z0-9._-]} ≤64，且非默认工作区名。非法返回错误码，合法返回 null。 */
    private static String validateName(String name) {
        if (name == null || name.isBlank()) return "name_invalid";
        if (name.length() > 64) return "name_invalid";
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z')
                    || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9')
                    || c == '-' || c == '_' || c == '.';
            if (!ok) return "name_invalid";
        }
        if (name.equals(DEFAULT_WORKSPACE)) return "workspace_exists";
        return null;
    }

    private static Workspace loadWorkspace(Path wsDir) {
        Path meta = wsDir.resolve(META);
        if (!Files.isRegularFile(meta)) return null;
        try {
            JsonNode n = JSON.readTree(Files.readString(meta, StandardCharsets.UTF_8));
            String name = n.path("name").asText(null);
            String dir = n.path("dir").asText(null);
            if (name == null || dir == null) return null;
            return new Workspace(name, Paths.get(dir), wsDir.resolve("sessions"));
        } catch (Exception e) {
            log.warn("读取工作区元数据失败: {}", wsDir, e);
            return null;
        }
    }
}
