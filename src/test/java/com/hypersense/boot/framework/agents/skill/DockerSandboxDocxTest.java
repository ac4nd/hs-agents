package com.hypersense.boot.framework.agents.skill;

import com.hypersense.boot.framework.agents.config.AgentProperties;
import com.hypersense.boot.framework.agents.sandbox.DockerSandbox;
import com.hypersense.boot.framework.agents.sandbox.SandboxResult;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Docker 沙箱 + docx 技能 E2E 测试（手动执行，非 JUnit）
 * <p>
 * 使用远程 Docker daemon (47.107.160.31:2375) 和 godlikeagents/sandbox:1.0.0 镜像，
 * 验证完整闭环：SkillRegistry 扫描 → SkillLoadTool 加载 → Docker 容器内执行 JS → 生成 .docx 文件
 * <p>
 * 前置条件：
 * <ul>
 *   <li>远程 Docker daemon 可访问（47.107.160.31:2375）</li>
 *   <li>镜像 godlikeagents/sandbox:1.0.0 已包含 nodejs/npm + docx npm 包</li>
 * </ul>
 */
public class DockerSandboxDocxTest {

    private static final String SKILLS_DIR = "D:/project/myproject/ac4nd/skills";

    public static void main(String[] args) throws Exception {
        System.out.println("===== Docker 沙箱 + docx 技能 E2E 测试 =====\n");

        // ========== 1. 技能加载验证 ==========
        System.out.println("[1] 加载 docx 技能...");
        SkillRegistry registry = new SkillRegistry();
        registry.scan(SKILLS_DIR);
        if (registry.isEmpty()) {
            System.err.println("    [FAIL] 未发现任何技能");
            return;
        }
        System.out.println("    技能数量: " + registry.getAll().size());
        System.out.println("    OK\n");

        // ========== 2. SkillLoadTool 加载完整说明 ==========
        System.out.println("[2] 加载 docx 技能完整内容...");
        SkillLoadTool tool = new SkillLoadTool(registry);
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> loadResult = (java.util.Map<String, Object>) tool.execute(
                java.util.Map.of("skill_name", "docx"));
        if (!Boolean.TRUE.equals(loadResult.get("success"))) {
            System.err.println("    [FAIL] skill_load 失败: " + loadResult.get("error"));
            return;
        }
        String skillContent = (String) loadResult.get("content");
        System.out.println("    内容长度: " + skillContent.length() + " 字符");
        System.out.println("    OK\n");

        // ========== 3. 创建 Docker 沙箱 ==========
        System.out.println("[3] 创建 Docker 容器沙箱...");
        AgentProperties props = new AgentProperties();
        AgentProperties.SandboxConfig sandboxConfig = props.getTools().getSandbox();
        sandboxConfig.setEnabled(true);
        sandboxConfig.setTimeout(120);

        AgentProperties.CustomSandboxConfig customConfig = sandboxConfig.getCustom();
        customConfig.setSocketPath("tcp://47.107.160.31:2375");
        customConfig.setImage("godlikeagents/sandbox:1.0.0");
        customConfig.setMemoryLimit("512m");
        customConfig.setCpuLimit(1.0);
        customConfig.setNetworkMode("none");
        customConfig.setWorkspacePath("/workspace");
        customConfig.setVolumeBasePath("/opt/sandbox-output");
        customConfig.setAutoRemove(true);
        customConfig.setPidsLimit(100);

        String sessionId = "docx-test-" + System.currentTimeMillis();
        DockerSandbox sandbox = new DockerSandbox(props, sessionId);

        try {
            sandbox.initialize();
            System.out.println("    容器已启动: agent-sandbox-" + sessionId);
            System.out.println("    OK\n");

            // ========== 4. 验证 docx npm 包可用 ==========
            System.out.println("[4] 验证 docx npm 包...");
            SandboxResult verifyResult = sandbox.executeCommand("node -e \"const d = require('docx'); console.log('docx OK, version=' + Object.keys(d).length + ' exports')\"");
            if (verifyResult.isSuccess() && verifyResult.getStdout() != null
                    && verifyResult.getStdout().contains("docx OK")) {
                System.out.println("    " + verifyResult.getStdout().trim());
                System.out.println("    OK\n");
            } else {
                System.err.println("    [FAIL] docx npm 包不可用");
                System.err.println("    stderr: " + verifyResult.getStderr());
                System.err.println("    请确认镜像已包含 docx: docker/sandbox/Dockerfile 中 npm install -g docx");
                return;
            }

            // ========== 5. 执行 JS 生成 .docx ==========
            System.out.println("[5] 执行 JavaScript 生成 .docx 文件...");
            String jsCode = buildDocxJsCode("GodlikeAgents_Docker.docx");

            // 写 JS 文件到容器工作目录
            SandboxResult writeResult = sandbox.writeFile("generate_docx.js", jsCode);
            System.out.println("    写入 JS 文件: " + (writeResult.isSuccess() ? "OK" : "FAIL"));

            // 执行
            SandboxResult execResult = sandbox.executeCommand("cd /workspace && node generate_docx.js");
            System.out.println("    执行结果: success=" + execResult.isSuccess()
                    + ", exitCode=" + execResult.getExitCode());
            if (execResult.getStdout() != null && !execResult.getStdout().isBlank()) {
                System.out.println("    stdout: " + execResult.getStdout().trim());
            }
            if (execResult.getStderr() != null && !execResult.getStderr().isBlank()) {
                System.out.println("    stderr: " + execResult.getStderr().trim());
            }

            if (!execResult.isSuccess()) {
                System.err.println("    [FAIL] JS 执行失败");
                return;
            }
            System.out.println("    OK\n");

            // ========== 6. 验证容器工作目录中的 .docx 文件 ==========
            System.out.println("[6] 验证容器工作目录中的文件...");
            SandboxResult lsResult = sandbox.listDirectory(".");
            System.out.println("    容器文件列表:");
            if (lsResult.getStdout() != null) {
                System.out.println("    " + lsResult.getStdout().trim());
            }

            boolean containerHasFile = lsResult.isSuccess()
                    && lsResult.getStdout() != null
                    && lsResult.getStdout().contains("GodlikeAgents_Docker.docx");
            System.out.println("    .docx 文件存在: " + containerHasFile);

            if (!containerHasFile) {
                System.err.println("    [FAIL] 容器工作目录中未找到 .docx 文件");
                return;
            }

            // 获取文件大小
            SandboxResult sizeResult = sandbox.executeCommand("ls -l GodlikeAgents_Docker.docx | awk '{print $5}'");
            if (sizeResult.isSuccess() && sizeResult.getStdout() != null) {
                System.out.println("    文件大小: " + sizeResult.getStdout().trim() + " bytes");
            }
            System.out.println("    OK\n");

            System.out.println("===== Docker 沙箱 docx 测试通过 =====");

        } catch (Exception e) {
            System.err.println("\n[!] 测试失败: " + e.getMessage());
            e.printStackTrace();
        } finally {
            System.out.println("\n[Cleanup] 销毁容器...");
            sandbox.destroy();
            System.out.println("    容器已销毁");
        }
    }

