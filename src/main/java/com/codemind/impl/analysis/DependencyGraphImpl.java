package com.codemind.impl.analysis;

import com.codemind.api.analysis.DependencyGraph;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 依赖图实现
 * 
 * 基于 import 语句解析，构建文件间依赖关系图。
 * 支持 BFS 遍历查找受影响文件。
 * 
 * 学习要点：
 * - 图的邻接表表示
 * - BFS 遍历算法
 * - import 语句正则解析
 */
public class DependencyGraphImpl implements DependencyGraph {
    
    // Java import 正则: import xxx.xxx.Xxx;
    private static final Pattern JAVA_IMPORT_PATTERN = 
        Pattern.compile("^import\\s+([\\w.]+);?\\s*$", Pattern.MULTILINE);
    
    // 文件扩展名
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(".java", ".py", ".ts", ".js");
    
    // 依赖图: 文件 -> 该文件依赖的文件集合
    private final Map<String, Set<String>> dependencies = new HashMap<>();
    
    // 反向索引: 文件 -> 依赖该文件的文件集合
    private final Map<String, Set<String>> dependents = new HashMap<>();
    
    // 已构建标志
    private boolean built = false;
    
    // 仓库根目录
    private Path repoRoot;
    
    @Override
    public void build(Path repoRoot) {
        this.repoRoot = repoRoot;
        dependencies.clear();
        dependents.clear();
        
        // 1. 遍历所有支持的代码文件
        List<Path> codeFiles = collectCodeFiles(repoRoot);
        
        // 2. 解析每个文件的 import 语句
        for (Path file : codeFiles) {
            String filePath = normalizePath(file);
            Set<String> imports = parseImports(file);
            
            dependencies.put(filePath, imports);
            
            // 构建反向索引
            for (String imported : imports) {
                dependents.computeIfAbsent(imported, k -> new HashSet<>()).add(filePath);
            }
        }
        
        built = true;
    }
    
    @Override
    public Set<String> findAffectedFiles(Set<String> changedFiles, int maxHops) {
        if (!built) {
            throw new IllegalStateException("依赖图尚未构建，请先调用 build()");
        }
        
        Set<String> affected = new HashSet<>();
        Queue<String> queue = new LinkedList<>();
        
        // 初始化队列和已访问集合
        Set<String> visited = new HashSet<>();
        for (String file : changedFiles) {
            String normalized = normalizePathString(file);
            if (!visited.contains(normalized)) {
                visited.add(normalized);
                queue.add(normalized);
                affected.add(normalized);
            }
        }
        
        // BFS 遍历
        int currentHop = 0;
        while (!queue.isEmpty() && currentHop < maxHops) {
            int levelSize = queue.size();
            
            for (int i = 0; i < levelSize; i++) {
                String current = queue.poll();
                
                // 查找依赖该文件的所有文件（即反向依赖）
                Set<String> deps = getDependentsInternal(current);
                
                for (String dependent : deps) {
                    if (!visited.contains(dependent)) {
                        visited.add(dependent);
                        queue.add(dependent);
                        affected.add(dependent);
                    }
                }
            }
            
            currentHop++;
        }
        
        return affected;
    }
    
    @Override
    public Set<String> getDependencies(String filePath) {
        if (!built) {
            throw new IllegalStateException("依赖图尚未构建");
        }
        String normalized = normalizePathString(filePath);
        return new HashSet<>(dependencies.getOrDefault(normalized, Collections.emptySet()));
    }
    
    @Override
    public Set<String> getDependents(String filePath) {
        if (!built) {
            throw new IllegalStateException("依赖图尚未构建");
        }
        return new HashSet<>(getDependentsInternal(normalizePathString(filePath)));
    }
    
    /**
     * 内部方法：不进行路径规范化
     */
    private Set<String> getDependentsInternal(String filePath) {
        // 先尝试精确匹配
        Set<String> result = dependents.getOrDefault(filePath, Collections.emptySet());
        
        // 如果没有，尝试模糊匹配（处理相对路径情况）
        if (result.isEmpty()) {
            String simpleName = getSimpleName(filePath);
            for (String key : dependents.keySet()) {
                if (key.endsWith(simpleName) || simpleName.endsWith(getSimpleName(key))) {
                    result = dependents.get(key);
                    break;
                }
            }
        }
        
        return result;
    }
    
    @Override
    public boolean hasCircularDependency(Set<String> filePaths) {
        if (!built) {
            throw new IllegalStateException("依赖图尚未构建");
        }
        
        // DFS 检测循环
        Set<String> visited = new HashSet<>();
        Set<String> recursionStack = new HashSet<>();
        
        for (String file : filePaths) {
            String normalized = normalizePathString(file);
            if (hasCycleDFS(normalized, visited, recursionStack, filePaths)) {
                return true;
            }
        }
        
        return false;
    }
    
    private boolean hasCycleDFS(String node, Set<String> visited, 
                                 Set<String> recursionStack, Set<String> filePaths) {
        visited.add(node);
        recursionStack.add(node);
        
        Set<String> deps = dependencies.getOrDefault(node, Collections.emptySet());
        for (String dep : deps) {
            if (!filePaths.contains(dep)) {
                continue; // 只检查指定的文件集合
            }
            
            if (!visited.contains(dep)) {
                if (hasCycleDFS(dep, visited, recursionStack, filePaths)) {
                    return true;
                }
            } else if (recursionStack.contains(dep)) {
                return true; // 发现循环
            }
        }
        
        recursionStack.remove(node);
        return false;
    }
    
