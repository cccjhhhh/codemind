package com.codemind.impl.tool;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 文件排除规则管理器
 *
 * 解决问题：
 * - 工具（如 GrepTool）在遍历目录时错误地包含 .git、target 等目录
 * - 提供统一的排除规则管理，支持默认规则 + 项目自定义规则
 *
 * 最佳实践（参考 ripgrep、Claude Code）：
 * - 默认排除常见非源代码目录
 * - 支持读取 .gitignore 获取项目级排除规则
 * - 支持通过构造函数自定义额外排除规则
 *
 * 使用方式：
 * <pre>
 * ExcludeRules rules = new ExcludeRules();
 * rules.addGitignoreRules(projectDir);  // 可选：加载 .gitignore
 * rules.shouldExclude(path);  // 检查路径是否应排除
 * </pre>
 */
public class ExcludeRules {

    /**
     * 默认排除的目录名称（精确匹配）
     */
    private static final Set<String> DEFAULT_EXCLUDE_DIRS = Set.of(
        ".git",
        "target",
        "node_modules",
        ".svn",
        ".hg",
        "build",
        "dist",
        "out",
        ".gradle",
        ".idea",
        ".vscode",
        ".eclipse",
        ".settings"
    );

    /**
     * 默认排除的文件模式（正则表达式）
     */
    private static final Set<Pattern> DEFAULT_EXCLUDE_PATTERNS = Set.of(
        Pattern.compile(".*\\.class$"),       // Java 编译文件
        Pattern.compile(".*\\.jar$"),         // JAR 包
        Pattern.compile(".*\\.war$"),         // WAR 包
        Pattern.compile(".*\\.nar$"),         // NAR 包
        Pattern.compile(".*\\.ear$"),         // EAR 包
        Pattern.compile(".*\\.zip$"),         // ZIP 压缩包
        Pattern.compile(".*\\.tar$"),          // TAR 压缩包
        Pattern.compile(".*\\.gz$"),          // GZ 压缩包
        Pattern.compile(".*\\.log$"),         // 日志文件
        Pattern.compile("^\\.DS_Store$"),     // macOS 文件
        Pattern.compile("^Thumbs\\.db$"),     // Windows 文件
        Pattern.compile("^ehthumbs\\.db$"),  // Windows 文件
        Pattern.compile(".*\\.tmp$"),         // 临时文件
        Pattern.compile(".*\\.temp$"),        // 临时文件
        Pattern.compile(".*\\.bak$")          // 备份文件
    );

    /**
     * 目录名称集合（用于快速匹配）
     */
    private final Set<String> excludeDirNames = new HashSet<>();

    /**
     * 文件名模式集合
     */
    private final Set<Pattern> excludeFilePatterns = new HashSet<>();

    /**
     * 路径模式集合（用于匹配带路径的前缀）
     */
    private final Set<Pattern> excludePathPatterns = new HashSet<>();

    /**
     * 创建默认排除规则
     */
    public ExcludeRules() {
        // 添加默认排除目录
        excludeDirNames.addAll(DEFAULT_EXCLUDE_DIRS);

        // 添加默认文件模式
        excludeFilePatterns.addAll(DEFAULT_EXCLUDE_PATTERNS);

        // 添加默认路径模式（匹配目录本身或目录下的所有内容）
        excludePathPatterns.add(Pattern.compile("^\\.git(/.*)?$"));
        excludePathPatterns.add(Pattern.compile("^target(/.*)?$"));
        excludePathPatterns.add(Pattern.compile("^node_modules(/.*)?$"));
        excludePathPatterns.add(Pattern.compile("^build(/.*)?$"));
        excludePathPatterns.add(Pattern.compile("^dist(/.*)?$"));
        excludePathPatterns.add(Pattern.compile("^out(/.*)?$"));
        excludePathPatterns.add(Pattern.compile("^\\.gradle(/.*)?$"));
        excludePathPatterns.add(Pattern.compile("^\\.idea(/.*)?$"));
        excludePathPatterns.add(Pattern.compile("^\\.vscode(/.*)?$"));
        excludePathPatterns.add(Pattern.compile("^\\.sisyphus(/.*)?$"));
    }