    /**
     * 构建生成 .docx 的 JS 代码（容器内路径）
     */
    private static String buildDocxJsCode(String filename) {
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
             + "          children: [new TextRun({ text: \"GodlikeAgents - Docker Sandbox\", italics: true, size: 18, color: \"888888\" })] })\n"
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
             + "      new TableOfContents(\"Table of Contents\", { hyperlink: true, headingStyleRange: \"1-2\" }),\n"
             + "      new Paragraph({ heading: HeadingLevel.HEADING_2,\n"
             + "        children: [new TextRun(\"1. \\u9879\\u76ee\\u6982\\u8ff0\")] }),\n"
             + "      new Paragraph({ children: [new TextRun(\n"
             + "        \"GodlikeAgents \\u662f\\u4e00\\u4e2a\\u57fa\\u4e8e Java 17 + Spring Boot \\u7684 AI Agent \\u6846\\u67b6\\u3002\"\n"
             + "        + \"\\u5b83\\u4f7f\\u7528 LangGraph4j \\u6784\\u5efa\\u6709\\u5411\\u5faa\\u73af\\u56fe\\uff0c\"\n"
             + "        + \"\\u652f\\u6301 Plan\\u2192Execute\\u2192Tool\\u2192Delegate\\u2192Finalize \\u5de5\\u4f5c\\u6d41\\u3002\"\n"
             + "      )] }),\n"
             + "      new Paragraph({ heading: HeadingLevel.HEADING_2,\n"
             + "        children: [new TextRun(\"2. \\u6838\\u5fc3\\u67b6\\u6784\")] }),\n"
             + "      new Paragraph({ children: [new TextRun(\"\\u6846\\u67b6\\u91c7\\u7528 5 \\u8282\\u70b9\\u6709\\u5411\\u5faa\\u73af\\u56fe\\u67b6\\u6784\\uff1a\")] }),\n"
             + "      new Paragraph({ children: [new TextRun({ text: \"Plan Node: \", bold: true }), new TextRun(\"\\u5206\\u6790\\u6307\\u4ee4\")] }),\n"
             + "      new Paragraph({ children: [new TextRun({ text: \"Execute Node: \", bold: true }), new TextRun(\"\\u6267\\u884c TODO\")] }),\n"
             + "      new Paragraph({ children: [new TextRun({ text: \"Tool Node: \", bold: true }), new TextRun(\"\\u6267\\u884c\\u5de5\\u5177\")] }),\n"
             + "      new Paragraph({ children: [new TextRun({ text: \"Delegate Node: \", bold: true }), new TextRun(\"\\u59d4\\u6d3e\\u5b50\\u4efb\\u52a1\")] }),\n"
             + "      new Paragraph({ children: [new TextRun({ text: \"Finalize Node: \", bold: true }), new TextRun(\"\\u6c47\\u603b\\u7ed3\\u679c\")] }),\n"
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
             + "            [\"LangGraph4j\", \"1.8.16\", \"\\u56fe\\u5f15\\u64ce\"],\n"
             + "            [\"MyBatis-Plus\", \"3.5.15\", \"ORM\"],\n"
             + "            [\"PostgreSQL\", \"16.4\", \"\\u6570\\u636e\\u5e93\"],\n"
             + "            [\"Redis 7\", \"\", \"\\u7f13\\u5b58\"],\n"
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
             + "      }),\n"
             + "      new Paragraph({ heading: HeadingLevel.HEADING_2,\n"
             + "        children: [new TextRun(\"4. \\u5feb\\u901f\\u4e0a\\u624b\")] }),\n"
             + "      new Paragraph({ shading: { fill: \"F2F2F2\", type: ShadingType.CLEAR },\n"
             + "        children: [new TextRun({ text: \"GodlikeAgent.builder().model(chatModel).build().run(\\\"task\\\");\",\n"
             + "          font: \"Consolas\", size: 20 })] }),\n"
             + "    ]\n"
             + "  }]\n"
             + "});\n\n"
             + "Packer.toBuffer(doc).then(buffer => {\n"
             + "  fs.writeFileSync('" + filename + "', buffer);\n"
             + "  console.log('SUCCESS: docx file created, size=' + buffer.length + ' bytes');\n"
             + "}).catch(err => {\n"
             + "  console.error('ERROR: ' + err.message);\n"
             + "});\n";
    }
}
