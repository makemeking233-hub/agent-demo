package com.example.agent.web.api;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * picker 异步任务表（picker-async）。
 *
 * <p>每个 pick-folder 任务：
 * <ul>
 *   <li>submit 时启动 process + 返回 task_id
 *   <li>后台 thread 读 stdout → 写 outputFile（避免阻塞 reader）
 *   <li>process 退出 → future 写入 path
 *   <li>1 分钟未访问自动清理（避免内存泄漏）
 * </ul>
 */
@Component
public class PickerTaskStore {

    private static final Logger log = LoggerFactory.getLogger(PickerTaskStore.class);
    private static final long CLEANUP_AFTER_MINUTES = 1;

    private final Map<String, Task> tasks = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleaner = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "picker-task-cleaner");
        t.setDaemon(true);
        return t;
    });

    public record Task(
            String id,
            Process process,
            Path outputFile,
            CompletableFuture<String> future,
            Instant createdAt) {}

    /**
     * 启动一个 process + 注册到任务表。
     *
     * @param starter 接受 (outputFile, readyCallback) 启动 process；readyCallback 在 process 启动后立刻调用
     * @return 新建任务（含 task_id）
     */
    public Task submit(BiConsumer<Path, Runnable> starter) throws IOException {
        String id = UUID.randomUUID().toString();
        Path outputFile = Files.createTempFile("picker-", "-" + id + ".out");
        CompletableFuture<String> future = new CompletableFuture<>();
        Task task = new Task(id, null, outputFile, future, Instant.now());

        // 启动 process；启动失败立即 fail
        try {
            starter.accept(outputFile, () -> {});
        } catch (Exception e) {
            Files.deleteIfExists(outputFile);
            future.completeExceptionally(e);
            throw new IOException("process 启动失败: " + e.getMessage(), e);
        }

        tasks.put(id, task);
        // 1 分钟后清理
        cleaner.schedule(() -> cleanup(id), CLEANUP_AFTER_MINUTES, TimeUnit.MINUTES);
        return task;
    }

    /**
     * 启动 process 并把 process 注入到 task。
     */
    public Task submitWithProcess(java.util.function.Function<Path, Process> starter) throws IOException {
        String id = UUID.randomUUID().toString();
        Path outputFile = Files.createTempFile("picker-", "-" + id + ".out");
        CompletableFuture<String> future = new CompletableFuture<>();

        Process process;
        try {
            process = starter.apply(outputFile);
        } catch (Exception e) {
            Files.deleteIfExists(outputFile);
            future.completeExceptionally(e);
            throw new IOException("process 启动失败: " + e.getMessage(), e);
        }

        Task task = new Task(id, process, outputFile, future, Instant.now());
        tasks.put(id, task);

        // 后台 thread：等 process 退出 → 读 stdout → complete future
        Thread waiter = new Thread(() -> waitAndComplete(task), "picker-wait-" + id);
        waiter.setDaemon(true);
        waiter.start();

        cleaner.schedule(() -> cleanup(id), CLEANUP_AFTER_MINUTES, TimeUnit.MINUTES);
        return task;
    }

    private void waitAndComplete(Task task) {
        Process p = task.process();
        try {
            boolean finished = p.waitFor(5, TimeUnit.MINUTES);
            if (!finished) {
                p.destroyForcibly();
                Files.deleteIfExists(task.outputFile());
                task.future().completeExceptionally(new IOException("操作超时（5 分钟）"));
                return;
            }
            int code = p.exitValue();
            if (code != 0) {
                task.future().complete(null); // cancelled
                return;
            }
            String out = Files.readString(task.outputFile()).trim();
            if (out.isEmpty()) {
                task.future().complete(null);
                return;
            }
            Path path = Paths.get(out);
            if (!path.isAbsolute() || !Files.exists(path) || !Files.isDirectory(path)) {
                task.future().completeExceptionally(new IOException("选定路径无效: " + out));
                return;
            }
            task.future().complete(path.toAbsolutePath().toString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            task.future().completeExceptionally(e);
        } catch (IOException e) {
            log.warn("[picker] 任务 {} IO 异常: {}", task.id(), e.getMessage());
            task.future().completeExceptionally(e);
        } finally {
            try {
                Files.deleteIfExists(task.outputFile());
            } catch (IOException ignored) {
                /* ignore */
            }
        }
    }

    public Optional<Task> get(String id) {
        Task t = tasks.get(id);
        if (t == null) return Optional.empty();
        // 访问即续期
        cleaner.schedule(() -> cleanup(id), CLEANUP_AFTER_MINUTES, TimeUnit.MINUTES);
        return Optional.of(t);
    }

    /** 中止任务：destroy process + 标 cancelled */
    public boolean cancel(String id) {
        Task t = tasks.remove(id);
        if (t == null) return false;
        if (t.process() != null && t.process().isAlive()) {
            t.process().destroyForcibly();
        }
        if (!t.future().isDone()) {
            t.future().cancel(true);
        }
        try {
            Files.deleteIfExists(t.outputFile());
        } catch (IOException ignored) {
            /* ignore */
        }
        return true;
    }

    private void cleanup(String id) {
        Task t = tasks.remove(id);
        if (t == null) return;
        // process 应该已经退出；但保险起见 destroy
        if (t.process() != null && t.process().isAlive()) {
            t.process().destroyForcibly();
        }
        try {
            Files.deleteIfExists(t.outputFile());
        } catch (IOException ignored) {
            /* ignore */
        }
    }

    public int size() {
        return tasks.size();
    }

    /** 测试钩子 */
    void clearForTest() {
        for (String id : List.copyOf(tasks.keySet())) cleanup(id);
    }
}