    /**
     * 添加额外的排除规则
     *
     * @param pattern 正则表达式模式
     */
    public void addExcludePattern(String pattern) {
        excludePathPatterns.add(Pattern.compile(pattern));
    }

    /**
     * 添加额外的排除目录
     *
     * @param dirName 目录名称（不含路径）
     */
    public void addExcludeDir(String dirName) {
        excludeDirNames.add(dirName);
    }

    /**
     * 从 .gitignore 文件加载排除规则
     *
     * @param projectDir 项目根目录
     * @return 加载的规则数量
     */
    public int loadFromGitignore(Path projectDir) {
        Path gitignore = projectDir.resolve(".gitignore");
        if (!Files.exists(gitignore)) {
            return 0;
        }

        try {
            return (int) Files.lines(gitignore)
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .filter(line -> !line.startsWith("#"))
                .map(this::parseGitignoreLine)
                .filter(pattern -> pattern != null)
                .peek(excludePathPatterns::add)
                .count();
        } catch (IOException e) {
            return 0;
        }
    }

    /**
     * 解析 .gitignore 单行规则
     *
     * @param line .gitignore 中的单行
     * @return 对应的正则表达式模式，如果无效则返回 null
     */
    private Pattern parseGitignoreLine(String line) {
        try {
            // 转义正则特殊字符（除 * 和 ** 外）
            String regex = line
                .replaceAll("([\\\\\\[\\]{}()^$.|+?])", "\\\\$1")
                .replace("**", "<<<DOUBLESTAR>>>")
                .replace("*", "[^/]*")
                .replace("<<<DOUBLESTAR>>>", ".*");

            // 处理以 / 结尾的情况（只匹配目录）
            if (line.endsWith("/")) {
                regex = regex.substring(0, regex.length() - 1) + "(/.*)?";
            } else {
                // 匹配文件或目录
                regex = "^" + regex + "(/.*)?$";
            }

            return Pattern.compile(regex);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 检查路径是否应被排除
     *
     * @param path 要检查的路径
     * @return true 如果路径应被排除
     */
    public boolean shouldExclude(Path path) {
        if (path == null) {
            return true;
        }

        String pathStr = path.toString();
        String fileName = path.getFileName() != null ? path.getFileName().toString() : "";

        // 1. 检查是否是排除的目录名
        if (excludeDirNames.contains(fileName)) {
            return true;
        }

        // 2. 规范化路径分隔符并检查
        String normalizedPath = pathStr.replace("\\", "/");

        // 3. 检查路径模式
        for (Pattern pattern : excludePathPatterns) {
            if (pattern.matcher(normalizedPath).matches() || pattern.matcher(fileName).matches()) {
                return true;
            }
        }

        // 4. 检查文件名模式
        for (Pattern pattern : excludeFilePatterns) {
            if (pattern.matcher(fileName).matches()) {
                return true;
            }
        }

        return false;
    }

    /**
     * 检查路径是否应被排除（字符串版本）
     *
     * @param pathStr 要检查的路径字符串
     * @return true 如果路径应被排除
     */
    public boolean shouldExclude(String pathStr) {
        return shouldExclude(Path.of(pathStr));
    }

    /**
     * 获取排除的目录名称集合
     *
     * @return 不可修改的目录名称集合
     */
    public Set<String> getExcludeDirNames() {
        return java.util.Collections.unmodifiableSet(excludeDirNames);
    }

    /**
     * 获取默认排除的目录名称集合（静态方法）
     *
     * @return 不可修改的默认排除目录集合
     */
    public static Set<String> getDefaultExcludeDirs() {
        return DEFAULT_EXCLUDE_DIRS;
    }

    /**
     * 获取排除规则统计信息
     *
     * @return 格式化的统计字符串
     */
    public String getStats() {
        return String.format("ExcludeRules: %d dir names, %d path patterns, %d file patterns",
            excludeDirNames.size(), excludePathPatterns.size(), excludeFilePatterns.size());
    }
}
