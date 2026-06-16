package com.codemind.session;

import com.codemind.llm.Message;
import com.codemind.session.ContextWindowManager;
import com.codemind.session.TokenCountService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class SlidingWindowContextManager implements ContextWindowManager {

    private static final Logger log = LoggerFactory.getLogger(SlidingWindowContextManager.class);
    private static final int DEFAULT_RESERVED_TOKENS = 1024;

    private final TokenCountService tokenCountService;
    private int reservedResponseTokens;
    private double targetRatio = 0.8;

    public SlidingWindowContextManager() {
        this(new JTokkitTokenCountService());
    }

    public SlidingWindowContextManager(TokenCountService tokenCountService) {
        this.tokenCountService = tokenCountService;
        this.reservedResponseTokens = DEFAULT_RESERVED_TOKENS;
    }

    public static SlidingWindowContextManager forModel(String modelId) {
        return new SlidingWindowContextManager(JTokkitTokenCountService.forModel(modelId));
    }

    public void setTargetRatio(double targetRatio) { this.targetRatio = targetRatio; }

    @Override
    public List<Message> manageWindow(List<Message> messages, Message systemMessage) {
        List<Message> result = new ArrayList<>();
        // 保留系统消息在头部
        if (systemMessage != null) {
            result.add(systemMessage);
        }
        // 移除其他 SYSTEM 角色消息
        for (Message msg : messages) {
            if (msg.getRole() == Message.Role.SYSTEM) continue;
            result.add(msg);
        }

        // 【harness 规则 03】SlidingWindowManager 不做工具结果占位（由 L2 统一处理）
        // 仅做窗口裁剪：超出预算时删除最旧的 USER 消息
        if (isOverLimit(result)) {
            result = trimMessages(result, systemMessage != null);
        }

        return result;
    }

    public TokenCountService getTokenCountService() {
        return tokenCountService;
    }

    @Override
    public boolean isOverLimit(List<Message> messages) {
        int tokens = getCurrentTokenCount(messages);
        return tokens > tokenCountService.getAvailableContextTokens(reservedResponseTokens);
    }

    @Override
    public int getCurrentTokenCount(List<Message> messages) {
        return tokenCountService.estimateTokens(messages);
    }

    @Override
    public int getRemainingTokens(List<Message> messages) {
        int used = getCurrentTokenCount(messages);
        int available = tokenCountService.getAvailableContextTokens(reservedResponseTokens);
        return Math.max(0, available - used);
    }

    @Override
    public void setReservedResponseTokens(int tokens) {
        this.reservedResponseTokens = tokens;
    }

    @Override
    public int getReservedResponseTokens() {
        return reservedResponseTokens;
    }

    private List<Message> trimMessages(List<Message> messages, boolean hasSystemMessage) {
        List<Message> result = new ArrayList<>(messages);

        int maxIterations = 100;
        int iteration = 0;
        int limit = (int) (tokenCountService.getAvailableContextTokens(reservedResponseTokens) * targetRatio);

        while (getCurrentTokenCount(result) > limit && result.size() > (hasSystemMessage ? 2 : 1) && iteration < maxIterations) {
            iteration++;

            int deleteIndex = -1;
            int startIndex = hasSystemMessage && result.get(0).getRole() == Message.Role.SYSTEM ? 1 : 0;

            for (int i = startIndex; i < result.size(); i++) {
                if (result.get(i).getRole() == Message.Role.USER) {
                    deleteIndex = i;
                    break;
                }
            }

            if (deleteIndex == -1) break;

            result.remove(deleteIndex);

            if (deleteIndex < result.size()) {
                Message.Role nextRole = result.get(deleteIndex).getRole();
                if (nextRole == Message.Role.ASSISTANT) {
                    result.remove(deleteIndex);
                    while (deleteIndex < result.size() && result.get(deleteIndex).getRole() == Message.Role.TOOL) {
                        result.remove(deleteIndex);
                    }
                } else if (nextRole == Message.Role.TOOL) {
                    while (deleteIndex < result.size() && result.get(deleteIndex).getRole() == Message.Role.TOOL) {
                        result.remove(deleteIndex);
                    }
                }
            }
        }

        if (iteration >= maxIterations) {
            log.warn("上下文裁剪达到最大迭代次数，可能存在配置问题");
        }

        return result;
    }

}
