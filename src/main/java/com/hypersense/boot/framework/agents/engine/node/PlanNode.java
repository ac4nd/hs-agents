package com.hypersense.boot.framework.agents.engine.node;

import com.hypersense.boot.framework.agents.engine.SubAgentEventBus;
import com.hypersense.boot.framework.agents.enums.AgentEventType;
import com.hypersense.boot.framework.agents.config.AgentProperties;
import com.hypersense.boot.framework.agents.hitl.DecisionPoint;
import com.hypersense.boot.framework.agents.hitl.HitlDecision;
import com.hypersense.boot.framework.agents.hitl.HitlGateChecker;
import com.hypersense.boot.framework.agents.model.AgentEvent;
import com.hypersense.boot.framework.agents.model.DeepAgentState;
import com.hypersense.boot.framework.agents.model.TodoItem;
import com.hypersense.boot.framework.agents.enums.TodoStatus;
import com.hypersense.boot.framework.agents.serializer.AttachmentContext;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 规划节点
 * <p>
 * 调用 LLM 分析当前任务状态，生成或更新 TODO 计划。
 * 这是 DeepAgents「显式规划」能力的核心实现。
 * </p>
 * <p>
 * 流式事件协议：
 * <ul>
 *   <li>开始 → {@link AgentEventType#NODE_EXECUTION}</li>
 *   <li>完成 → {@link AgentEventType#PLAN_CREATED}（data 带 todos）</li>
 *   <li>智能判断需确认 → {@link AgentEventType#INTERRUPT}（data 带 HitlDecision）</li>
 * </ul>
 * </p>
 *
 * @author Claude
 * @since 2026/5/15
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlanNode implements NodeAction<DeepAgentState> {

    private final ChatModel chatModel;
    private final HitlGateChecker hitlGateChecker;
    private final AgentProperties agentProperties;
    private final AttachmentContext attachmentContext;

    /** 连续解析失败的最大容忍次数，超过后将所有 PENDING TODO 标记为 FAILED 以打破循环 */
    private static final int MAX_PARSE_FAILURES = 3;

    /** TODO 校验失败后给 LLM 的重试上限（含首次规划，最多 2 次重新规划） */
    private static final int MAX_TODO_VALIDATION_RETRIES = 2;

    /** plan 周期上限：允许 1 次规划 + 1 次复用，第 3 次进入仍需重新规划即视为漂移强制终止 */
    private static final int MAX_PLAN_CYCLES = 2;

    /**
     * DIRECT_REPLY 短路时，编码预设回复内容的标记 token。
     * <p>
     * 用一对 token 把 LLM 在 plan 阶段生成的回复正文包起来嵌入 TODO 描述，
     * 便于 ToolNode 的 function-call LLM 识别"原样使用"的内容范围，避免改写或总结。
     * </p>
     */
    private static final String DIRECT_REPLY_PRESET_MARKER = "<PRESET_REPLY_CONTENT>";

    private static final String PLAN_SYSTEM_PROMPT = """
            你是任务规划专家。先判断用户指令的复杂度，再决定响应方式。

            【上下文区块说明】（重要！）
            用户指令中可能包含以下结构化区块，全部是已发生的事实，必须当作 ground truth 使用：
            - 【历史摘要】：早期对话的要点压缩，可直接引用，不需要再问用户。
            - 【最近对话】：最近几轮的 User/Assistant 完整原文。其中 Assistant 的内容是上一轮 Agent 实际给出的回复。
            - 【当前用户指令】：本轮用户的最新输入，可能引用"上面/上面那个/刚才的内容"，均指代【历史摘要】或【最近对话】中已存在的内容。
            当用户指令出现指代词（如"上面那个"、"刚才的信息"、"它"、"这个"）时，必须先在【最近对话】中查找指代对象，
            找到就直接基于该内容制定计划，禁止把"向用户确认信息来源"作为 TODO。
            只有当指代对象在历史区块中确实找不到时，才允许生成"向用户确认"类 TODO。

            【必须走 TODO 计划的场景】满足任一即必须生成 TODO（不能 DIRECT_REPLY）：
            1. 需要实时/外部信息：天气、新闻、股价、汇率、赛事比分、最新动态、当前时间相关的查询
            2. 需要调用工具：网络搜索、文件读写、代码执行、数据库查询、API 调用
            3. 需要多步骤协作、跨系统操作、生成产物（文件/代码/报告）
            4. 用户明确要求"查"、"搜"、"获取"、"打开"、"生成"、"创建"等动作动词
            对于上述场景，TODO 描述要明确包含工具使用意图，例如「使用网络搜索查询 X」。
            注意：若所需内容已经在【历史摘要】或【最近对话】中，无需再调工具获取，
            应直接基于已有内容生成后续步骤（如整理/保存/转换格式等）。

            【可以直接 DIRECT_REPLY 的场景】（仅限以下情形）：
            - 问候、闲聊、身份介绍（"你好"、"你是谁"、"谢谢"）
            - 用户已有信息范围内的纯知识问答（"什么是闭包"、"解释 Transformer 原理"）
            - 对历史对话的澄清/确认（"我刚才说的是…"、"明白了"）
            - 基于历史内容的小幅补充说明，无需新外部信息或工具调用
            注：DIRECT_REPLY 的回复内容会由后端构造为 reply_text 工具调用 TODO 走 ToolNode 执行，
            使所有输出都经工具留痕、可审计。LLM 只需关注回复正文质量。

            【输出格式】严格二选一，不要任何额外解释、markdown 包裹：
            - DIRECT_REPLY: <自然回复正文>
            - TODO: <任务描述>（可多行）

            【示例 1】用户指令="你好"
            输出：DIRECT_REPLY: 你好！很高兴见到你。有什么我可以帮你的吗？

            【示例 2】用户指令="解释一下什么是闭包"
            输出：DIRECT_REPLY: 闭包是指……（自然语言解释）

            【示例 3】用户指令="查询福州今天的天气"
            输出：
            TODO: 使用 internet_search 工具查询福州今日天气
            TODO: 使用 reply_text 工具汇总天气信息回复用户

            【示例 4】用户指令="最新 AI 新闻有哪些"
            输出：
            TODO: 使用 internet_search 工具查询最新 AI 新闻
            TODO: 使用 reply_text 工具整理新闻要点回复用户

            【示例 5】用户指令="帮我生成一个 Python 数据清洗脚本并保存到 data_clean.py"
            输出：
            TODO: 设计数据清洗逻辑
            TODO: 使用 file_write 工具编写 Python 脚本并保存为 data_clean.py

            工具使用规则（强制）：
            1. 每个 TODO 必须明确引用一个工具（file_write / file_read / internet_search / sandbox / reply_text / delegate）
            2. 禁止生成"直接保存/直接生成/直接执行"类 TODO，必须改为"使用 file_write 工具保存"
            3. 工具名必须在系统内置工具集中，引用未注册工具会被拒绝
            4. 禁止在 TODO 描述中包含操作结果（如"已保存"），结果由工具实际执行后产生
            5. 问候、知识问答、解释、总结类需求必须使用 reply_text 工具
            6. file_read 类 TODO 的目标文件名必须来自上下文中真实存在的文件（如用户明确上传/提及、或前序步骤实际生成的产物）；
               严禁凭空假设工作空间中存在某个文件（例如未经用户确认就规划"读取 prototype.html / protype.html / template.html"）。
               若不确定文件是否存在，应改为直接基于用户需求和【最近对话】内容原创生成，而不是 file_read。
            7. 当用户请求是"把上面那个设计/方案/代码落地为 HTML/文件并保存"时，必须规划 file_write TODO，
               由 LLM 原创/汇总生成完整 content，禁止以"等待前序产出"为由跳过。
            8. file_write TODO 中禁止出现任何路径前缀（如 /home/user/、/tmp/、/var/workspace/、C:\\、~/），
               只允许描述"保存为 xxx.html"（仅文件名）。系统会自动写入沙箱工作目录的 uploads/ 子目录。
               违反该规则的 TODO 会被后端拒绝。
            9. 【原创产物即时落盘原则】凡是要生成 HTML/CSS/JS/代码/文档/报告等产物的请求，
               必须在【同一轮 TODO 计划内】安排 file_write TODO 并由 LLM 在该步骤直接产出完整 content 落盘。
               严禁先用 reply_text 把"设计方案/代码大纲"输出给用户、再等下一轮才保存——会话记忆不可靠，跨轮必丢内容。
               正确做法：TODO1=使用 file_write 工具原创生成 xxx 并保存为 xxx.html；TODO2=使用 reply_text 工具告知用户已保存。
               禁止把"先生成内容文本回复用户"作为单独 TODO。
            10. 【指令模糊必澄清原则】出现以下任一情形时，必须只生成一个 reply_text 工具的 CLARIFY TODO（replyType=CLARIFY），
                询问清楚后再进入实质执行，禁止自行编造或猜测：
                a. 用户要【修改/覆盖】某个【已存在】文件但工作空间有多个候选文件且指代不明
                   （如"修改刚才那个"但 state.files 中有 2 个以上文件）；
                b. 用户指令出现"按上面风格/类似刚才那个/接着做"等指代词，但【最近对话】和【上下文区块】中都找不到明确指代对象；
                c. 用户要求修改一个工作空间中【不存在】的文件，且未说明是否新建；
                d. 用户要求读取/查询明确不存在的外部资源（如某未上传文件、未指定 URL）。
                【注意：以下情形【不属于】模糊指令，禁止 CLARIFY】
                - 用户要做【原创设计/生成/创建】类请求（如"设计一个宠物领养页面"、"生成一个 Python 脚本"），
                  即使未指定文件名、未指定风格细节，也【必须直接执行】：由 LLM 自主命名 + 基于通用最佳实践产出，
                  系统底层有自动命名兜底。原创场景禁止以"未指定文件名/风格/配色"为由 CLARIFY。
                - 用户提到"当前设计方案/当前设计系统/当前风格"等指代【设计模式上下文】时，
                  该上下文已由后端通过 instructions 注入，LLM 必须直接从用户指令中查找，
                  找到就立即基于其执行，找不到再 CLARIFY。
            11. 【一次成型原则】LLM 在 file_write 步骤产出的 content 必须是【完整、可直接运行/使用的最终内容】，
                不允许先写"骨架版"再下一轮补充。若用户未提细节，按行业最佳实践 + 用户明确给出的约束补全。
            12. 【单文件原创原则】凡是「设计/创建/生成 HTML 页面或单个产物文件」类原创需求，
                必须只规划【恰好 1 个】file_write TODO 落盘整个产物。
                禁止把一个页面拆成多个 file_write TODO（如先生成 layout.html 再生成 components.html）。
                正确做法：1 个 file_write TODO 输出完整 HTML（含所有 section、样式、脚本）+ 1 个 reply_text TODO 告知用户。
                违反此规则的「拆分式多文件 TODO」会被后端拒绝。
            """;

    @Override
    public Map<String, Object> apply(DeepAgentState state) {
        log.info("PlanNode: 开始规划任务");
        emit(AgentEventType.NODE_EXECUTION, "规划节点开始", Map.of("node", "plan"));

        // === plan 周期计数（双保险防漂移，与 RouteAfterPlan 共享）===
        // 即使 plan_cycle_count 未在 SCHEMA 注册，langgraph4j AgentState 基于可变 Map，
        // 未声明 key 仍可直接覆盖写入；若被丢弃则保险 2（RouteAfterPlan 的 todos 兜底）仍生效。
        int planCycleCount;
        try {
            planCycleCount = state.<Integer>value(DeepAgentState.PLAN_CYCLE_COUNT).orElse(0) + 1;
        } catch (ClassCastException cce) {
            log.warn("PlanNode: plan_cycle_count 类型异常，回退为 1: {}", cce.getMessage());
            planCycleCount = 1;
        }
        log.info("PlanNode: 进入 plan 节点, plan_cycle_count={}", planCycleCount);

        // === 终止性短路 1（最优先，零 LLM 调用）：todos 全部 COMPLETED/FAILED → 直接 finalize ===
        // 这是修复「一次需求触发多次 plan 循环」的核心保险：一旦所有 TODO 都已结束，
        // 任何后续 LLM 调用都可能因为描述漂移导致 findExistingStatus 失败、COMPLETED 被重新标 PENDING。
        List<TodoItem> existingTodos = state.todos();
        if (existingTodos != null && !existingTodos.isEmpty()) {
            boolean allDone = existingTodos.stream().allMatch(t ->
                    t.getStatus() == TodoStatus.COMPLETED || t.getStatus() == TodoStatus.FAILED);
            if (allDone) {
                log.warn("PlanNode: 所有 TODO 已结束，强制跳过 LLM 重新规划，进入 finalize. planCycleCount={}",
                        planCycleCount);
                Map<String, Object> doneResult = new HashMap<>();
                doneResult.put(DeepAgentState.TODOS, existingTodos);
                doneResult.put(DeepAgentState.MESSAGES, AiMessage.from("所有任务已完成"));
                doneResult.put(DeepAgentState.PLAN_CYCLE_COUNT, planCycleCount);
                return doneResult;
            }
        }

        // === 终止性短路 2（防漂移保护）：plan 周期超过上限强制终止 ===
        // 正常流程 1 次规划即可走完全部 TODO；复用场景至多再加 1 次。
        // 第 3 次进入 plan 仍需 LLM 规划，意味着状态机已漂移，必须终止。
        if (planCycleCount > MAX_PLAN_CYCLES) {
            log.error("PlanNode: 检测到 plan 循环 {} 次，疑似异常，强制终止. todos={}",
                    planCycleCount, existingTodos);
            Map<String, Object> driftResult = new HashMap<>();
            if (existingTodos != null) {
                driftResult.put(DeepAgentState.TODOS, existingTodos);
            }
            driftResult.put(DeepAgentState.MESSAGES, AiMessage.from("已达到 plan 循环上限，终止"));
            driftResult.put(DeepAgentState.PLAN_CYCLE_COUNT, planCycleCount);
            return driftResult;
        }

        // 迭代上限保护：超过配置的 max-iterations 强制 finalize，避免 plan↔execute 空转
        int iteration = state.iterationCount();
        Integer maxIterCfg = agentProperties.getDeep().getMaxIterations();
        int maxIter = maxIterCfg != null && maxIterCfg > 0 ? maxIterCfg : 25;
        if (iteration >= maxIter) {
            log.warn("PlanNode: 迭代次数已达上限 {}，强制结束（保留现有 todos 状态进入 finalize）", maxIter);
            String msg = String.format("已达迭代上限（%d 次），任务中止以防无限循环。", maxIter);
            List<TodoItem> frozenTodos = state.todos().stream()
                    .map(t -> (t.getStatus() == TodoStatus.PENDING || t.getStatus() == TodoStatus.IN_PROGRESS)
                            ? TodoItem.builder().id(t.getId()).description(t.getDescription())
                            .status(TodoStatus.FAILED).result("迭代超限未执行")
                            .assignedAgent(t.getAssignedAgent()).updatedAt(LocalDateTime.now()).build()
                            : t)
                    .toList();
            Map<String, Object> iterResult = new HashMap<>();
            iterResult.put(DeepAgentState.TODOS, frozenTodos);
            iterResult.put(DeepAgentState.MESSAGES, AiMessage.from(msg));
            iterResult.put(DeepAgentState.PLAN_CYCLE_COUNT, planCycleCount);
            return iterResult;
        }

        // 短路：已有 todos 且至少一个处于 PENDING/IN_PROGRESS 时，跳过 LLM 重新规划，
        // 保留原 todos 让 RouteAfterPlan 路由到 execute 继续推进；
        // 避免 LLM 描述漂移（如"搜索"→"查询"）导致 findExistingStatus 精确匹配失败，
        // 已 COMPLETED 的 TODO 被当作新 PENDING 重新生成 → 死循环
        if (existingTodos != null && !existingTodos.isEmpty()) {
            boolean hasPending = existingTodos.stream().anyMatch(t ->
                    t.getStatus() == TodoStatus.PENDING || t.getStatus() == TodoStatus.IN_PROGRESS);
            if (hasPending) {
                log.info("PlanNode: 已有 {} 个 TODO 含未完成项，跳过 LLM 重新规划，继续推进执行. planCycleCount={}",
                        existingTodos.size(), planCycleCount);
                emit(AgentEventType.NODE_EXECUTION, "继续执行现有计划",
                        Map.of("node", "plan", "todos", existingTodos));
                Map<String, Object> pendingResult = new HashMap<>();
                pendingResult.put(DeepAgentState.TODOS, existingTodos);
                pendingResult.put(DeepAgentState.MESSAGES, AiMessage.from("继续执行现有计划"));
                pendingResult.put(DeepAgentState.PLAN_CYCLE_COUNT, planCycleCount);
                return pendingResult;
            }
        }

        // 构建提示上下文
        // MemoryMiddleware.before 通过 ThreadLocal 旁路注入记忆增强后的 instructions
        // （LangGraph4j 节点执行期间 state.data() 不可修改，无法直接 put）
        // ThreadLocal 清理由 wrapWithMemoryMiddleware 的 finally 块统一负责，避免节点异常时泄漏
        String memoryEnhanced = com.hypersense.boot.framework.agents.memory.MemoryMiddleware.ENHANCED_INSTRUCTIONS.get();
        String instructions = memoryEnhanced != null ? memoryEnhanced : state.instructions();
        String existingSummary = existingTodos.isEmpty()
                ? "（暂无计划）"
                : existingTodos.stream()
                .map(t -> String.format("- [%s] %s", t.getStatus().getLabel(), t.getDescription()))
                .collect(Collectors.joining("\n"));

        String userPrompt = String.format("""
                用户指令（含历史上下文区块，已是 ground truth，请直接基于其内容规划）：%s

                当前计划状态：
                %s

                请制定或更新执行计划。
                规则提醒：若用户指令引用了"上面/刚才/之前"的内容，且该内容出现在【历史摘要】或【最近对话】中，
                必须直接复用该内容规划后续步骤，禁止生成"向用户确认信息来源"类 TODO。
                """, instructions, existingSummary);

        // 调用 LLM 生成计划
        // 若本轮有图片附件且模型支持视觉，附加 ImageContent 让 LLM 能真正"看到"图片
        UserMessage planUserMessage = attachmentContext
                .buildMultimodal(state.sessionId(), userPrompt, state.attachmentPaths())
                .orElseGet(() -> UserMessage.from(userPrompt));
        List<ChatMessage> messages = List.of(
                SystemMessage.from(PLAN_SYSTEM_PROMPT),
                planUserMessage
        );

        ChatResponse response = attachmentContext.chatWithVisionFallback(
                chatModel, messages, state.sessionId(), userPrompt);
        String planText = response.aiMessage().text();
        log.debug("PlanNode: LLM 规划结果\n{}", planText);

        // 短路：LLM 直接回复简单对话（DIRECT_REPLY: 前缀）
        // 废除 direct 后的改造：不再让 LLM 直接产出文本作为 FINAL_RESPONSE，
        // 而是构造一个虚拟的 reply_text 工具调用 TODO，走正常 ToolNode 执行，
        // 使所有输出都经工具留痕、可审计。
        // 注意：不写入 FINAL_RESPONSE 通道，避免 RouteAfterPlan 短路到 END 跳过 ToolNode。
        String directReply = parseDirectReply(planText);
        if (directReply != null) {
            log.info("PlanNode: 检测到简单对话，构造虚拟 reply_text TODO 走 ToolNode 执行（不再直接产 FINAL_RESPONSE）");
            // 把 LLM 在 plan 阶段生成的回复内容编码到 TODO 描述中，
            // ToolNode 的 buildUserPrompt 会把 TODO 描述拼给 function-call LLM，
            // 引导其选择 reply_text 工具并把这段内容填入 content 参数。
            // 标记前缀让 ToolNode / 校验器能识别这是预设好的回复 TODO。
            String todoDesc = "使用 reply_text 工具回复用户（replyType=GREETING），"
                    + "content 参数必须原样使用以下内容（不要改写/总结）:\n"
                    + DIRECT_REPLY_PRESET_MARKER + directReply + DIRECT_REPLY_PRESET_MARKER;
            TodoItem replyTodo = TodoItem.builder()
                    .id(UUID.randomUUID().toString().substring(0, 8))
                    .description(todoDesc)
                    .status(TodoStatus.PENDING)
                    .updatedAt(LocalDateTime.now())
                    .build();

            Map<String, Object> directData = new HashMap<>();
            directData.put("replyType", "GREETING");
            directData.put("contentLength", directReply.length());
            emit(AgentEventType.NODE_EXECUTION, "构造虚拟 reply_text TODO 走 ToolNode 执行", directData);

            Map<String, Object> result = new HashMap<>();
            result.put(DeepAgentState.TODOS, List.of(replyTodo));
            // 不写 FINAL_RESPONSE，让 RouteAfterPlan 检测到 pending TODO → execute → ToolNode
            result.put(DeepAgentState.MESSAGES, AiMessage.from(
                    "已构造 reply_text TODO，待 ToolNode 执行回复"));
            result.put(DeepAgentState.PLAN_CYCLE_COUNT, planCycleCount);
            return result;
        }

        // 解析 TODO 列表
        List<TodoItem> todos = parseTodos(planText, existingTodos);

        // 解析失败保护：当解析结果为空且存在未完成计划时，检测连续失败
        if (todos == existingTodos && !existingTodos.isEmpty()) {
            boolean hasPending = existingTodos.stream()
                    .anyMatch(t -> t.getStatus() == TodoStatus.PENDING || t.getStatus() == TodoStatus.IN_PROGRESS);
            if (hasPending) {
                log.warn("PlanNode: LLM 未输出有效 TODO 格式，planText 前 200 字符: {}",
                        planText.substring(0, Math.min(200, planText.length())));

                // 连续失败达到上限，强制将所有 PENDING TODO 标记为 FAILED（打破无限循环）
                // 类型守卫：若 ITERATION_COUNT 通道被污染为非 Integer（如 checkpoint 反序列化降级），
                // value() 会抛 ClassCastException 让死循环保护失效。捕获后回退为 0，触发强制终止路径。
                int failCount;
                try {
                    failCount = state.<Integer>value(DeepAgentState.ITERATION_COUNT).orElse(0);
                } catch (ClassCastException cce) {
                    log.warn("PlanNode: ITERATION_COUNT 类型异常，回退为 0: {}", cce.getMessage());
                    failCount = 0;
                }
                if (failCount >= MAX_PARSE_FAILURES) {
                    log.error("PlanNode: 连续 {} 次规划未能产生有效 TODO，强制终止", failCount);
                    List<TodoItem> failedTodos = existingTodos.stream()
                            .map(t -> t.getStatus() == TodoStatus.PENDING || t.getStatus() == TodoStatus.IN_PROGRESS
                                    ? TodoItem.builder().id(t.getId()).description(t.getDescription())
                                    .status(TodoStatus.FAILED).result("规划节点连续解析失败，强制终止")
                                    .assignedAgent(t.getAssignedAgent()).updatedAt(LocalDateTime.now()).build()
                                    : t)
                            .toList();
                    Map<String, Object> parseFailResult = new HashMap<>();
                    parseFailResult.put(DeepAgentState.TODOS, failedTodos);
                    parseFailResult.put(DeepAgentState.MESSAGES, AiMessage.from("规划连续失败，任务已强制终止"));
                    parseFailResult.put(DeepAgentState.PLAN_CYCLE_COUNT, planCycleCount);
                    return parseFailResult;
                }
            }
        }

        // ===== TodoValidator 后端校验（含 LLM 重新规划重试）=====
        // 解析成功后、写入 state 前，对每个 TODO 执行工具引用 + 越界动词校验。
        // 校验失败时把错误反馈作为 user message 注入，最多重新调用 LLM 规划 MAX_TODO_VALIDATION_RETRIES 次。
        todos = validateWithRetry(todos, state, instructions, existingSummary);

        // 流式推送 PLAN_CREATED（带 todos）
        emit(AgentEventType.PLAN_CREATED, "规划完成，共 " + todos.size() + " 个任务项", Map.of("todos", todos));

        // 智能 HITL Gate 判断
        HitlDecision decision = hitlGateChecker.check(state, DecisionPoint.PLAN_COMPLETED);
        Map<String, Object> result = new HashMap<>();
        result.put(DeepAgentState.TODOS, todos);
        result.put(DeepAgentState.MESSAGES, AiMessage.from("规划完成，共 " + todos.size() + " 个任务项"));

        if (decision.isNeedConfirm()) {
            // 触发智能中断：emit INTERRUPT，置 NEED_CONFIRMATION=true
            Map<String, Object> gateData = new HashMap<>();
            gateData.put("decisionPoint", DecisionPoint.PLAN_COMPLETED.getValue());
            gateData.put("severity", decision.getSeverity());
            gateData.put("dimension", decision.getDimension());
            gateData.put("reason", decision.getReason());
            gateData.put("todos", todos);
            emit(AgentEventType.INTERRUPT, "规划需用户确认: " + decision.getReason(), gateData);

            result.put(DeepAgentState.NEED_CONFIRMATION, true);
            result.put(DeepAgentState.INTERRUPT_REASON, decision.getReason());
            result.put(DeepAgentState.INTERRUPT_SEVERITY, decision.getSeverity());
            log.info("PlanNode: 智能门控触发中断, severity={}, reason={}",
                    decision.getSeverity(), decision.getReason());
        }

        result.put(DeepAgentState.PLAN_CYCLE_COUNT, planCycleCount);
        return result;
    }

    /**
     * 识别 DIRECT_REPLY: 前缀。匹配时返回去掉前缀的回复；否则返回 null。
     */
    private String parseDirectReply(String planText) {
        if (planText == null) return null;
        String trimmed = planText.trim();
        // 大小写不敏感 + 容忍全角冒号
        String prefix = "DIRECT_REPLY";
        if (trimmed.regionMatches(true, 0, prefix, 0, prefix.length())) {
            int colonIdx = trimmed.indexOf(':', prefix.length());
            if (colonIdx < 0) {
                colonIdx = trimmed.indexOf('：', prefix.length());
            }
            if (colonIdx >= 0) {
                String reply = trimmed.substring(colonIdx + 1).trim();
                if (!reply.isBlank()) {
                    return reply;
                }
            }
        }
        return null;
    }

    /**
     * 对 LLM 解析出的 TODO 列表做后端校验，失败时带反馈重新调用 LLM 规划（最多重试 {@value #MAX_TODO_VALIDATION_RETRIES} 次）。
     * <p>
     * 重试流程：
     * <ol>
     *   <li>调用 {@link com.hypersense.boot.framework.agents.engine.validator.TodoValidator#validate}
     *       检查工具引用 + 越界动词</li>
     *   <li>校验通过 → 返回原 todos</li>
     *   <li>校验失败 → 把 errors 拼成 feedback，把"原始 instructions + feedback"重新喂给 LLM，
     *       重新解析 TODO，再次校验</li>
     *   <li>超过重试上限仍失败 → 抛 {@link com.hypersense.boot.common.exception.BusinessException}</li>
     * </ol>
     * </p>
     *
     * @param initialTodos    首次解析得到的 TODO 列表
     * @param state           当前 agent state（用于读取 enabledTools）
     * @param instructions    原始用户指令（含历史拼接），用于重试时拼回 prompt
     * @param existingSummary 现有计划摘要（用于重试时拼回 prompt）
     * @return 校验通过的 TODO 列表（首次通过则原样返回 initialTodos）
     */
    private List<TodoItem> validateWithRetry(List<TodoItem> initialTodos, DeepAgentState state,
                                             String instructions, String existingSummary) {
        // 当前 Agent 启用的工具集合（null 安全：传 null 时仅按内置白名单校验）
        java.util.Set<String> enabledTools = null;
        List<String> enabledList = state.enabledTools();
        if (enabledList != null && !enabledList.isEmpty()) {
            enabledTools = new java.util.HashSet<>(enabledList);
        }

        // 首次校验
        com.hypersense.boot.framework.agents.engine.validator.ValidationResult vr =
                com.hypersense.boot.framework.agents.engine.validator.TodoValidator.validate(
                        initialTodos, enabledTools);
        if (vr.isValid()) {
            return initialTodos;
        }

        // 校验失败：进入重试循环
        List<TodoItem> currentTodos = initialTodos;
        String lastFeedback = vr.joinedErrors();
        for (int attempt = 1; attempt <= MAX_TODO_VALIDATION_RETRIES; attempt++) {
            log.warn("PlanNode: TODO 校验失败，第 {}/{} 次重新规划。错误: {}",
                    attempt, MAX_TODO_VALIDATION_RETRIES, lastFeedback);

            // 构造带 feedback 的重试 prompt
            String retryPrompt = String.format("""
                    用户指令（含历史上下文区块，已是 ground truth，请直接基于其内容规划）：%s

                    当前计划状态：
                    %s

                    请制定或更新执行计划。

                    【上次规划校验失败，必须修正以下问题】
                    %s

                    修正规则：
                    - 每个 TODO 必须明确引用一个工具（file_write / file_read / internet_search / sandbox / reply_text / delegate）
                    - 禁止使用"直接保存/直接生成/直接执行"等表述，改为"使用 xxx 工具"
                    - 工具名必须在系统内置工具集中
                    """, instructions, existingSummary, lastFeedback);

            // 重新调用 LLM（不带附件视觉，重试简化为纯文本以降低成本）
            try {
                List<ChatMessage> retryMessages = List.of(
                        SystemMessage.from(PLAN_SYSTEM_PROMPT),
                        UserMessage.from(retryPrompt)
                );
                ChatResponse retryResp = chatModel.chat(retryMessages);
                String retryPlanText = retryResp.aiMessage().text();
                log.debug("PlanNode: 第 {} 次重新规划结果\n{}", attempt, retryPlanText);

                // 重新解析
                currentTodos = parseTodos(retryPlanText, initialTodos);
                // 再次校验
                vr = com.hypersense.boot.framework.agents.engine.validator.TodoValidator.validate(
                        currentTodos, enabledTools);
                if (vr.isValid()) {
                    log.info("PlanNode: 第 {} 次重新规划校验通过", attempt);
                    return currentTodos;
                }
                lastFeedback = vr.joinedErrors();
            } catch (Exception e) {
                // LLM 调用异常时不中断流程，继续下一次重试
                log.warn("PlanNode: 第 {} 次重新规划 LLM 调用异常: {}", attempt, e.getMessage());
                lastFeedback = "重新规划调用异常: " + e.getMessage();
            }
        }

        // 重试用尽仍失败：抛业务异常，交由上层处理
        log.error("PlanNode: TODO 校验失败，重试 {} 次仍未通过。最终错误: {}",
                MAX_TODO_VALIDATION_RETRIES, lastFeedback);
        throw new com.hypersense.boot.common.exception.BusinessException(
                "TODO 校验失败（重试 " + MAX_TODO_VALIDATION_RETRIES + " 次仍未通过）: " + lastFeedback);
    }

    /**
     * 解析 LLM 输出为 TodoItem 列表
     */
    private List<TodoItem> parseTodos(String planText, List<TodoItem> existingTodos) {
        List<TodoItem> todos = new ArrayList<>();
        String[] lines = planText.split("\n");

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isBlank()) continue;

            // 支持多种格式：TODO:, - TODO:, 1. TODO:, * TODO:
            String description = null;
            if (trimmed.contains("TODO:") || trimmed.contains("TODO ：")) {
                // 提取 TODO: 后面的描述
                int idx = trimmed.indexOf("TODO");
                int colonIdx = trimmed.indexOf(':', idx);
                if (colonIdx >= 0) {
                    description = trimmed.substring(colonIdx + 1).trim();
                }
            }

            if (description != null && !description.isBlank()) {
                TodoStatus status = findExistingStatus(description, existingTodos);
                todos.add(TodoItem.builder()
                        .id(UUID.randomUUID().toString().substring(0, 8))
                        .description(description)
                        .status(status)
                        .updatedAt(LocalDateTime.now())
                        .build());
            }
        }

        // 如果解析结果为空，保留原有计划
        return todos.isEmpty() ? existingTodos : todos;
    }

    /**
     * 查找已有 TODO 的状态
     */
    private TodoStatus findExistingStatus(String description, List<TodoItem> existingTodos) {
        return existingTodos.stream()
                .filter(t -> t.getDescription().equalsIgnoreCase(description))
                .map(TodoItem::getStatus)
                .findFirst()
                .orElse(TodoStatus.PENDING);
    }

    /**
     * 通过 SubAgentEventBus 推送事件（线程本地，由 AgentServiceImpl 设置的消费者接收）
     */
    private void emit(AgentEventType type, String message, Map<String, Object> data) {
        var consumer = SubAgentEventBus.get();
        if (consumer == null) return;
        AgentEvent event = AgentEvent.builder()
                .type(type)
                .message(message)
                .data(data)
                .timestamp(System.currentTimeMillis())
                .build();
        consumer.accept(event);
    }
}
