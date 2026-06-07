package com.hypersense.boot.framework.agents.skill;

import com.hypersense.boot.framework.agents.config.AgentProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 技能系统 Spring 自动配置
 * <p>
 * 当 application.yml 配置了 agent.skills.dirs 时自动激活。
 * 通过 Spring Profile 切换不同的技能目录实现技能包切换。
 * </p>
 *
 * <h3>配置示例：</h3>
 * <pre>
 * # application-dev.yml
 * agent:
 *   skills:
 *     dirs:
 *       - ./skills
 *
 * # application-prod.yml
 * agent:
 *   skills:
 *     dirs:
 *       - /opt/agent-skills/production
 * </pre>
 *
 * @author Claude
 * @since 2026/5/26
 */
@Slf4j
@Configuration
public class SkillAutoConfiguration {

    /**
     * 创建技能注册表（条件装配：仅在配置了 agent.skills.dirs 时创建）
     */
    @Bean
    @ConditionalOnProperty(prefix = "agent.skills", name = "dirs")
    public SkillRegistry skillRegistry(AgentProperties agentProperties) {
        SkillRegistry registry = new SkillRegistry();
        List<String> dirs = agentProperties.getSkills().getDirs();
        if (dirs != null && !dirs.isEmpty()) {
            registry.scan(dirs.toArray(new String[0]));
        }
        log.info("SkillAutoConfiguration: 技能注册表已创建, 共 {} 个技能", registry.getAll().size());
        return registry;
    }

    /**
     * 创建技能目录注入中间件
     */
    @Bean
    @ConditionalOnBean(SkillRegistry.class)
    public SkillsMiddleware skillsMiddleware(SkillRegistry skillRegistry) {
        log.info("SkillAutoConfiguration: SkillsMiddleware 已创建");
        return new SkillsMiddleware(skillRegistry);
    }

    /**
     * 创建技能加载工具
     */
    @Bean
    @ConditionalOnBean(SkillRegistry.class)
    public SkillLoadTool skillLoadTool(SkillRegistry skillRegistry) {
        log.info("SkillAutoConfiguration: SkillLoadTool 已创建");
        return new SkillLoadTool(skillRegistry);
    }
}
