package cn.findchar.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 码表（纯内存，无数据库）：
 * 1. GB18030-2022 覆盖表 —— 修正 JDK8 Charset(2005) 与 GB18030-2022 的 28+28 处差异，
 *    以及 GBK 的 0x80/欧元特例
 * 2. IBM-1388 (EBCDIC) 对照表 —— JDK8 无此字符集，用 ICU ibm-1388_P100-2024.ucm 加载到内存
 * 两份原始数据文件都在 src/main/resources/tables/ 下。
 */
@Component
public class CodeTables {

    private static final Logger log = LoggerFactory.getLogger(CodeTables.class);

    /** GB18030 编码覆盖：码点 -> 字节序列 */
    private final Map<Integer, byte[]> gb18030EncOverride = new HashMap<>();
    /** GB18030 解码覆盖：字节序列 hex(无分隔大写) -> 码点 */
    private final Map<String, Integer> gb18030DecOverride = new HashMap<>();

    /** GBK 覆盖（0x80 与欧元符互映射等） */
    private final Map<Integer, byte[]> gbkEncOverride = new HashMap<>();
    private final Map<String, Integer> gbkDecOverride = new HashMap<>();

    /** IBM-1388：码点 -> 字节序列 */
    private final Map<Integer, byte[]> ibmEnc = new ConcurrentHashMap<>();
    /** IBM-1388：字节序列 hex -> 码点 */
    private final Map<String, Integer> ibmDec = new ConcurrentHashMap<>();

    public CodeTables() {
        try {
            loadOverrides("/tables/gb18030-2022-overrides.tsv");
        } catch (IOException e) {
            throw new IllegalStateException("无法加载 GB18030 覆盖表", e);
        }
        try {
            loadIbm1388("/tables/ibm-1388.ucm");
        } catch (IOException e) {
            throw new IllegalStateException("无法加载 IBM-1388 对照表", e);
        }
    }

    private void loadOverrides(String path) throws IOException {
        int skipped = 0;
        try (BufferedReader r = reader(path)) {
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] parts = line.split("\t");
                if (parts.length < 3) { skipped++; continue; }
                try {
                    byte[] bytes;
                    int cp;
                    if (parts[0].endsWith("_DEC") || "DEC".equals(parts[0])) {
                        bytes = unhex(parts[1].trim());
                        cp = Integer.parseInt(parts[2].trim(), 16);
                    } else {
                        cp = Integer.parseInt(parts[1].trim(), 16);
                        bytes = unhex(parts[2].trim());
                    }
                    switch (parts[0]) {
                        case "ENC": gb18030EncOverride.put(cp, bytes); break;
                        case "DEC": gb18030DecOverride.put(key(bytes), cp); break;
                        case "GBK_ENC": gbkEncOverride.put(cp, bytes); break;
                        case "GBK_DEC": gbkDecOverride.put(key(bytes), cp); break;
                        default: skipped++; break;
                    }
                } catch (RuntimeException e) {
                    skipped++;
                }
            }
        }
        int total = gb18030EncOverride.size() + gb18030DecOverride.size() + gbkEncOverride.size() + gbkDecOverride.size();
        if (skipped > 0) {
            log.warn("GB18030-2022 覆盖表存在无法解析的行: {} 条（已跳过）", skipped);
        }
        log.info("GB18030-2022 覆盖表加载完成: {} 条（ENC {}, DEC {}, GBK {}）",
                total, gb18030EncOverride.size(), gb18030DecOverride.size(),
                gbkEncOverride.size() + gbkDecOverride.size());
    }

    /**
     * 解析 .ucm 映射表（IBM-1388）。
     * 精度代码：|0 = 双向精确；|1 = 正向回退（编码）；|2 = 替换符（跳过，视为不可编码）；
     * |4 = VPUA 虚拟映射（原站实测按可编码处理，如 U+E864 -> CE A6）；|3 = 反向回退（解码）。
     */
    private void loadIbm1388(String path) throws IOException {
        try (BufferedReader r = reader(path)) {
            String line;
            boolean inCharmap = false;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("CHARMAP")) { inCharmap = true; continue; }
                if (!inCharmap) continue;
                if (!line.startsWith("<U")) continue;
                String[] parts = line.split("\\s+");
                try {
                    int precision = Integer.parseInt(parts[parts.length - 1].substring(1));
                    if (precision == 2) continue; // subchar 替换，视为不可映射
                    int cp = Integer.parseInt(parts[0].substring(2, parts[0].length() - 1), 16);
                    byte[] bytes = unhex(parts[1].replace("\\x", " "));
                    ibmEnc.put(cp, bytes);
                    String k = key(bytes);
                    if (!ibmDec.containsKey(k)) ibmDec.put(k, cp);
                } catch (RuntimeException ignore) {
                    // 行格式异常则跳过
                }
            }
        }
        if (ibmEnc.isEmpty()) throw new IOException("IBM-1388 表为空");
        log.info("IBM-1388 对照表加载完成: {} 条", ibmEnc.size());
    }

    private BufferedReader reader(String path) throws IOException {
        InputStream in = new ClassPathResource(path).getInputStream();
        return new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
    }

    private static byte[] unhex(String s) {
        // 兼容两种写法：无分隔连写 "FE59"、以及带 \x 或空格分隔的("\\x59\\xBA" / "59 BA")
        String h = s.replace("\\x", "").replace(" ", "").trim();
        if (h.isEmpty() || h.length() % 2 != 0) throw new IllegalArgumentException("bad hex: " + s);
        byte[] b = new byte[h.length() / 2];
        for (int i = 0; i < b.length; i++) b[i] = (byte) Integer.parseInt(h.substring(i * 2, i * 2 + 2), 16);
        return b;
    }

    /** 字节序列 -> 无分隔大写 hex 串，作为 Map key */
    public static String key(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte x : bytes) sb.append(String.format("%02X", x & 0xFF));
        return sb.toString();
    }

    public byte[] gb18030EncodeOverride(int cp) { return gb18030EncOverride.get(cp); }
    public Integer gb18030DecodeOverride(byte[] bytes) { return gb18030DecOverride.get(key(bytes)); }

    public byte[] gbkEncodeOverride(int cp) { return gbkEncOverride.get(cp); }
    public Integer gbkDecodeOverride(byte[] bytes) { return gbkDecOverride.get(key(bytes)); }

    public byte[] ibm1388Encode(int cp) { return ibmEnc.get(cp); }
    public Integer ibm1388Decode(byte[] bytes) { return ibmDec.get(key(bytes)); }

    public int gb18030OverrideCount() { return gb18030EncOverride.size() + gb18030DecOverride.size(); }
    public int gbkOverrideCount() { return gbkEncOverride.size() + gbkDecOverride.size(); }
    public int ibm1388EntryCount() { return ibmEnc.size(); }
}
