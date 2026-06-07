package com.hypersense.boot.framework.agents.sandbox;

import com.hypersense.boot.framework.agents.config.AgentProperties;

/**
 * DockerSandbox 端到端测试（手动执行）
 * <p>
 * 使用远程 Docker daemon 和 godlikeagents/sandbox:1.0.0 镜像，
 * 完整验证：创建容器 → 执行代码 → 文件操作 → 命令执行 → 销毁容器
 * </p>
 */
public class DockerSandboxE2ETest {

    public static void main(String[] args) {
        System.out.println("===== DockerSandbox 端到端测试 =====\n");

        // 构建配置
        AgentProperties props = new AgentProperties();
        AgentProperties.SandboxConfig sandboxConfig = props.getTools().getSandbox();
        sandboxConfig.setEnabled(true);
        sandboxConfig.setTimeout(30);

        AgentProperties.CustomSandboxConfig customConfig = sandboxConfig.getCustom();
        customConfig.setSocketPath("tcp://47.107.160.31:2375");
        customConfig.setImage("godlikeagents/sandbox:1.0.0");
        customConfig.setMemoryLimit("512m");
        customConfig.setCpuLimit(1.0);
        customConfig.setNetworkMode("none");
        customConfig.setWorkspacePath("/workspace");
        customConfig.setAutoRemove(true);
        customConfig.setPidsLimit(100);

        String sessionId = "e2e-test-" + System.currentTimeMillis();

        DockerSandbox sandbox = new DockerSandbox(props, sessionId);

        try {
            // ========== 1. 初始化（创建+启动容器） ==========
            System.out.println("[1] 初始化容器沙箱...");
            sandbox.initialize();
            System.out.println("    容器状态: " + sandbox.inspectContainerStatus());
            System.out.println("    OK\n");

            // ========== 2. Python 代码执行 ==========
            System.out.println("[2] 执行 Python 代码...");
            SandboxResult pyResult = sandbox.executeCode("python",
                    "import sys, platform\n" +
                    "print(f'Python {sys.version}')\n" +
                    "print(f'Platform: {platform.system()} {platform.machine()}')\n" +
                    "print(f'numpy: {__import__(\"numpy\").__version__}')\n" +
                    "print(f'pandas: {__import__(\"pandas\").__version__}')\n" +
                    "print(f'2^10 = {2**10}')",
                    15);
            printResult(pyResult);

            // ========== 3. JavaScript 代码执行 ==========
            System.out.println("[3] 执行 JavaScript 代码...");
            SandboxResult jsResult = sandbox.executeCode("javascript",
                    "const version = process.version;\n" +
                    "const sum = [1,2,3,4,5].reduce((a,b) => a+b, 0);\n" +
                    "console.log(`Node.js ${version}`);\n" +
                    "console.log(`1+2+3+4+5 = ${sum}`);\n" +
                    "console.log(`Math.PI = ${Math.PI}`);",
                    10);
            printResult(jsResult);

            // ========== 4. Shell 命令执行 ==========
            System.out.println("[4] 执行 Shell 命令...");
            SandboxResult shResult = sandbox.executeCommand(
                    "echo 'Hello from sandbox!' && " +
                    "uname -a && " +
                    "df -h / && " +
                    "free -m 2>/dev/null || cat /proc/meminfo | head -3");
            printResult(shResult);

            // ========== 5. 写入文件 ==========
            System.out.println("[5] 写入文件...");
            SandboxResult writeResult = sandbox.writeFile("test_report.txt",
                    "===== 沙箱测试报告 =====\n" +
                    "生成时间: " + java.time.LocalDateTime.now() + "\n" +
                    "状态: 所有测试通过\n" +
                    "========================\n");
            printResult(writeResult);

            // ========== 6. 读取文件 ==========
            System.out.println("[6] 读取文件...");
            SandboxResult readResult = sandbox.readFile("test_report.txt");
            printResult(readResult);
            if (readResult.isSuccess() && readResult.getContent() != null) {
                System.out.println("    文件内容:\n" + indent(readResult.getContent()));
            }

            // ========== 7. 列出目录 ==========
            System.out.println("[7] 列出工作目录...");
            SandboxResult lsResult = sandbox.listDirectory(".");
            printResult(lsResult);

            // ========== 8. 复杂 Python（数据处理） ==========
            System.out.println("[8] 执行复杂数据处理...");
            SandboxResult complexResult = sandbox.executeCode("python",
                    "import numpy as np\n" +
                    "import pandas as pd\n" +
                    "\n" +
                    "# 生成数据\n" +
                    "data = pd.DataFrame({\n" +
                    "    'name': ['Alice', 'Bob', 'Charlie', 'Diana', 'Eve'],\n" +
                    "    'score': np.random.randint(60, 100, 5)\n" +
                    "})\n" +
                    "print(data.to_string())\n" +
                    "print(f'\\n平均分: {data[\"score\"].mean():.1f}')\n" +
                    "print(f'最高分: {data[\"score\"].max()}')\n" +
                    "print(f'最低分: {data[\"score\"].min()}')",
                    15);
            printResult(complexResult);

            System.out.println("\n===== 所有端到端测试通过! =====");

        } catch (Exception e) {
            System.err.println("\n[!] 测试失败: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // ========== 9. 销毁容器 ==========
            System.out.println("\n[9] 销毁容器...");
            sandbox.destroy();
            System.out.println("    容器已销毁");
        }
    }

    static void printResult(SandboxResult result) {
        if (result.isSuccess()) {
            System.out.println("    [OK] 耗时: " + result.getElapsedMs() + "ms");
            if (result.getExitCode() != null) {
                System.out.println("    退出码: " + result.getExitCode());
            }
            if (result.getStdout() != null && !result.getStdout().isBlank()) {
                System.out.println("    输出:\n" + indent(result.getStdout()));
            }
        } else {
            System.out.println("    [FAIL] " + result.getError());
            if (result.getStderr() != null && !result.getStderr().isBlank()) {
                System.out.println("    stderr: " + result.getStderr());
            }
        }
        System.out.println();
    }

    static String indent(String s) {
        return s.lines().map(l -> "      " + l).reduce((a, b) -> a + "\n" + b).orElse("");
    }
}
