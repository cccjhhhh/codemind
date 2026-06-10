package com.codemind.impl.tool;

import com.codemind.api.tool.Tool;
import com.codemind.api.tool.ToolResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 文件列举工具（支持 glob 模式匹配）
 *
 * 解决问题：
 * - 让 Agent 能够安全地列举项目文件，自动排除 .git、target 等目录
 * - 支持 glob 模式过滤（如 *java）
 * - 参考 Claude Code 的 glob 工具设计
 *
 * 使用方式：
 * <pre>
 * # 列举所有 Java 文件
 * glob pattern="**.java"
 *
 * # 列举特定目录下的文件
 * glob path="src/main/java" pattern="**.java"
 * </pre>
 *
 * 排除规则：
 * - 默认排除 .git、target、node_modules 等目录
 * - 可选读取 .gitignore 获取额外规则
 * - 支持自定义排除模式
 **/
public class GlobTool implements Tool {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int DEFAULT_MAX_RESULTS = 100;

    @Override
    public String getName() {
        return "Glob";
    }

    @Override
    public String getDescription() {
        return "列举匹配模式的文件，自动排除 .git、target 等目录。支持 glob 模式（**/*.java）。";
    }
    
    /**
     * 向后兼容：旧工具名
     */
    @Override
    public java.util.Optional<String> getDeprecatedName() {
        return java.util.Optional.of("glob");
    }

    @Override
    public JsonNode getInputSchema() {
        ObjectNode schema = JSON.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = schema.putObject("properties");

        ObjectNode patternProp = properties.putObject("pattern");
        patternProp.put("type", "string");
        patternProp.put("description", "glob 模式，如 **/*.java 或 *.md");

        ObjectNode pathProp = properties.putObject("path");
        pathProp.put("type", "string");
        pathProp.put("description", "搜索根目录（默认当前目录）");

        ObjectNode maxResultsProp = properties.putObject("max_results");
        maxResultsProp.put("type", "integer");
        maxResultsProp.put("description", "最大返回文件数（默认 100）");

        ObjectNode includeGitignoreProp = properties.putObject("include_gitignore");
        includeGitignoreProp.put("type", "boolean");
        includeGitignoreProp.put("description", "是否加载 .gitignore 排除规则（默认 true）");

        schema.putArray("required").add("pattern");

        return schema;
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        try {
            String patternStr = (String) params.get("pattern");
            String pathStr = (String) params.get("path");
            Integer maxResults = (Integer) params.get("max_results");
            Boolean includeGitignore = (Boolean) params.get("include_gitignore");

            if (patternStr == null || patternStr.isEmpty()) {
                return ToolResult.failure("参数 'pattern' 是必需的");
            }

            Path searchPath = pathStr != null ? Path.of(pathStr) : Path.of(".");
            int limit = maxResults != null ? maxResults : DEFAULT_MAX_RESULTS;
            boolean loadGitignore = includeGitignore == null || includeGitignore;

            // 初始化排除规则
            ExcludeRules excludeRules = new ExcludeRules();
            if (loadGitignore) {
                excludeRules.loadFromGitignore(searchPath);
            }

            // 将 glob 模式转换为 Predicate
            Predicate<Path> patternMatcher = createGlobMatcher(patternStr);

            // 遍历文件
            List<String> matchedFiles = findFiles(searchPath, patternMatcher, excludeRules, limit);

            if (matchedFiles.isEmpty()) {
                return ToolResult.success("未找到匹配文件: " + patternStr);
            }

            // 格式化输出
            StringBuilder result = new StringBuilder();
            result.append("找到 ").append(matchedFiles.size()).append(" 个文件:\n\n");

            // 按目录分组显示
            Map<String, List<String>> groupedFiles = matchedFiles.stream()
                .collect(Collectors.groupingBy(this::getDirectory));

            groupedFiles.forEach((dir, files) -> {
                result.append(dir).append("/\n");
                files.forEach(file -> result.append("  ").append(getFileName(file)).append("\n"));
                result.append("\n");
            });

            return ToolResult.success(result.toString());

        } catch (Exception e) {
            return ToolResult.failure("文件列举失败: " + e.getMessage());
        }
    }

    /**
     * 创建 glob 模式匹配器
     */
    private Predicate<Path> createGlobMatcher(String globPattern) {
        // 处理常见 glob 模式
        String regex = globPattern
            .replace(".", "\\.")           // 转义点号
            .replace("**", "<<<DOUBLESTAR>>>")  // 临时标记
            .replace("*", "[^/]*")         // 单星号匹配非路径分隔符
            .replace("<<<DOUBLESTAR>>>",".*"); // 双星号匹配任意路径

        // 确保匹配完整路径
        if (!regex.startsWith("^")) {
            regex = "^" + regex;
        }
        if (!regex.endsWith("$")) {
            regex = regex + "$";
        }

        Pattern pattern = Pattern.compile(regex);
        return path -> {
            String pathStr = path.toString().replace("\\", "/");
            return pattern.matcher(pathStr).find();
        };
    }

    /**
     * 查找匹配的文件
     */
    private List<String> findFiles(Path root, Predicate<Path> patternMatcher, ExcludeRules excludeRules, int limit) {
        List<String> results = new ArrayList<>();

        try (Stream<Path> stream = Files.walk(root)) {
            stream
                .filter(Files::isRegularFile)
                .filter(path -> !excludeRules.shouldExclude(path))
                .filter(path -> !isInExcludedDir(path, excludeRules))
                .filter(patternMatcher)
                .limit(limit)
                .map(path -> root.relativize(path).toString().replace("\\", "/"))
                .forEach(results::add);
        } catch (IOException e) {
            // 忽略遍历错误
        }

        return results;
    }

    /**
     * 检查文件是否在排除的目录内
     */
    private boolean isInExcludedDir(Path file, ExcludeRules excludeRules) {
        Path absFile = file.toAbsolutePath().normalize();
        Path root = file.getRoot();

        for (Path parent = absFile.getParent(); parent != null && !parent.equals(root); parent = parent.getParent()) {
            if (excludeRules.shouldExclude(parent)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取文件所在目录
     */
    private String getDirectory(String filePath) {
        int lastSlash = filePath.lastIndexOf('/');
        return lastSlash > 0 ? filePath.substring(0, lastSlash) : ".";
    }

    /**
     * 获取文件名
     */
    private String getFileName(String filePath) {
        int lastSlash = filePath.lastIndexOf('/');
        return lastSlash > 0 ? filePath.substring(lastSlash + 1) : filePath;
    }
}
