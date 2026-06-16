package com.codemind.safety.spi;

/**
 * 速率限制器接口
 * 
 * 控制 API 调用的频率，防止超出 Rate Limit。
 * 
 * 设计原则：接口分离原则（ISP）
 * - 提供简单的 acquire() 方法用于通用场景
 * - 提供 tryAcquire() 用于需要判断结果的场景
 */
public interface RateLimiter {
    
    /**
     * 请求一个许可（阻塞直到获取）
     * 
     * 如果当前没有可用许可，将阻塞等待。
     * 
     * @return 获取到的许可数量（通常为1）
     * @throws InterruptedException 如果等待被中断
     */
    long acquire() throws InterruptedException;
    
    /**
     * 请求指定数量的许可（阻塞直到获取）
     * 
     * @param permits 需要的许可数量
     * @return 获取到的许可数量
     * @throws InterruptedException 如果等待被中断
     */
    long acquire(long permits) throws InterruptedException;
    
    /**
     * 尝试获取指定数量的许可（非阻塞）
     *
     * @param permits 需要的许可数量
     * @return true 如果获取成功，false 如果没有足够许可
     */
    boolean tryAcquire(long permits);
}