// src/main/java/com/hypersense/boot/framework/agents/profile/impl/CodeProfile.java
package com.hypersense.boot.framework.agents.profile.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hypersense.boot.framework.agents.profile.*;
import com.hypersense.boot.framework.agents.profile.lint.*;
import com.hypersense.boot.framework.agents.tool.SandboxExecutor;

import java.util.List;

/**
 * 真实 code-profile 实现（替换 Plan A 的 StubCodeProfile）。
 *
 * 覆盖能力：
 * - 真实工程纪律 systemPrompt（SOLID/TDD/读后写/不改测试以通过/反幻觉 API）
 * - 4 条 lint 规则（compile_pass + test_pass + no_phantom_api + comment_language_match）
 * - 强制 TEST_HITL 中断
 *
 * CompilePassRule / TestPassRule 需运行时 sandbox，由 withRuntimeContext 工厂注入。
 * NoPhantomApiRule 需运行时 sessionId，由 withRuntimeContext 工厂注入。
 */
public class CodeProfile extends AbstractCapabilityProfile {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final List<LintRule> lintRules;

    public CodeProfile(String id, String name, String template, List<String> tools,
                       PlanStrategy strategy, JsonNode outputFormat, HitlPolicy policy) {
        super(id, name, template, tools, strategy, outputFormat, policy);
        // 静态规则（不需要运行时 sandbox/sessionId）
        this.lintRules = List.of(new CommentLanguageMatchRule());
    }

    /**
     * 创建带 sandbox 与 session 上下文的 code-profile（推荐入口）。
     * @param sandbox    sandbox 执行器（compile_pass / test_pass 用）
     * @param symbolReg  API 符号白名单
     * @param sessionId  当前 session（no_phantom_api 用）
     * @param language   主语言（决定 compile/test 命令）
     * @param sourceFile 主源文件路径（compile_pass 用）
     * @param testFile   主测试文件路径（test_pass 用）
     */
    public static CodeProfile withRuntimeContext(
            SandboxExecutor sandbox, SymbolRegistry symbolReg, String sessionId,
            String language, String sourceFile, String testFile,
            String template, List<String> tools, JsonNode outputFormat, HitlPolicy policy) {

        CodeProfile base = new CodeProfile(
                "code", "代码模式", template, tools,
                PlanStrategy.TDD, outputFormat, policy);

        List<LintRule> runtimeRules = List.of(
                new CommentLanguageMatchRule(),
                new NoPhantomApiRule(symbolReg, sessionId),
                new CompilePassRule(sandbox, language, sourceFile),
                new TestPassRule(sandbox, language, testFile)
        );

        return new CodeProfile(base, runtimeRules);
    }

    private CodeProfile(CodeProfile source, List<LintRule> customRules) {
        super(source.id(), source.name(),
                source.getSystemPromptTemplate(),  // 同包 protected getter 已存在
                source.allowedTools(), source.planStrategy(),
                source.outputFormat(), source.hitlPolicy());
        this.lintRules = customRules;
    }

    @Override
    public List<LintRule> lintRules() {
        return lintRules;
    }

    /** code-profile 默认 outputFormat */
    public static JsonNode defaultOutputFormat() {
        try {
            return MAPPER.readTree(CodeSchema.OUTPUT_FORMAT_JSON);
        } catch (Exception e) {
            ObjectNode fallback = MAPPER.createObjectNode();
            fallback.put("note", "fallback: schema parse failed");
            return fallback;
        }
    }
}