    @Override
    public Set<String> getCoreModules(int threshold) {
        if (!built) {
            throw new IllegalStateException("依赖图尚未构建");
        }
        
        Set<String> coreModules = new HashSet<>();
        
        for (Map.Entry<String, Set<String>> entry : dependents.entrySet()) {
            if (entry.getValue().size() >= threshold) {
                coreModules.add(entry.getKey());
            }
        }
        
        return coreModules;
    }
    
    @Override
    public double calculateRiskScore(String filePath) {
        if (!built) {
            throw new IllegalStateException("依赖图尚未构建");
        }
        
        String normalized = normalizePathString(filePath);
        double score = 0.0;
        
        // 1. 是否为核心模块（被依赖次数多）
        int dependentCount = getDependentsInternal(normalized).size();
        if (dependentCount >= 5) {
            score += 0.3;
        } else if (dependentCount >= 3) {
            score += 0.2;
        } else if (dependentCount >= 1) {
            score += 0.1;
        }
        
        // 2. 依赖其他文件的数量（出度）
        int dependencyCount = dependencies.getOrDefault(normalized, Collections.emptySet()).size();
        if (dependencyCount > 10) {
            score += 0.2; // 依赖多说明是高层模块
        } else if (dependencyCount > 5) {
            score += 0.1;
        }
        
        // 3. 安全关键词检测
        String lowerPath = normalized.toLowerCase();
        if (lowerPath.contains("security") || 
            lowerPath.contains("auth") ||
            lowerPath.contains("password") ||
            lowerPath.contains("crypto")) {
            score += 0.3;
        }
        
        // 4. 测试文件风险低
        if (lowerPath.contains("test") || lowerPath.contains("mock")) {
            score -= 0.2;
        }
        
        return Math.max(0.0, Math.min(1.0, score));
    }
    
    @Override
    public void clear() {
        dependencies.clear();
        dependents.clear();
        built = false;
        repoRoot = null;
    }
    
    @Override
    public String getStats() {
        if (!built) {
            return "DependencyGraph: not built";
        }
        
        int totalFiles = dependencies.size();
        int totalEdges = dependencies.values().stream().mapToInt(Set::size).sum();
        int maxDependents = dependents.values().stream()
            .mapToInt(Set::size)
            .max().orElse(0);
        
        return String.format(
            "DependencyGraph: %d files, %d dependencies, max dependents: %d",
            totalFiles, totalEdges, maxDependents
        );
    }
    
    // ========== 辅助方法 ==========
    
    /**
     * 收集目录下所有支持的代码文件
     */
    private List<Path> collectCodeFiles(Path root) {
        List<Path> files = new ArrayList<>();
        
        try {
            Files.walk(root)
                .filter(Files::isRegularFile)
                .filter(path -> {
                    String ext = getExtension(path);
                    return SUPPORTED_EXTENSIONS.contains(ext.toLowerCase());
                })
                .filter(path -> !isIgnored(path)) // 忽略测试和生成文件
                .forEach(files::add);
        } catch (IOException e) {
            System.err.println("遍历目录失败: " + e.getMessage());
        }
        
        return files;
    }
    
    /**
     * 解析文件的 import 语句
     */
    private Set<String> parseImports(Path file) {
        Set<String> imports = new HashSet<>();
        
        try {
            String content = Files.readString(file);
            Matcher matcher = JAVA_IMPORT_PATTERN.matcher(content);
            
            while (matcher.find()) {
                String importedClass = matcher.group(1);
                // 转换为文件路径
                String importedPath = classToPath(importedClass);
                imports.add(importedPath);
            }
        } catch (IOException e) {
            // 忽略无法读取的文件
        }
        
        return imports;
    }
    
    /**
     * 将类名转换为可能的文件路径
     */
    private String classToPath(String className) {
        // com.example.Foo -> com/example/Foo.java
        return className.replace('.', '/') + ".java";
    }
    
    /**
     * 获取文件扩展名
     */
    private String getExtension(Path path) {
        String name = path.getFileName().toString();
        int dotIndex = name.lastIndexOf('.');
        return dotIndex > 0 ? name.substring(dotIndex) : "";
    }
    
    /**
     * 检查是否应忽略文件
     */
    private boolean isIgnored(Path path) {
        String pathStr = path.toString().replace('\\', '/');
        
        // 忽略目录
        if (pathStr.contains("/test/") || 
            pathStr.contains("/tests/") ||
            pathStr.contains("/target/") ||
            pathStr.contains("/build/") ||
            pathStr.contains("/node_modules/") ||
            pathStr.contains("/.git/")) {
            return true;
        }
        
        // 忽略文件名
        String fileName = path.getFileName().toString();
        if (fileName.startsWith(".") || 
            fileName.endsWith("Test.java") ||
            fileName.endsWith("Tests.java") ||
            fileName.endsWith("Mock.java")) {
            return true;
        }
        
        return false;
    }
    
    /**
     * 标准化 Path 对象为相对路径字符串
     */
    private String normalizePath(Path file) {
        if (repoRoot == null) {
            return file.toString().replace('\\', '/');
        }
        
        try {
            Path relative = repoRoot.relativize(file);
            return relative.toString().replace('\\', '/');
        } catch (IllegalArgumentException e) {
            // 无法相对化，返回绝对路径
            return file.toString().replace('\\', '/');
        }
    }
    
    /**
     * 标准化字符串路径
     */
    private String normalizePathString(String path) {
        return path.replace('\\', '/');
    }
    
    /**
     * 获取简单类名
     */
    private String getSimpleName(String pathOrClass) {
        String normalized = normalizePathString(pathOrClass);
        int lastSlash = normalized.lastIndexOf('/');
        String name = lastSlash >= 0 ? normalized.substring(lastSlash + 1) : normalized;
        int lastDot = name.lastIndexOf('.');
        return lastDot > 0 ? name.substring(0, lastDot) : name;
    }
}
