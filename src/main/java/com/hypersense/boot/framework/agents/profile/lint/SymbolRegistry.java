package com.hypersense.boot.framework.agents.profile.lint;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * 已确认存在的 API 符号白名单（session 级隔离）。
 *
 * 数据来源：
 * - 启动时预注册常见语言内置（Python print/len/range、JS console.log/Array.from）
 * - LLM 调 package_lookup 成功 → 符号加入
 * - LLM 调 file_write 写源码 → 解析 import/from/require 语句自动加入「文件级符号」
 *
 * NoPhantomApiRule 检查源码中所有 API 调用符号是否都在本表内。
 */
@Component
public class SymbolRegistry {

    private final Map<String, Set<String>> perSession = new ConcurrentHashMap<>();

    /** 预注册内置符号集 */
    private static final Set<String> COMMON_BUILTINS = Set.of(
            // Python 内置
            "print", "len", "range", "list", "dict", "set", "tuple", "str", "int", "float",
            "bool", "open", "input", "enumerate", "zip", "map", "filter", "sorted", "reversed",
            "sum", "min", "max", "abs", "any", "all", "isinstance", "type",
            // JS 内置
            "console.log", "console.error", "console.warn",
            "Array.from", "Array.isArray", "Array.of",
            "Object.keys", "Object.values", "Object.entries", "Object.assign",
            "JSON.parse", "JSON.stringify",
            "Math.max", "Math.min", "Math.floor", "Math.ceil", "Math.random"
    );

    public SymbolRegistry() {}

    public static SymbolRegistry withCommonBuiltins() {
        SymbolRegistry reg = new SymbolRegistry();
        reg.perSession.put("__shared__", new CopyOnWriteArraySet<>(COMMON_BUILTINS));
        return reg;
    }

    public void register(String sessionId, String symbol) {
        if (symbol == null || symbol.isBlank()) return;
        perSession.computeIfAbsent(sessionId, k -> new CopyOnWriteArraySet<>()).add(symbol.trim());
    }

    public boolean contains(String sessionId, String symbol) {
        if (symbol == null) return false;
        Set<String> shared = perSession.get("__shared__");
        Set<String> session = perSession.get(sessionId);
        return (shared != null && shared.contains(symbol)) ||
               (session != null && session.contains(symbol));
    }

    public int size(String sessionId) {
        Set<String> session = perSession.get(sessionId);
        return session == null ? 0 : session.size();
    }

    public void clearSession(String sessionId) {
        perSession.remove(sessionId);
    }
}
