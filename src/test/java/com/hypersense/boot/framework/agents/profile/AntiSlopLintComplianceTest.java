package com.hypersense.boot.framework.agents.profile;

import com.hypersense.boot.framework.agents.profile.lint.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 反 slop lint 合规测试：10 份样本 HTML 中，前 9 份故意 slop，第 10 份 clean。
 * 期望：前 9 份至少被 1 条规则拦截；第 10 份全部放过。
 */
class AntiSlopLintComplianceTest {

    private List<LintRule> rulesWithBrandGreen;
    private List<LintRule> rulesWithoutBrand;

    @BeforeEach
    void setUp() {
        rulesWithBrandGreen = List.of(
                new NoPurpleGradientRule(),
                new NoEmojiIconRule(),
                new NoPlaceholderRule(),
                new NoSvgHumanRule(),
                new BrandColorDriftRule("#07c160", 15.0)
        );
        rulesWithoutBrand = List.of(
                new NoPurpleGradientRule(),
                new NoEmojiIconRule(),
                new NoPlaceholderRule(),
                new NoSvgHumanRule()
        );
    }

    static Stream<Arguments> slopSamples() {
        return Stream.of(
                Arguments.of("01_purple_gradient.html", true),
                Arguments.of("02_emoji_icon.html", true),
                Arguments.of("03_lorem_ipsum.html", true),
                Arguments.of("04_todo.html", true),
                Arguments.of("05_dots.html", true),
                Arguments.of("06_svg_face.html", true),
                Arguments.of("07_svg_face_keywords.html", true),
                Arguments.of("08_indigo_gradient.html", true),
                Arguments.of("09_brand_drift.html", true),
                Arguments.of("10_clean.html", false)
        );
    }

    @ParameterizedTest(name = "{0} → shouldFail={1}")
    @MethodSource("slopSamples")
    void samplesShouldMatchExpectation(String fileName, boolean shouldFail) throws IOException {
        Path path = Paths.get("src/test/resources/anti-slop-samples", fileName);
        String html = Files.readString(path, StandardCharsets.UTF_8);
        List<LintRule> rules = fileName.contains("brand_drift") ? rulesWithBrandGreen : rulesWithoutBrand;

        boolean anyFail = rules.stream().anyMatch(r -> r.check(html) != null);

        if (shouldFail) {
            assertTrue(anyFail, "样本 " + fileName + " 应被至少 1 条规则拦截");
        } else {
            assertFalse(anyFail, "样本 " + fileName + " 应通过所有规则");
        }
    }
}
