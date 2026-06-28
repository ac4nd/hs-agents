package com.hypersense.boot.framework.agents.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDateTime;

@TableName(value = "sys_agent_profile", autoResultMap = true)
public class AgentProfileEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String profileId;
    private String name;
    private String description;
    private String systemPrompt;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private JsonNode allowedTools;

    private String planStrategy;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private JsonNode outputFormat;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private JsonNode lintRules;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private JsonNode hitlPolicy;

    private Boolean enabled;
    private Integer sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getProfileId() { return profileId; }
    public void setProfileId(String profileId) { this.profileId = profileId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getSystemPrompt() { return systemPrompt; }
    public void setSystemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; }
    public JsonNode getAllowedTools() { return allowedTools; }
    public void setAllowedTools(JsonNode allowedTools) { this.allowedTools = allowedTools; }
    public String getPlanStrategy() { return planStrategy; }
    public void setPlanStrategy(String planStrategy) { this.planStrategy = planStrategy; }
    public JsonNode getOutputFormat() { return outputFormat; }
    public void setOutputFormat(JsonNode outputFormat) { this.outputFormat = outputFormat; }
    public JsonNode getLintRules() { return lintRules; }
    public void setLintRules(JsonNode lintRules) { this.lintRules = lintRules; }
    public JsonNode getHitlPolicy() { return hitlPolicy; }
    public void setHitlPolicy(JsonNode hitlPolicy) { this.hitlPolicy = hitlPolicy; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
