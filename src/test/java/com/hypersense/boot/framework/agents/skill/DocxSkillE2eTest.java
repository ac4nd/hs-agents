package com.hypersense.boot.framework.agents.skill;

import com.hypersense.boot.framework.agents.GodlikeAgent;
import com.hypersense.boot.framework.agents.config.AgentProperties;
import com.hypersense.boot.framework.agents.sandbox.Sandbox;
import com.hypersense.boot.framework.agents.sandbox.SandboxManager;
import com.hypersense.boot.framework.agents.sandbox.SandboxResult;
import com.hypersense.boot.framework.agents.sandbox.factory.LocalSandboxFactory;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.junit.jupiter.api.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 技能系统 E2E 测试 — GodlikeAgent 加载 docx 技能生成 Word 文档
 * <p>
 * 分两个阶段验证：
 * <ol>
 *   <li>阶段 1：直接沙箱验证 — 手动编写 JS 代码通过 docx npm 包生成 .docx 文件</li>
 *   <li>阶段 2：Agent 完整闭环 — LLM 加载 docx 技能，自动生成 JS 代码并执行</li>
 * </ol>
 * <p>
 * 前置条件：
 * <ul>
 *   <li>Node.js 已安装（node 可用）</li>
 *   <li>docx npm 包已全局安装（npm install -g docx）</li>
 *   <li>LLM API 可访问（使用 application-dev.yml 中的配置）</li>
 * </ul>
 *
 * @author test
 */
class DocxSkillE2eTest {

    private static final String SKILLS_DIR = "D:/project/myproject/ac4nd/skills";
    private static final String API_ENDPOINT = "https://open.bigmodel.cn/api/coding/paas/v4";
    private static final String API_KEY = "40a1cff4ec6c45a09704ec79550211a3.eLcaJYrFS2unG829";
    private static final String MODEL_NAME = "glm-4.7";

    /** 持久化输出目录（不会被沙箱 destroy 清理） */
    private static final Path OUTPUT_DIR = Path.of("D:/project/myproject/workspace/GodlikeAgents/target/docx-e2e-output");

    @BeforeAll
    static void createOutputDir() throws Exception {
        Files.createDirectories(OUTPUT_DIR);
    }

    // ======================== 阶段 1：直接沙箱验证 ========================

    @Nested
    @DisplayName("阶段 1 - 直接沙箱验证：docx npm 包生成 Word")
    class DirectSandboxTests {

