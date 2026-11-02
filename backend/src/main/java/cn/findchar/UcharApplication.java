package cn.findchar;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.File;

@SpringBootApplication
public class UcharApplication {

    private static final String LOG_DIR_PROP = "APP_LOG_DIR";

    public static void main(String[] args) {
        // 在日志系统初始化前，把日志目录固定到 jar 包同级目录的 appLogs 下；
        // IDE 内直接运行（非 jar）时退回为工作目录相对路径 appLogs
        initLogDir();
        SpringApplication.run(UcharApplication.class, args);
    }

    private static void initLogDir() {
        try {
            File source = new File(UcharApplication.class
                    .getProtectionDomain().getCodeSource().getLocation().toURI());
            if (source.isFile() && source.getName().toLowerCase().endsWith(".jar")) {
                File dir = new File(source.getParentFile(), "appLogs");
                System.setProperty(LOG_DIR_PROP, dir.getAbsolutePath());
            }
        } catch (Exception ignore) {
            // 取不到 jar 位置时，log4j2.xml 使用默认值（工作目录下的 appLogs）
        }
    }
}
