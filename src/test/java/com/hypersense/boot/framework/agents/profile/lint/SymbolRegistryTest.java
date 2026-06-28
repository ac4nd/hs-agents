package com.hypersense.boot.framework.agents.profile.lint;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SymbolRegistryTest {

    @Test
    void shouldRegisterAndCheckSymbolExistence() {
        SymbolRegistry reg = new SymbolRegistry();
        reg.register("s1", "np.array");
        reg.register("s1", "useState");

        assertTrue(reg.contains("s1", "np.array"));
        assertTrue(reg.contains("s1", "useState"));
        assertFalse(reg.contains("s1", "np.imaginary_func"));
    }

    @Test
    void shouldIsolateBetweenSessions() {
        SymbolRegistry reg = new SymbolRegistry();
        reg.register("s1", "np.array");

        assertFalse(reg.contains("s2", "np.array"));
    }

    @Test
    void shouldClearSession() {
        SymbolRegistry reg = new SymbolRegistry();
        reg.register("s1", "np.array");
        reg.clearSession("s1");
        assertFalse(reg.contains("s1", "np.array"));
    }

    @Test
    void shouldReturnSize() {
        SymbolRegistry reg = new SymbolRegistry();
        reg.register("s1", "np.array");
        reg.register("s1", "np.zeros");
        assertEquals(2, reg.size("s1"));
    }

    @Test
    void shouldPreRegisterCommonBuiltinApis() {
        SymbolRegistry reg = SymbolRegistry.withCommonBuiltins();
        // Python 内置应已注册
        assertTrue(reg.contains("s1", "print"));
        assertTrue(reg.contains("s1", "len"));
        assertTrue(reg.contains("s1", "range"));
        // JS 内置
        assertTrue(reg.contains("s1", "console.log"));
        assertTrue(reg.contains("s1", "Array.from"));
    }
}