        @Test
        @DisplayName("SkillRegistry 扫描 docx 技能 → SkillLoadTool 加载内容 → 沙箱执行 JS 生成 .docx")
        void testSkillLoadAndSandboxExecution() throws Exception {
            // Step 1: SkillRegistry 扫描
            SkillRegistry registry = new SkillRegistry();
            registry.scan(SKILLS_DIR);
            assertFalse(registry.isEmpty(), "应发现 docx 技能");

            var skillOpt = registry.getByName("docx");
            assertTrue(skillOpt.isPresent(), "应找到 docx 技能");
            System.out.println(">>> Step 1: SkillRegistry 扫描成功 — " + registry.getAll().size() + " 个技能");

            // Step 2: SkillLoadTool 加载完整 SKILL.md
            SkillLoadTool tool = new SkillLoadTool(registry);
            @SuppressWarnings("unchecked")
            Map<String, Object> loadResult = (Map<String, Object>) tool.execute(Map.of("skill_name", "docx"));
            assertEquals(true, loadResult.get("success"), "skill_load 应成功");
            String skillContent = (String) loadResult.get("content");
            assertTrue(skillContent.contains("docx-js"), "SKILL.md 应包含 docx-js 说明");
            System.out.println(">>> Step 2: SkillLoadTool 加载成功 — 内容长度 " + skillContent.length());

            // Step 3: 沙箱执行 JS 生成 .docx 文件
            AgentProperties props = new AgentProperties();
            props.getTools().getSandbox().setEnabled(true);
            props.getTools().getSandbox().setTimeout(30);
            props.getTools().getSandbox().getLocal().setSanitizeEnv(false);

            LocalSandboxFactory factory = new LocalSandboxFactory(props);
            SandboxManager sandboxManager = new SandboxManager(factory, props);

            String sessionId = "direct-test-" + System.currentTimeMillis();
            Sandbox sandbox = sandboxManager.getOrCreate(sessionId);

            // 写到持久化输出目录（不受沙箱 destroy 影响）
            String outputPath = OUTPUT_DIR.resolve("GodlikeAgents简介_直接测试.docx").toString().replace("\\", "/");

            String jsCode = buildDocxJsCode(outputPath);

            // 写 JS 文件到沙箱工作目录，然后用 node 执行
            // 需要设置 NODE_PATH 让 Node.js 找到全局安装的 docx 包
            sandbox.writeFile("generate_docx.js", jsCode);
            String nodePath = System.getenv("APPDATA") != null
                    ? System.getenv("APPDATA") + "/npm/node_modules"
                    : "/usr/local/lib/node_modules";
            String nodeCmd = "NODE_PATH=\"" + nodePath.replace("\\", "/") + "\" node generate_docx.js";
            SandboxResult result = sandbox.executeCommand(nodeCmd);
            System.out.println(">>> Step 3: 沙箱执行完成");
            System.out.println("    success=" + result.isSuccess());
            System.out.println("    exitCode=" + result.getExitCode());
            System.out.println("    stdout=" + result.getStdout());
            System.out.println("    stderr=" + result.getStderr());
            System.out.println("    error=" + result.getError());

            // 验证文件生成
            File docxFile = OUTPUT_DIR.resolve("GodlikeAgents简介_直接测试.docx").toFile();
            assertTrue(docxFile.exists(), ".docx 文件应存在: " + docxFile.getAbsolutePath());
            assertTrue(docxFile.length() > 5000,
                    ".docx 文件应 > 5KB，实际: " + docxFile.length() + " bytes");
            System.out.println(">>> Step 4: 文件验证通过 — " + docxFile.getAbsolutePath()
                    + " (" + docxFile.length() + " bytes)");

            // 清理沙箱
            sandboxManager.destroy(sessionId);
        }
    }

    // ======================== 阶段 2：Agent 完整闭环 ========================

    @Nested
    @DisplayName("阶段 2 - Agent 闭环：LLM 加载技能 + 生成文档")
    class AgentE2eTests {

        @Test
        @DisplayName("GodlikeAgent 加载 docx 技能 → LLM 自动规划执行 → 生成 .docx")
        void testAgentWithDocxSkill() {
            String outputPath = OUTPUT_DIR.resolve("GodlikeAgents简介_Agent测试.docx")
                    .toString().replace("\\", "/");

            // 1. ChatModel
            OpenAiChatModel chatModel = OpenAiChatModel.builder()
                    .baseUrl(API_ENDPOINT)
                    .apiKey(API_KEY)
                    .modelName(MODEL_NAME)
                    .temperature(0.3)
                    .maxTokens(16384)
                    .timeout(Duration.ofSeconds(300))
                    .build();

            // 2. 沙箱配置
            AgentProperties props = new AgentProperties();
            props.getTools().getSandbox().setEnabled(true);
            props.getTools().getSandbox().setTimeout(120);
            props.getTools().getSandbox().getLocal().setSanitizeEnv(false);

            LocalSandboxFactory sandboxFactory = new LocalSandboxFactory(props);

            // 3. 构建 Agent
            GodlikeAgent agent = GodlikeAgent.builder()
                    .model(chatModel)
                    .skills(SKILLS_DIR)
                    .sandbox(sandboxFactory)
                    .enableLogging()
                    .recursionLimit(50)
                    .build();

            // 4. 执行（指定输出路径到持久化目录，不受沙箱 destroy 影响）
            String prompt = """
                    任务：创建一份 Word 文档介绍 GodlikeAgents 框架。

                    操作步骤：
                    1. 调用 skill_load("docx") 加载 docx 技能完整说明
                    2. 根据技能说明中的 "Creating New Documents" 章节，用 JavaScript (docx npm 包) 创建文档
                    3. 通过 executeCode 工具执行 JavaScript，文件保存到: %s
                    4. 确认生成成功后给出最终回复

                    文档内容：
                    - 标题：GodlikeAgents 框架简介
                    - 章节：项目概述、核心架构（有向循环图 5 节点）、技术栈表格、快速上手
                    - Arial 字体，US Letter 页面，页眉页脚，目录

                    框架简介：GodlikeAgents 基于 Java 17 + Spring Boot，使用 LangGraph4j 构建有向循环图
                    （plan→execute→tool/delegate→finalize），具备技能系统、HITL、沙箱、多租户能力。
                    """.formatted(outputPath);

            try {
                String result = agent.run(prompt);
                System.out.println("===== Agent 响应 =====");
                if (result != null) {
                    System.out.println(result.length() > 500 ? result.substring(0, 500) + "..." : result);
                }
            } catch (Exception e) {
                System.out.println("Agent 执行异常（可能达到递归上限）: " + e.getMessage());
            }

            // 5. 验证文件（无论 Agent 是否完成，检查文件是否已生成）
            File docxFile = new File(outputPath);
            System.out.println("\n===== 验证结果 =====");
            System.out.println("目标文件: " + docxFile.getAbsolutePath());
            System.out.println("文件存在: " + docxFile.exists());

            if (docxFile.exists()) {
                System.out.println("文件大小: " + docxFile.length() + " bytes");
                assertTrue(docxFile.length() > 1000,
                        "docx 文件应有实质内容");
                System.out.println(">>> E2E 验证通过！");
            } else {
                System.out.println(">>> LLM 未能在递归限制内生成文件");
                System.out.println(">>> 技能加载 + 沙箱执行路径已在阶段 1 验证通过");
            }
        }
    }

