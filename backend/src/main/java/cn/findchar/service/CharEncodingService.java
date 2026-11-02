package cn.findchar.service;

import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;

/**
 * 核心查字服务。
 *
 * 行为与原站 name.vurls.cn 编码查字页逐点对齐（含 GB18030-2022 覆盖表、伪码规则、
 * 不可编码字符返回伪码 [U+XXXX]、无效输入返回全空等，均经原站实测校准）。
 * 纯内存查表 + JDK8 自带字符集，无数据库。
 */
@Service
public class CharEncodingService {

    static final Charset GB18030 = Charset.forName("GB18030");
    static final Charset GBK = Charset.forName("GBK");

    private final CodeTables tables;

    public CharEncodingService(CodeTables tables) {
        this.tables = tables;
    }

    /** 查询结果 */
    public static class QueryResult {
        public boolean found;
        /** 大字显示用 */
        public String ch = "";
        public int codePoint = -1;
        public String unicodeHex = "";
        public String utf8 = "";
        public String gb18030 = "";
        public String utf16le = "";
        public String utf16be = "";
        public String utf32le = "";
        public String utf32be = "";
        public String gbk = "";
        public String ibm1388 = "";
    }

    /** 允许的查询方式（与前端单选框 value 完全一致） */
    private static boolean validType(String t) {
        return "char".equals(t) || "Unicode".equals(t) || "utf_16_be".equals(t)
                || "UTF-8".equals(t) || "GB18030".equals(t) || "GBK".equals(t) || "IBM1388".equals(t);
    }

    public QueryResult query(String input, String searchType) {
        QueryResult result = new QueryResult();
        if (input == null) input = "";
        if (input.length() > 64) input = input.substring(0, 64);
        if (!validType(searchType)) return result;

        Integer cp = resolveCodePoint(input, searchType);
        if (cp == null || !isScalar(cp)) return result;

        result.found = true;
        result.codePoint = cp;
        result.ch = new String(Character.toChars(cp));
        result.unicodeHex = unicodeHex(cp);
        String ch = new String(Character.toChars(cp));
        result.utf8 = hexSpaced(utf8Bytes(cp));
        result.utf16le = hexSpaced(encodeStrict(ch, Charset.forName("UTF-16LE")));
        result.utf16be = hexSpaced(encodeStrict(ch, Charset.forName("UTF-16BE")));
        result.utf32le = hexSpaced(new byte[]{(byte) (cp & 0xFF), (byte) ((cp >> 8) & 0xFF), (byte) ((cp >> 16) & 0xFF), (byte) ((cp >> 24) & 0xFF)});
        result.utf32be = hexSpaced(new byte[]{(byte) ((cp >> 24) & 0xFF), (byte) ((cp >> 16) & 0xFF), (byte) ((cp >> 8) & 0xFF), (byte) (cp & 0xFF)});
        result.gb18030 = nullToPseudo(cp, encGb18030(cp));
        result.gbk = nullToPseudo(cp, encGbk(cp));
        result.ibm1388 = nullToPseudo(cp, tables.ibm1388Encode(cp) != null && tables.ibm1388Encode(cp).length > 0
                ? tables.ibm1388Encode(cp) : null);
        return result;
    }

    private static String nullToPseudo(int cp, byte[] bytes) {
        if (bytes == null) return "[U+" + unicodeHex(cp) + "]";
        return hexSpaced(bytes);
    }

    /** 根据输入与查询方向解析出码点；解析失败返回 null（页面上显示为空） */
    private Integer resolveCodePoint(String input, String searchType) {
        switch (searchType) {
            case "char": {
                String text = input.trim();
                if (text.isEmpty()) return null;
                return text.codePointAt(0);
            }
            case "Unicode": {
                String h = input.replaceAll("\\s+", "");
                if (!h.matches("[0-9a-fA-F]{1,6}")) return null;
                int cp;
                try { cp = Integer.parseInt(h, 16); } catch (NumberFormatException e) { return null; }
                return isScalar(cp) ? cp : null;
            }
            default: {
                byte[] bytes = parseHexBytes(input);
                if (bytes == null || bytes.length == 0) return null;
                return decodeFirstCodePoint(bytes, searchType);
            }
        }
    }

