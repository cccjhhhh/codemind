package com.codemind.impl.tool;

import com.codemind.api.safety.PermissionLevel;
import com.codemind.api.tool.Tool;
import com.codemind.api.tool.ToolResult;
import com.codemind.impl.skill.SkillEntry;
import com.codemind.impl.skill.SkillRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;

public class LoadSkillTool implements Tool {

    private static final ObjectMapper JSON = new ObjectMapper();
    private final SkillRegistry skillRegistry;

    public LoadSkillTool(SkillRegistry skillRegistry) {
        this.skillRegistry = skillRegistry;
    }

    @Override
    public String getName() { return "LoadSkill"; }

    @Override
    public String getDescription() {
        return "加载指定技能的完整指令。技能名称通过 Available Skills 列表获取。";
    }

    @Override
    public PermissionLevel getDefaultPermission() { return PermissionLevel.ALLOW; }

    @Override
    public JsonNode getInputSchema() {
        ObjectNode schema = JSON.createObjectNode();
        schema.put("type", "object");
        ObjectNode props = schema.putObject("properties");
        ObjectNode nameProp = props.putObject("name");
        nameProp.put("type", "string");
        nameProp.put("description", "要加载的技能名称");
        schema.putArray("required").add("name");
        return schema;
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        String name = (String) params.get("name");
        if (name == null || name.isBlank()) {
            return ToolResult.failure("参数 'name' 是必需的");
        }

        SkillEntry entry = skillRegistry.get(name);
        if (entry == null) {
            return ToolResult.failure("技能 '" + name + "' 不存在。可用技能: "
                + skillRegistry.listAll().stream().map(SkillEntry::name).toList());
        }

        String fullContent = loadFullContent(entry);
        if (fullContent == null) {
            return ToolResult.failure("无法读取技能 '" + name + "' 的完整内容");
        }

        return ToolResult.success(fullContent);
    }

    private String loadFullContent(SkillEntry entry) {
        if ("classpath".equals(entry.source())) {
            return entry.metadata().fullContent();
        }
        if (entry.sourcePath() != null) {
            try {
                return Files.readString(entry.sourcePath().resolve("SKILL.md"));
            } catch (IOException e) {
                return null;
            }
        }
        return null;
    }
}
