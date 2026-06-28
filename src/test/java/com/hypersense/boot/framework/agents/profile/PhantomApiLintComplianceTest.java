package com.hypersense.boot.framework.agents.profile;

import com.hypersense.boot.framework.agents.profile.lint.NoPhantomApiRule;
import com.hypersense.boot.framework.agents.profile.lint.SymbolRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class PhantomApiLintComplianceTest {

    private SymbolRegistry registry;
    private NoPhantomApiRule rule;

    @BeforeEach
    void setUp() {
        registry = SymbolRegistry.withCommonBuiltins();
        // 注册已知存在的 API（模拟 LLM 已 package_lookup 过）
        registry.register("s1", "np.array");
        registry.register("s1", "pd.read_csv");
        rule = new NoPhantomApiRule(registry, "s1");
    }

    static Stream<Arguments> samples() {
        return Stream.of(
                Arguments.of("01_clean_numpy.py", false),
                Arguments.of("02_phantom_np_func.py", true),
                Arguments.of("03_phantom_react_hook.js", true),
                Arguments.of("04_clean_builtin.py", false),
                Arguments.of("05_phantom_pandas.py", true)
        );
    }

    @ParameterizedTest(name = "{0} → shouldFail={1}")
    @MethodSource("samples")
    void samplesShouldMatchExpectation(String fileName, boolean shouldFail) throws IOException {
        Path path = Paths.get("src/test/resources/phantom-api-samples", fileName);
        String code = Files.readString(path, StandardCharsets.UTF_8);
        String err = rule.check(code);
        if (shouldFail) {
            assertNotNull(err, fileName + " 应被 no_phantom_api 拦截");
        } else {
            assertNull(err, fileName + " 应通过：" + err);
        }
    }
}
