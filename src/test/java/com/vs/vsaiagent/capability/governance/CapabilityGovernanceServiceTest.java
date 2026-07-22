package com.vs.vsaiagent.capability.governance;

import com.vs.vsaiagent.agentplatform.model.ToolExecuteRequest;
import com.vs.vsaiagent.agentplatform.model.ToolExecuteResult;
import com.vs.vsaiagent.agentplatform.model.ToolMetadata;
import com.vs.vsaiagent.agentplatform.model.ToolSourceType;
import com.vs.vsaiagent.agentplatform.service.ToolExecutionService;
import com.vs.vsaiagent.skill.Skill;
import com.vs.vsaiagent.skill.SkillContext;
import com.vs.vsaiagent.skill.SkillMetadata;
import com.vs.vsaiagent.skill.SkillResult;
import com.vs.vsaiagent.skill.registry.InMemorySkillRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CapabilityGovernanceServiceTest {

    @Test
    void auditsMissingContractsHighRiskConfirmationAndMissingEvaluation() {
        InMemorySkillRegistry skills = new InMemorySkillRegistry();
        skills.register(skill(SkillMetadata.builder()
                .name("pdf-generation")
                .displayName("PDF 生成")
                .description("生成本地 PDF 文件")
                .tags(List.of("file", "document"))
                .security(CapabilitySecurityContract.builder()
                        .riskLevel("HIGH")
                        .permissionScopes(List.of("FILE_WRITE"))
                        .requiresConfirmation(false)
                        .build())
                .build()));

        ToolExecutionService tools = new StubToolExecutionService(List.of(
                ToolMetadata.builder()
                        .toolName("terminal_exec")
                        .displayName("终端执行")
                        .description("执行系统命令")
                        .sourceType(ToolSourceType.LOCAL)
                        .tags(List.of("system"))
                        .build()
        ));

        CapabilityAuditReport report = new CapabilityGovernanceService(skills, tools).audit();

        assertEquals(2, report.totalCapabilities());
        assertTrue(report.issueCount() >= 3);
        assertTrue(report.issues().stream().anyMatch(issue ->
                issue.capabilityName().equals("terminal_exec")
                        && issue.message().contains("缺少安全契约")));
        assertTrue(report.issues().stream().anyMatch(issue ->
                issue.capabilityName().equals("terminal_exec")
                        && issue.message().contains("高风险")));
        assertTrue(report.issues().stream().anyMatch(issue ->
                issue.capabilityName().equals("pdf-generation")
                        && issue.message().contains("需要二次确认")));
        assertTrue(report.issues().stream().anyMatch(issue ->
                issue.capabilityName().equals("pdf-generation")
                        && issue.message().contains("缺少评估契约")));
    }

    private Skill skill(SkillMetadata metadata) {
        return new Skill() {
            @Override
            public SkillMetadata metadata() {
                return metadata;
            }

            @Override
            public SkillResult execute(Map<String, Object> arguments, SkillContext context) {
                return SkillResult.ok(Map.of(), 0);
            }
        };
    }

    private record StubToolExecutionService(List<ToolMetadata> metadata) implements ToolExecutionService {
        @Override
        public ToolExecuteResult executeByName(ToolExecuteRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ToolExecuteResult executeByMetadata(String tag, ToolExecuteRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<ToolMetadata> listTools() {
            return metadata;
        }
    }
}

