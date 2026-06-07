package com.codemind.impl.safety;

import com.codemind.api.safety.RateLimiter;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 令牌桶速率限制器
 * 
 * 使用令牌桶算法实现 API 调用频率控制。
 * 
 * 参数说明：
 * - permitsPerSecond: 每秒生成的令牌数（API Rate Limit）
 * - maxBurst: 桶的最大容量（允许的突发流量大小）
 * 
 * 示例用法：
 * <pre>
 * // 创建一个每秒 10 次，最大突发 20 次的 RateLimiter
 * RateLimiter limiter = new TokenBucketRateLimiter(10, 20);
 * 
 * // 使用前请求许可
 * limiter.acquire();  // 阻塞直到获取到令牌
 * callAPI();          // 执行 API 调用
 * </pre>
 */
public class TokenBucketRateLimiter implements RateLimiter {
    
    /** 每秒生成的令牌数 */
    private final long permitsPerSecond;
    
    /** 桶的最大容量 */
    private final long maxBurst;
    
    /** 当前令牌数量 */
    private long currentTokens;
    
    /** 上次更新时间 */
    private long lastRefillTime;
    
    /** 锁，保证线程安全 */
    private final ReentrantLock lock = new ReentrantLock();
    
    /**
     * 创建令牌桶速率限制器
     * 
     * @param permitsPerSecond 每秒允许的请求数
     * @param maxBurst 最大突发请求数（桶容量）
     */
    public TokenBucketRateLimiter(long permitsPerSecond, long maxBurst) {
        if (permitsPerSecond <= 0) {
            throw new IllegalArgumentException("permitsPerSecond must be positive");
        }
        if (maxBurst <= 0) {
            throw new IllegalArgumentException("maxBurst must be positive");
        }
        
        this.permitsPerSecond = permitsPerSecond;
        this.maxBurst = maxBurst;
        this.currentTokens = maxBurst;  // 初始时桶是满的
        this.lastRefillTime = System.nanoTime();
    }
    
    /**
     * 创建令牌桶速率限制器（突发量等于每秒请求数）
     * 
     * @param permitsPerSecond 每秒允许的请求数
     */
    public TokenBucketRateLimiter(long permitsPerSecond) {
        this(permitsPerSecond, permitsPerSecond);
    }
    
    @Override
    public long acquire() throws InterruptedException {
        return acquire(1);
    }
    
    @Override
    public long acquire(long permits) throws InterruptedException {
        if (permits <= 0) {
            throw new IllegalArgumentException("permits must be positive");
        }
        
        while (true) {
            lock.lockInterruptibly();
            try {
                refillTokens();
                
                if (currentTokens >= permits) {
                    currentTokens -= permits;
                    return permits;
                }
                
                // 计算需要等待的时间
                long neededTokens = permits - currentTokens;
                long waitNanos = (long) (neededTokens * 1_000_000_000.0 / permitsPerSecond);
                
                // 释放锁并等待
                lock.unlock();
                TimeUnit.NANOSECONDS.sleep(waitNanos);
                lock.lockInterruptibly();
                
                // 等待后重新检查
            } finally {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }
        }
    }
    
    @Override
    public boolean tryAcquire() {
        return tryAcquire(1);
    }
    
    @Override
    public boolean tryAcquire(long permits) {
        if (permits <= 0) {
            throw new IllegalArgumentException("permits must be positive");
        }
        
        lock.lock();
        try {
            refillTokens();
            
            if (currentTokens >= permits) {
                currentTokens -= permits;
                return true;
            }
            
            return false;
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * 尝试在指定时间内获取指定数量的许可
     * 
     * @param permits 需要的许可数量
     * @param timeout 最长等待时间（毫秒）
     * @return true 如果获取成功，false 如果超时
     */
    public boolean tryAcquire(long permits, long timeout) throws InterruptedException {
        if (permits <= 0) {
            throw new IllegalArgumentException("permits must be positive");
        }
        
        long startTime = System.nanoTime();
        long timeoutNanos = TimeUnit.MILLISECONDS.toNanos(timeout);
        
        while (true) {
            lock.lockInterruptibly();
            try {
                refillTokens();
                
                if (currentTokens >= permits) {
                    currentTokens -= permits;
                    return true;
                }
                
                // 检查是否超时
                long elapsed = System.nanoTime() - startTime;
                if (elapsed >= timeoutNanos) {
                    return false;
                }
                
                // 计算需要等待的时间
                long neededTokens = permits - currentTokens;
                long waitNanos = (long) (neededTokens * 1_000_000_000.0 / permitsPerSecond);
                
                // 确保不超过剩余超时时间
                long remainingNanos = timeoutNanos - elapsed;
                waitNanos = Math.min(waitNanos, remainingNanos);
                
                // 释放锁并等待
                lock.unlock();
                TimeUnit.NANOSECONDS.sleep(waitNanos);
                lock.lockInterruptibly();
                
            } finally {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }
        }
    }
    
    @Override
    public long availablePermits() {
        lock.lock();
        try {
            refillTokens();
            return currentTokens;
        } finally {
            lock.unlock();
        }
    }
    
    @Override
    public void reset() {
        lock.lock();
        try {
            this.currentTokens = maxBurst;
            this.lastRefillTime = System.nanoTime();
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * 补充令牌
     * 
     * 根据时间差计算应该补充的令牌数量。
     */
    private void refillTokens() {
        long now = System.nanoTime();
        long elapsed = now - lastRefillTime;
        
        if (elapsed > 0) {
            // 计算应该补充的令牌数
            double newTokens = elapsed * permitsPerSecond / 1_000_000_000.0;
            currentTokens = Math.min(maxBurst, (long) (currentTokens + newTokens));
            lastRefillTime = now;
        }
    }
    
    /**
     * 获取速率配置
     */
    public long getPermitsPerSecond() {
        return permitsPerSecond;
    }
    
    /**
     * 获取最大突发量
     */
    public long getMaxBurst() {
        return maxBurst;
    }
}