package com.hypersense.boot.framework.agents.profile.lint;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 会话级 lint 违规计数器（Plan C P0#2）。
 *
 * <p>设计参考 {@code TddPhaseManager}：单例 + ConcurrentHashMap 按 sessionId 隔离计数，
 * 满足「同一会话内 file_write/file_render 多次违规累计 → 触发 HITL」的需求。
 * 不同会话互不干扰，线程安全。</p>
 *
 * <p>使用流程：</p>
 * <ol>
 *   <li>{@link #increment} 在 ToolNode 每次命中 lint 违规时累加</li>
 *   <li>{@link #total} / {@link #perRule} 用于决策是否触发 HITL（对比 {@code HitlPolicy.maxLintRetriesBeforeInterrupt}）</li>
 *   <li>{@link #reset} 在会话结束 / HITL 审批通过 / 显式 retry 时清零</li>
 * </ol>
 *
 * @author Claude
 * @since 2026/6/29
 */
@Slf4j
@Component
public class LintStatsManager {

    /** 会话级统计快照（不可变）。 */
    public record LintStats(int total, Map<String, Integer> perRule) { }

    private final Map<String, Map<String, AtomicInteger>> sessionStats = new ConcurrentHashMap<>();

    /**
     * 累加 1 次违规。
     * @return 当前会话该规则的累计违规次数（per-rule count，便于 P0#2 调用方对比 HitlPolicy.maxLintRetriesBeforeInterrupt）
     */
    public int increment(String sessionId, String ruleId) {
        String sid = normalize(sessionId);
        String rid = ruleId == null || ruleId.isBlank() ? "unknown" : ruleId;
        int newVal = sessionStats.computeIfAbsent(sid, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(rid, k -> new AtomicInteger(0))
                .incrementAndGet();
        return newVal;
    }

    /** 当前会话累计违规次数。 */
    public int total(String sessionId) {
        String sid = normalize(sessionId);
        Map<String, AtomicInteger> perRule = sessionStats.get(sid);
        if (perRule == null || perRule.isEmpty()) return 0;
        int sum = 0;
        for (AtomicInteger c : perRule.values()) sum += c.get();
        return sum;
    }

    /** 当前会话某条规则累计违规次数。 */
    public int perRule(String sessionId, String ruleId) {
        String sid = normalize(sessionId);
        String rid = ruleId == null || ruleId.isBlank() ? "unknown" : ruleId;
        Map<String, AtomicInteger> perRule = sessionStats.get(sid);
        if (perRule == null) return 0;
        AtomicInteger c = perRule.get(rid);
        return c == null ? 0 : c.get();
    }

    /** 当前会话的完整快照（防御性拷贝）。 */
    public LintStats snapshot(String sessionId) {
        String sid = normalize(sessionId);
        Map<String, AtomicInteger> perRule = sessionStats.get(sid);
        if (perRule == null || perRule.isEmpty()) {
            return new LintStats(0, Map.of());
        }
        Map<String, Integer> copy = new java.util.HashMap<>();
        int sum = 0;
        for (var e : perRule.entrySet()) {
            int v = e.getValue().get();
            copy.put(e.getKey(), v);
            sum += v;
        }
        return new LintStats(sum, Map.copyOf(copy));
    }

    /** 清零（会话结束 / HITL 审批通过 / 显式 retry 时调用）。 */
    public void reset(String sessionId) {
        String sid = normalize(sessionId);
        sessionStats.remove(sid);
    }

    private static String normalize(String sessionId) {
        return (sessionId == null || sessionId.isBlank()) ? "__default__" : sessionId;
    }
}
