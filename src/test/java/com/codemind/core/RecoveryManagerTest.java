package com.codemind.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测试 RecoveryManager 的循环检测功能。
 */
class RecoveryManagerTest {

    private RecoveryManager recoveryManager;

    @BeforeEach
    void setUp() {
        recoveryManager = new RecoveryManager();
    }

    @Test
    void testLoopDetectionForTaskTool() {
        // 模拟 Task 工具调用，使用不同的 instruction 参数
        // 由于 digestArgs 现在会提取 instruction，不同的调用不会被误判为循环
        String instruction1 = "搜索项目中的所有 Java 文件";
        String instruction2 = "分析代码质量并给出建议";
        String instruction3 = "重构这个类以提高可读性";
        String instruction4 = "编写单元测试覆盖边界情况";

        ContinueReason reason1 = recoveryManager.recordToolCall("Task", Map.of("instruction", instruction1));
        ContinueReason reason2 = recoveryManager.recordToolCall("Task", Map.of("instruction", instruction2));
        ContinueReason reason3 = recoveryManager.recordToolCall("Task", Map.of("instruction", instruction3));
        ContinueReason reason4 = recoveryManager.recordToolCall("Task", Map.of("instruction", instruction4));

        // 不同的 instruction 应该不会触发循环检测
        assertNull(reason1);
        assertNull(reason2);
        assertNull(reason3);
        assertNull(reason4);
    }

    @Test
    void testLoopDetectionForSameTaskInstruction() {
        // 模拟相同的 Task 工具调用，使用相同的 instruction 参数
        // 循环检测需要缓冲区大小达到 LOOP_BUFFER_SIZE (12) 才会触发
        String sameInstruction = "搜索所有 Java 文件";

        ContinueReason reason = null;
        for (int i = 0; i < 12; i++) {
            reason = recoveryManager.recordToolCall("Task", Map.of("instruction", sameInstruction));
        }

        // 相同的 instruction 应该触发循环检测
        assertNotNull(reason);
        assertEquals(ContinueReason.LOOP_DETECTED, reason);
    }

    @Test
    void testClearRecentToolCalls() {
        // 模拟循环检测后清空缓冲区
        String sameInstruction = "搜索所有 Java 文件";

        // 触发循环检测
        for (int i = 0; i < 12; i++) {
            recoveryManager.recordToolCall("Task", Map.of("instruction", sameInstruction));
        }

        // 清空缓冲区
        recoveryManager.clearRecentToolCalls();

        // 重新开始记录，不应该立即再次触发循环检测
        ContinueReason reason = recoveryManager.recordToolCall("Task", Map.of("instruction", sameInstruction));
        assertNull(reason);
    }

    @Test
    void testDigestArgsForTaskTool() {
        // 验证 Task 工具的参数摘要提取
        String instruction = "这是一个测试指令，用于验证参数摘要提取功能";
        ContinueReason reason = recoveryManager.recordToolCall("Task", Map.of("instruction", instruction));

        // 单次调用不应触发循环检测
        assertNull(reason);
    }
}
