package cn.findchar.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.StringJoiner;

/**
 * 可选的生僻字字体支持（与原站 /media/css/atom_chars 的思路一致）：
 * 如果部署目录下存在 fonts/uXXXX.ttf（按 Unicode 码位命名的单字字体文件），
 * 返回对应的 @font-face CSS；没有文件则返回空 CSS，由浏览器本机字库兜底。
 * 完全可选，不影响查字功能。
 */
@RestController
@RequestMapping("/api/uchar")
public class FontCssController {

    private static final Path FONTS_DIR = Paths.get("fonts");

    @GetMapping(value = "/fontcss", produces = "text/css;charset=UTF-8")
    public String fontCss(@RequestParam(name = "cps", defaultValue = "") String cps) {
        StringJoiner css = new StringJoiner("\n");
        if (cps != null && !cps.isEmpty()) {
            for (String token : cps.split("[,\"'\\s]+")) {
                if (token.isEmpty()) continue;
                String cp = token.toUpperCase().replace("U+", "").replaceAll("[^0-9A-F]", "");
                if (cp.isEmpty() || cp.length() > 6) continue;
                cp = String.format("%04X", Integer.parseInt(cp, 0x10) & 0x10FFFF);
                String sub = cp.substring(0, Math.min(3, cp.length()));
                for (String ext : new String[]{"ttf", "otf"}) {
                    Path p = FONTS_DIR.resolve("u" + cp + "." + ext);
                    if (Files.exists(p)) {
                        css.add("@font-face {");
                        css.add("  font-family: \"ac" + cp + "\";");
                        css.add("  src: url(\"/api/uchar/font/u" + cp + "." + ext + "\") format(\"" + (ext.equals("ttf") ? "truetype" : "opentype") + "\");");
                        css.add("  font-weight: normal; font-style: normal;");
                        css.add("}");
                        break;
                    }
                }
            }
        }
        return css.toString();
    }

    /** 直接输出字体文件（仅允许 fonts 目录下的 uXXXX.ttf/otf，防止路径穿越） */
    @GetMapping("/font/{name}")
    public org.springframework.http.ResponseEntity<byte[]> font(@PathVariable String name) {
        if (!name.matches("u[0-9A-F]{4,6}\\.(ttf|otf)")) {
            return org.springframework.http.ResponseEntity.notFound().build();
        }
        Path p = FONTS_DIR.resolve(name);
        if (!Files.exists(p)) return org.springframework.http.ResponseEntity.notFound().build();
        try {
            byte[] bytes = Files.readAllBytes(p);
            return org.springframework.http.ResponseEntity.ok()
                    .header("Content-Type", name.endsWith(".ttf") ? "font/ttf" : "font/otf")
                    .header("Cache-Control", "max-age=86400")
                    .body(bytes);
        } catch (IOException e) {
            return org.springframework.http.ResponseEntity.notFound().build();
        }
    }
}
