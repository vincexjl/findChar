package cn.findchar.web;

import cn.findchar.service.CharEncodingService;
import cn.findchar.service.CodeTables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/uchar")
public class UcharController {

    private static final Logger log = LoggerFactory.getLogger(UcharController.class);

    private final CharEncodingService service;
    private final CodeTables tables;
    private final VisitCounter counter;

    public UcharController(CharEncodingService service, CodeTables tables, VisitCounter counter) {
        this.service = service;
        this.tables = tables;
        this.counter = counter;
    }

    public static class QueryRequest {
        public String input;
        public String searchType;
    }

    @PostMapping("/query")
    public CharEncodingService.QueryResult query(@RequestBody QueryRequest req) {
        String input = req == null ? "" : req.input;
        String searchType = req == null ? "char" : req.searchType;
        if (input.length() > 64) input = input.substring(0, 64);
        counter.increment();
        CharEncodingService.QueryResult r = service.query(input, searchType);
        log.info("query input=[{}] searchType=[{}] -> found={} cp={} uni={} gb18030=[{}] gbk=[{}] ibm1388=[{}]",
                input, searchType, r.found, r.codePoint, r.unicodeHex, r.gb18030, r.gbk, r.ibm1388);
        return r;
    }

    @GetMapping("/count")
    public Map<String, Object> count() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("count", counter.get());
        return m;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", "UP");
        m.put("gb18030Overrides", tables.gb18030OverrideCount());
        m.put("gbkOverrides", tables.gbkOverrideCount());
        m.put("ibm1388Entries", tables.ibm1388EntryCount());
        return m;
    }

    /** 服务就绪后的启动信息（写入 appLogs） */
    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        log.info("编码查字服务启动完成: GB18030-2022 覆盖 {} 条, GBK 覆盖 {} 条, IBM-1388 表 {} 条, 已用计数 {} 次",
                tables.gb18030OverrideCount(), tables.gbkOverrideCount(),
                tables.ibm1388EntryCount(), counter.get());
    }
}