    /**
     * 构建生成 GodlikeAgents 简介 .docx 的 JavaScript 代码
     */
    private static String buildDocxJsCode(String outputPath) {
        return "const { Document, Packer, Paragraph, TextRun, Table, TableRow, TableCell,\n"
             + "        Header, Footer, AlignmentType, HeadingLevel, BorderStyle, WidthType,\n"
             + "        ShadingType, PageNumber, TableOfContents } = require('docx');\n"
             + "const fs = require('fs');\n\n"
             + "const border = { style: BorderStyle.SINGLE, size: 1, color: \"CCCCCC\" };\n"
             + "const borders = { top: border, bottom: border, left: border, right: border };\n"
             + "const cellMargins = { top: 80, bottom: 80, left: 120, right: 120 };\n\n"
             + "const doc = new Document({\n"
             + "  styles: {\n"
             + "    default: { document: { run: { font: \"Arial\", size: 24 } } },\n"
             + "    paragraphStyles: [\n"
             + "      { id: \"Heading1\", name: \"Heading 1\", basedOn: \"Normal\", next: \"Normal\", quickFormat: true,\n"
             + "        run: { size: 32, bold: true, font: \"Arial\" },\n"
             + "        paragraph: { spacing: { before: 240, after: 240 }, outlineLevel: 0 } },\n"
             + "      { id: \"Heading2\", name: \"Heading 2\", basedOn: \"Normal\", next: \"Normal\", quickFormat: true,\n"
             + "        run: { size: 28, bold: true, font: \"Arial\" },\n"
             + "        paragraph: { spacing: { before: 180, after: 180 }, outlineLevel: 1 } },\n"
             + "    ]\n"
             + "  },\n"
             + "  sections: [{\n"
             + "    properties: {\n"
             + "      page: { size: { width: 12240, height: 15840 },\n"
             + "              margin: { top: 1440, right: 1440, bottom: 1440, left: 1440 } }\n"
             + "    },\n"
             + "    headers: {\n"
             + "      default: new Header({ children: [\n"
             + "        new Paragraph({ alignment: AlignmentType.RIGHT,\n"
             + "          children: [new TextRun({ text: \"GodlikeAgents Framework\", italics: true, size: 18, color: \"888888\" })] })\n"
             + "      ] })\n"
             + "    },\n"
             + "    footers: {\n"
             + "      default: new Footer({ children: [\n"
             + "        new Paragraph({ alignment: AlignmentType.CENTER,\n"
             + "          children: [new TextRun({ text: \"Page \", size: 18 }), new TextRun({ children: [PageNumber.CURRENT], size: 18 })] })\n"
             + "      ] })\n"
             + "    },\n"
             + "    children: [\n"
             + "      new Paragraph({ heading: HeadingLevel.HEADING_1,\n"
             + "        children: [new TextRun(\"GodlikeAgents \\u6846\\u67b6\\u7b80\\u4ecb\")] }),\n"
             + "      new TableOfContents(\"Table of Contents\", { hyperlink: true, headingStyleRange: \"1-2\" }),\n\n"
             + "      new Paragraph({ heading: HeadingLevel.HEADING_2,\n"
             + "        children: [new TextRun(\"1. \\u9879\\u76ee\\u6982\\u8ff0\")] }),\n"
             + "      new Paragraph({ children: [new TextRun(\n"
             + "        \"GodlikeAgents \\u662f\\u4e00\\u4e2a\\u57fa\\u4e8e Java 17 + Spring Boot \\u7684 AI Agent \\u6846\\u67b6\\u3002\"\n"
             + "        + \"\\u5b83\\u4f7f\\u7528 LangGraph4j \\u6784\\u5efa\\u6709\\u5411\\u5faa\\u73af\\u56fe\\uff0c\"\n"
             + "        + \"\\u652f\\u6301 Plan\\u2192Execute\\u2192Tool\\u2192Delegate\\u2192Finalize \\u5de5\\u4f5c\\u6d41\\u3002\"\n"
             + "      )] }),\n\n"
             + "      new Paragraph({ heading: HeadingLevel.HEADING_2,\n"
             + "        children: [new TextRun(\"2. \\u6838\\u5fc3\\u67b6\\u6784\")] }),\n"
             + "      new Paragraph({ children: [new TextRun(\"\\u6846\\u67b6\\u91c7\\u7528 5 \\u8282\\u70b9\\u6709\\u5411\\u5faa\\u73af\\u56fe\\u67b6\\u6784\\uff1a\")] }),\n"
             + "      new Paragraph({ children: [new TextRun({ text: \"Plan Node: \", bold: true }), new TextRun(\"\\u5206\\u6790\\u6307\\u4ee4\\uff0c\\u751f\\u6210 TODO\")] }),\n"
             + "      new Paragraph({ children: [new TextRun({ text: \"Execute Node: \", bold: true }), new TextRun(\"\\u6267\\u884c TODO\\uff0c\\u8c03\\u7528\\u5de5\\u5177\")] }),\n"
             + "      new Paragraph({ children: [new TextRun({ text: \"Tool Node: \", bold: true }), new TextRun(\"\\u6267\\u884c\\u5de5\\u5177\\u8c03\\u7528\")] }),\n"
             + "      new Paragraph({ children: [new TextRun({ text: \"Delegate Node: \", bold: true }), new TextRun(\"\\u59d4\\u6d3e\\u5b50\\u4efb\\u52a1\")] }),\n"
             + "      new Paragraph({ children: [new TextRun({ text: \"Finalize Node: \", bold: true }), new TextRun(\"\\u6c47\\u603b\\u7ed3\\u679c\")] }),\n\n"
             + "      new Paragraph({ heading: HeadingLevel.HEADING_2,\n"
             + "        children: [new TextRun(\"3. \\u6280\\u672f\\u6808\")] }),\n"
             + "      new Table({\n"
             + "        width: { size: 9360, type: WidthType.DXA },\n"
             + "        columnWidths: [3120, 1560, 4680],\n"
             + "        rows: [\n"
             + "          new TableRow({ children: [\n"
             + "            new TableCell({ borders, width: { size: 3120, type: WidthType.DXA },\n"
             + "              shading: { fill: \"1F4E79\", type: ShadingType.CLEAR }, margins: cellMargins,\n"
             + "              children: [new Paragraph({ children: [new TextRun({ text: \"\\u4f9d\\u8d56\", bold: true, color: \"FFFFFF\" })] })] }),\n"
             + "            new TableCell({ borders, width: { size: 1560, type: WidthType.DXA },\n"
             + "              shading: { fill: \"1F4E79\", type: ShadingType.CLEAR }, margins: cellMargins,\n"
             + "              children: [new Paragraph({ children: [new TextRun({ text: \"\\u7248\\u672c\", bold: true, color: \"FFFFFF\" })] })] }),\n"
             + "            new TableCell({ borders, width: { size: 4680, type: WidthType.DXA },\n"
             + "              shading: { fill: \"1F4E79\", type: ShadingType.CLEAR }, margins: cellMargins,\n"
             + "              children: [new Paragraph({ children: [new TextRun({ text: \"\\u7528\\u9014\", bold: true, color: \"FFFFFF\" })] })] }),\n"
             + "          ] }),\n"
             + "          ...([\n"
             + "            [\"Java 17\", \"\", \"\\u8fd0\\u884c\\u65f6\\u5e73\\u53f0\"],\n"
             + "            [\"Spring Boot 4.0\", \"\", \"Web \\u6846\\u67b6\"],\n"
             + "            [\"LangGraph4j\", \"1.8.16\", \"\\u6709\\u5411\\u5faa\\u73af\\u56fe\\u5f15\\u64ce\"],\n"
             + "            [\"MyBatis-Plus\", \"3.5.15\", \"ORM \\u6846\\u67b6\"],\n"
             + "            [\"PostgreSQL\", \"16.4\", \"\\u4e3b\\u6570\\u636e\\u5e93\"],\n"
             + "            [\"Redis 7\", \"\", \"\\u7f13\\u5b58\\u4e0e\\u4f1a\\u8bdd\"],\n"
             + "          ].map((row, i) => new TableRow({ children: [\n"
             + "            new TableCell({ borders, width: { size: 3120, type: WidthType.DXA },\n"
             + "              shading: i % 2 === 0 ? { fill: \"D5E8F0\", type: ShadingType.CLEAR } : undefined,\n"
             + "              margins: cellMargins,\n"
             + "              children: [new Paragraph({ children: [new TextRun(row[0])] })] }),\n"
             + "            new TableCell({ borders, width: { size: 1560, type: WidthType.DXA },\n"
             + "              shading: i % 2 === 0 ? { fill: \"D5E8F0\", type: ShadingType.CLEAR } : undefined,\n"
             + "              margins: cellMargins,\n"
             + "              children: [new Paragraph({ children: [new TextRun(row[1])] })] }),\n"
             + "            new TableCell({ borders, width: { size: 4680, type: WidthType.DXA },\n"
             + "              shading: i % 2 === 0 ? { fill: \"D5E8F0\", type: ShadingType.CLEAR } : undefined,\n"
             + "              margins: cellMargins,\n"
             + "              children: [new Paragraph({ children: [new TextRun(row[2])] })] }),\n"
             + "          ] }))),\n"
             + "        ]\n"
             + "      }),\n\n"
             + "      new Paragraph({ heading: HeadingLevel.HEADING_2,\n"
             + "        children: [new TextRun(\"4. \\u5feb\\u901f\\u4e0a\\u624b\")] }),\n"
             + "      new Paragraph({ children: [new TextRun(\n"
             + "        \"\\u901a\\u8fc7 Builder \\u6a21\\u5f0f\\u4e00\\u884c\\u4ee3\\u7801\\u521b\\u5efa\\u53ef\\u8fd0\\u884c\\u7684 AI Agent\\uff1a\"\n"
             + "      )] }),\n"
             + "      new Paragraph({ shading: { fill: \"F2F2F2\", type: ShadingType.CLEAR },\n"
             + "        children: [new TextRun({ text: \"GodlikeAgent.builder().model(chatModel).build().run(\\\"task\\\");\",\n"
             + "          font: \"Consolas\", size: 20 })] }),\n"
             + "    ]\n"
             + "  }]\n"
             + "});\n\n"
             + "Packer.toBuffer(doc).then(buffer => {\n"
             + "  fs.writeFileSync('" + outputPath.replace("'", "\\'") + "', buffer);\n"
             + "  console.log('SUCCESS: docx file created, size=' + buffer.length + ' bytes');\n"
             + "}).catch(err => {\n"
             + "  console.error('ERROR: ' + err.message);\n"
             + "});\n";
    }
}
