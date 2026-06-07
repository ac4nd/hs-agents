package com.hypersense.boot.framework.agents.exception;

/**
 * HITL 中断异常
 * <p>
 * 当 HITL 启用且无 checkpoint 支持时，通过异常机制传播中断信号。
 * 该异常需要穿透 {@link com.hypersense.boot.framework.agents.middleware.MiddlewarePipeline}，
 * 不被中间件的 catch(Exception) 吞掉。
 * </p>
 *
 * @author Claude
 * @since 2026/5/23
 */
public class HitlInterruptedException extends RuntimeException {

    private final String nodeName;

    public HitlInterruptedException(String nodeName, String message) {
        super(message);
        this.nodeName = nodeName;
    }

    public HitlInterruptedException(String nodeName, String message, Throwable cause) {
        super(message, cause);
        this.nodeName = nodeName;
    }

    public String getNodeName() {
        return nodeName;
    }
}
