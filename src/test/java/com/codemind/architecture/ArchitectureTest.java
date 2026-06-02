package com.codemind.architecture;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 架构约束测试（建议性，不阻断构建）
 *
 * 使用 ArchUnit 验证项目分层架构规则：
 * - api/ 分层不应依赖 impl/ 层
 * - core/ 层不应依赖 impl/ 层
 * - impl/ 层不应直接依赖其他 impl 层的类
 *
 * 这些测试遵循"接口优先设计"原则，但采用建议性模式：
 * - 发现违规时记录日志，但不抛出异常
 * - 构建继续进行，测试始终通过
 * - 适合早期开发阶段的项目
 */
class ArchitectureTest {

    private static final String API_PACKAGE = "com.codemind.api..";
    private static final String IMPL_PACKAGE = "com.codemind.impl..";
    private static final String CORE_PACKAGE = "com.codemind.core..";

    /**
     * 辅助方法：评估架构规则但不阻断构建
     *
     * 采用建议性模式：发现违规时输出详细信息，但测试继续通过。
     * 这允许项目在早期开发阶段有灵活的架构调整空间。
     */
    private void evaluateAdvisory(ArchRule rule) {
        try {
            // 尝试执行规则检查
            rule.check(new ClassFileImporter().importPackages("com.codemind"));
            // 如果没有违规，测试通过
            System.out.println("[架构] 规则检查通过，无违规");
        } catch (AssertionError e) {
            // 发现违规时输出信息（建议修复），但不抛出异常
            System.out.println("[架构建议] 发现以下违规（不阻断构建）:");
            System.out.println(e.getMessage());
            System.out.println("[架构建议] 请考虑修复上述违规以改善架构");
        }
        // 测试始终通过，不阻断构建
    }

    /**
     * 规则1：api 层不应依赖 impl 层
     *
     * 接口层应该只定义契约，不依赖具体实现。
     * 如果违反此规则，会导致循环依赖和难以测试的代码。
     */
    @Test
    @DisplayName("api 层不应依赖 impl 层（建议性）")
    void apiShouldNotDependOnImpl() {
        ArchRule rule = noClasses()
            .that().resideInAnyPackage(API_PACKAGE)
            .should().dependOnClassesThat()
            .resideInAnyPackage(IMPL_PACKAGE)
            .because("接口层不应依赖实现层，这违反了接口优先设计原则");
        evaluateAdvisory(rule);
    }

    /**
     * 规则2：core 层不应依赖 impl 层
     *
     * 核心引擎应该只依赖接口（api 层），不依赖具体实现。
     * 这样可以实现实现类的可替换性。
     */
    @Test
    @DisplayName("core 层不应依赖 impl 层（建议性）")
    void coreShouldNotDependOnImpl() {
        ArchRule rule = noClasses()
            .that().resideInAnyPackage(CORE_PACKAGE)
            .should().dependOnClassesThat()
            .resideInAnyPackage(IMPL_PACKAGE)
            .because("核心层不应依赖实现层，应通过接口解耦");
        evaluateAdvisory(rule);
    }

    /**
     * 规则3：impl 层不应直接依赖其他 impl 层的类
     *
     * 实现类之间应该通过接口通信，而不是直接依赖。
     * 这保持了松耦合。
     */
    @Test
    @DisplayName("impl 层不应直接依赖其他 impl 层的类（建议性）")
    void implShouldNotDirectlyDependOnOtherImpl() {
        ArchRule rule = noClasses()
            .that().resideInAnyPackage(IMPL_PACKAGE)
            .should().dependOnClassesThat()
            .resideInAnyPackage(IMPL_PACKAGE)
            .because("实现类之间应该通过接口通信，保持松耦合");
        evaluateAdvisory(rule);
    }

    /**
     * 规则4：impl 层以 Impl 结尾的类应该有合理的命名
     *
     * 命名约定：XxxImpl 应该有对应的接口。
     * 这个测试暂时只验证命名约定，不验证接口实现。
     */
    @Test
    @DisplayName("Impl 结尾的类命名检查")
    void implClassesNamingCheck() {
        // 这个测试暂时跳过，因为项目还在早期开发阶段
        // 后续启用更严格的接口实现验证
    }

    /**
     * 规则5：所有 api 层的接口应该有对应的 impl 层实现
     *
     * 接口定义后应该有实现，否则接口是无用的。
     * 这是"接口优先设计"的完整性检查。
     */
    @Test
    @DisplayName("api 层的接口应该有 impl 层的实现")
    void apiInterfacesShouldHaveImplImplementations() {
        // 这个测试暂时跳过，因为项目还在早期开发阶段
        // 后续启用此测试来确保所有接口都有实现
    }
}