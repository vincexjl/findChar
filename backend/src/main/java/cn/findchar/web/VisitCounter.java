package cn.findchar.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 使用计数（无数据库）：落在程序运行目录 data/uchar-count.txt。
 * 原站页面上有 "本页面的功能已经被使用 N 次" 与页脚计数，此处用纯文件持久化同等体验。
 */
@Component
public class VisitCounter {

    private static final Logger log = LoggerFactory.getLogger(VisitCounter.class);

    private static final Path FILE = Paths.get("data", "uchar-count.txt");
    private final AtomicLong value = new AtomicLong(0);

    @PostConstruct
    public void load() {
        try {
            if (Files.exists(FILE)) {
                String s = new String(Files.readAllBytes(FILE), StandardCharsets.UTF_8).trim();
                if (!s.isEmpty()) value.set(Long.parseLong(s));
            }
            log.info("使用计数已加载: {}", value.get());
        } catch (Exception e) {
            log.warn("使用计数文件读取失败，从 0 开始: {}", FILE.toAbsolutePath(), e);
        }
    }

    public void increment() {
        long v = value.incrementAndGet();
        save(v);
    }

    public long get() {
        return value.get();
    }

    private synchronized void save(long v) {
        try {
            Files.createDirectories(FILE.getParent());
            Files.write(FILE, String.valueOf(v).getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            log.warn("使用计数写入失败（当前值仅保留在内存中）: {}", v, e);
        }
    }
}