    /** 字符串十六进制 -> 字节：忽略所有空白，必须是偶数位 hex；失败返回 null */
    private static byte[] parseHexBytes(String input) {
        String h = input.replaceAll("\\s+", "");
        if (h.isEmpty() || h.length() > 32 || h.length() % 2 != 0) return null;
        if (!h.matches("[0-9a-fA-F]+")) return null;
        byte[] b = new byte[h.length() / 2];
        for (int i = 0; i < b.length; i++) b[i] = (byte) Integer.parseInt(h.substring(i * 2, i * 2 + 2), 16);
        return b;
    }

    /** 按 7 种编码之一解码，取第一个码点；无法解码返回 null */
    private Integer decodeFirstCodePoint(byte[] bytes, String searchType) {
        int cp;
        switch (searchType) {
            case "utf_16_be":
                cp = firstCodePoint(decodeStrict(bytes, Charset.forName("UTF-16BE")));
                break;
            case "UTF-8":
                cp = firstCodePoint(decodeStrict(bytes, Charset.forName("UTF-8")));
                break;
            case "GB18030":
                cp = decGb18030(bytes);
                break;
            case "GBK":
                cp = decGbk(bytes);
                break;
            case "IBM1388": {
                Integer r = tables.ibm1388Decode(bytes);
                cp = r == null ? -1 : r;
                break;
            }
            default:
                return null;
        }
        return cp > 0 ? cp : null;
    }

    /** GB18030 解码：先查 GB18030-2022 覆盖字节序列，再走 JDK8 Charset（严格模式） */
    private int decGb18030(byte[] bytes) {
        Integer override = tables.gb18030DecodeOverride(bytes);
        if (override != null) return override;
        int cp = firstCodePoint(decodeStrict(bytes, GB18030));
        return cp;
    }

    /** GBK 解码：先查覆盖表（0x80 -> U+20AC），再走 JDK8 Charset（严格模式） */
    private int decGbk(byte[] bytes) {
        Integer override = tables.gbkDecodeOverride(bytes);
        if (override != null) return override;
        return firstCodePoint(decodeStrict(bytes, GBK));
    }

    /** GB18030 编码：先查 GB18030-2022 覆盖码点，再走 JDK8 Charset（严格模式） */
    public byte[] encGb18030(int cp) {
        byte[] override = tables.gb18030EncodeOverride(cp);
        if (override != null) return override;
        return encodeStrict(new String(Character.toChars(cp)), GB18030);
    }

    /** GBK 编码：先查覆盖表（欧元 -> 0x80），再走 JDK8 Charset（严格模式） */
    public byte[] encGbk(int cp) {
        byte[] override = tables.gbkEncodeOverride(cp);
        if (override != null) return override;
        return encodeStrict(new String(Character.toChars(cp)), GBK);
    }

    /* ---------- 工具 ---------- */

    private static boolean isScalar(int cp) {
        return cp >= 0 && cp <= 0x10FFFF && !(cp >= 0xD800 && cp <= 0xDFFF);
    }

    /** 大写 hex，不足 4 位补零（U+20BB7 / U+4E00） */
    public static String unicodeHex(int cp) {
        return String.format("%04X", cp).toUpperCase();
    }

    private static byte[] utf8Bytes(int cp) {
        return encodeStrict(new String(Character.toChars(cp)), Charset.forName("UTF-8"));
    }

    private static String hexSpaced(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte x : bytes) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(String.format("%02X", x & 0xFF));
        }
        return sb.toString();
    }

    /** 严格解码，失败返回 null */
    private static String decodeStrict(byte[] bytes, Charset cs) {
        try {
            CharsetDecoder d = cs.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
            CharBuffer cb = d.decode(ByteBuffer.wrap(bytes));
            return cb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    /** 严格编码，失败返回 null */
    private static byte[] encodeStrict(String s, Charset cs) {
        try {
            CharsetEncoder e = cs.newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
            ByteBuffer bb = e.encode(CharBuffer.wrap(s));
            byte[] b = new byte[bb.remaining()];
            bb.get(b);
            return b;
        } catch (Exception e2) {
            return null;
        }
    }

    /** 字符串里第一个码点 */
    private static int firstCodePoint(String s) {
        if (s == null || s.isEmpty()) return -1;
        return s.codePointAt(0);
    }

    /* ---------- 表项规模（供健康检查/日志） ---------- */
}
