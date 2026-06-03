package com.codemind.api.analysis;

import java.nio.file.Path;
import java.util.Set;

/**
 * 依赖图接口
 * 
 * 用于分析代码文件间的依赖关系，支持：
 * - 基于 import 语句的依赖解析
 * - BFS 遍历查找受影响文件
 * - 变更影响范围评估
 * 
 * 学习要点：
 * - 图数据结构的应用
 * - BFS/DFS 遍历算法
 * - import 解析
 * 
 * TODO（第二版实现）:
 * - 解析 Java/Python import 语句
 * - 构建文件间依赖图（邻接表/邻接矩阵）
 * - BFS 查找 N 层内的相关文件
 * - 循环依赖检测
 * - 核心模块识别（高度数节点）
 */
public interface DependencyGraph {
    
    /**
     * 构建依赖图
     * 
     * 遍历指定目录下的代码文件，解析 import 语句，
     * 构建文件间依赖关系图。
     * 
     * @param repoRoot 代码仓库根目录
     * 
     * TODO 实现:
     * 1. 遍历目录下的 .java/.py 文件
     * 2. 解析 import 语句（Java: import xxx.Xxx; Python: from xxx import Xxx）
     * 3. 构建 Map<String, Set<String>> 依赖图
     *    - key: 文件路径
     *    - value: 该文件依赖的文件集合
     * 4. 可选：存储到 SQLite 或内存
     */
    void build(Path repoRoot);
    
    /**
     * 查找受影响的文件
     * 
     * 从变更文件出发，BFS 遍历依赖图，
     * 找出 maxHops 步内的所有相关文件。
     * 
     * @param changedFiles 变更文件集合
     * @param maxHops 最大跳数（0 = 只返回变更文件本身）
     * @return 受影响的文件集合
     * 
     * TODO 实现:
     * 1. 初始化队列，放入变更文件
     * 2. BFS 遍历：
     *    - 弹出队列头部文件
     *    - 查找依赖该文件的所有文件（即依赖图的反向）
     *    - 放入队列
     * 3. 记录已访问文件，避免重复
     * 4. 返回 maxHops 步内的所有文件
     */
    Set<String> findAffectedFiles(Set<String> changedFiles, int maxHops);
    
    /**
     * 获取文件的直接依赖
     * 
     * @param filePath 文件路径
     * @return 该文件直接依赖的其他文件
     * 
     * TODO 实现:
     * 直接从构建好的依赖图中查找
     */
    Set<String> getDependencies(String filePath);
    
    /**
     * 获取依赖该文件的文件（反向查找）
     * 
     * @param filePath 文件路径
     * @return 依赖该文件的所有文件
     * 
     * TODO 实现:
     * 需要维护反向索引，或在查找时遍历
     */
    Set<String> getDependents(String filePath);
    
    /**
     * 检查是否存在循环依赖
     * 
     * @param filePaths 要检查的文件集合
     * @return 如果存在循环依赖返回 true
     * 
     * TODO 实现:
     * DFS 检测循环：
     * 1. 对每个文件做 DFS
     * 2. 维护三种状态：未访问、访问中、已完成
     * 3. 如果遇到"访问中"的节点，说明有循环
     */
    boolean hasCircularDependency(Set<String> filePaths);
    
    /**
     * 获取核心模块（高度数节点）
     * 
     * 识别被多个文件依赖的"核心"模块，
     * 这些模块变更影响范围最大。
     * 
     * @param threshold 被依赖次数阈值
     * @return 核心模块文件路径
     * 
     * TODO 实现:
     * 1. 统计每个文件的入度（被依赖次数）
     * 2. 筛选入度 >= threshold 的文件
     * 3. 按入度降序排序返回
     */
    Set<String> getCoreModules(int threshold);
    
    /**
     * 计算变更风险评分
     * 
     * 综合考虑：
     * - 是否为核心模块
     * - 是否被测试覆盖
     * - 依赖/被依赖的文件数量
     * 
     * @param filePath 文件路径
     * @return 风险评分 0.0 - 1.0（越高越危险）
     * 
     * TODO 实现:
     * 参考 code-review-graph 的评分逻辑：
     * - 核心模块 +0.2
     * - 无测试覆盖 +0.3
     * - 安全相关关键词 +0.2
     * - 依赖/被依赖数量加权
     */
    double calculateRiskScore(String filePath);
    
    /**
     * 清空依赖图，释放内存
     */
    void clear();
    
    /**
     * 获取图的基本统计信息
     */
    default String getStats() {
        return "DependencyGraph (not yet implemented)";
    }
}
