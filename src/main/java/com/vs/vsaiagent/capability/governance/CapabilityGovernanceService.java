package com.vs.vsaiagent.capability.governance;

import com.vs.vsaiagent.agentplatform.model.ToolMetadata;
import com.vs.vsaiagent.agentplatform.service.ToolExecutionService;
import com.vs.vsaiagent.skill.Skill;
import com.vs.vsaiagent.skill.SkillMetadata;
import com.vs.vsaiagent.skill.registry.SkillRegistry;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class CapabilityGovernanceService {

    private static final List<String> HIGH_RISK_PATTERNS = List.of(
            "terminal", "shell", "exec", "command", "delete", "remove", "write", "send", "publish", "deploy", "payment"
    );

    private final SkillRegistry skillRegistry;
    private final ToolExecutionService toolExecutionService;

    public CapabilityGovernanceService(SkillRegistry skillRegistry, ToolExecutionService toolExecutionService) {
        this.skillRegistry = skillRegistry;
        this.toolExecutionService = toolExecutionService;
    }

    public CapabilityAuditReport audit() {
        List<ToolMetadata> tools = safeTools();
        List<Skill> skills = skillRegistry.listAll();
        List<CapabilityAuditIssue> issues = new ArrayList<>();

        for (ToolMetadata tool : tools) {
            auditTool(tool, issues);
        }
        for (Skill skill : skills) {
            auditSkill(skill.metadata(), issues);
        }

        Map<String, Long> bySeverity = issues.stream()
                .collect(Collectors.groupingBy(CapabilityAuditIssue::severity, LinkedHashMap::new, Collectors.counting()));
        return new CapabilityAuditReport(
                tools.size() + skills.size(),
                tools.size(),
                skills.size(),
                issues.size(),
                bySeverity,
                issues,
                recommendations(issues)
        );
    }

    private List<ToolMetadata> safeTools() {
        List<ToolMetadata> tools = toolExecutionService.listTools();
        return tools == null ? List.of() : tools;
    }

    private void auditTool(ToolMetadata metadata, List<CapabilityAuditIssue> issues) {
        String name = metadata.getToolName();
        CapabilitySecurityContract security = metadata.getSecurity();
        if (security == null) {
            issues.add(issue("tool", name, "WARN", "安全契约", "缺少安全契约，当前只能按名称和标签进行保守推断",
                    "在 ToolMetadata.security 中声明 riskLevel、permissionScopes 和 requiresConfirmation"));
        }
        auditSecurity("tool", name, security, inferHighRisk(name, metadata.getTags()), issues);
        auditEvaluation("tool", name, metadata.getEvaluation(), issues);
    }

    private void auditSkill(SkillMetadata metadata, List<CapabilityAuditIssue> issues) {
        String name = metadata.name();
        CapabilitySecurityContract security = metadata.security();
        if (security == null) {
            issues.add(issue("skill", name, "WARN", "安全契约", "缺少安全契约，无法明确副作用范围和敏感数据边界",
                    "在 SKILL.md 或 defaultMetadata() 中声明 security"));
        }
        auditSecurity("skill", name, security, inferHighRisk(name, metadata.tags()), issues);
        auditEvaluation("skill", name, metadata.evaluation(), issues);
    }

    private void auditSecurity(String type, String name, CapabilitySecurityContract security,
                               boolean inferredHighRisk, List<CapabilityAuditIssue> issues) {
        boolean declaredHighRisk = security != null && "HIGH".equals(security.riskLevel());
        boolean scopeImpliesHighRisk = security != null && security.permissionScopes().stream().anyMatch(scope ->
                List.of("FILE_WRITE", "EXTERNAL_WRITE", "SYSTEM_COMMAND", "PRIVATE_DATA", "PAYMENT", "DEPLOY").contains(scope));
        boolean highRisk = inferredHighRisk || declaredHighRisk || scopeImpliesHighRisk;
        if (highRisk) {
            issues.add(issue(type, name, "HIGH", "风险分级", "能力被识别为高风险，需要更严格的调用策略",
                    "确认 riskLevel=HIGH，并限制最小 permissionScopes"));
        }
        if (highRisk && (security == null || !Boolean.TRUE.equals(security.requiresConfirmation()))) {
            issues.add(issue(type, name, "HIGH", "权限确认", "高风险能力调用前需要二次确认",
                    "设置 requiresConfirmation=true；后续接入统一 CapabilityPolicyService"));
        }
        if (security != null && security.riskLevel() == null) {
            issues.add(issue(type, name, "WARN", "风险分级", "安全契约缺少 riskLevel",
                    "补充 LOW、MEDIUM 或 HIGH"));
        }
        if (security != null && security.permissionScopes().isEmpty()) {
            issues.add(issue(type, name, "WARN", "权限范围", "安全契约缺少 permissionScopes",
                    "至少声明 LOCAL_COMPUTE、EXTERNAL_READ、FILE_READ 或 FILE_WRITE 等范围"));
        }
    }

    private void auditEvaluation(String type, String name, CapabilityEvaluationContract evaluation,
                                 List<CapabilityAuditIssue> issues) {
        if (evaluation == null) {
            issues.add(issue(type, name, "WARN", "评估契约", "缺少评估契约，无法稳定衡量效果",
                    "声明 evaluation.profile、successCriteria 和 goldenCaseTags"));
            return;
        }
        if (evaluation.successCriteria().isEmpty()) {
            issues.add(issue(type, name, "WARN", "成功标准", "缺少 successCriteria",
                    "补充硬约束和软目标，作为自动化评估基线"));
        }
        if (evaluation.goldenCaseTags().isEmpty()) {
            issues.add(issue(type, name, "WARN", "黄金用例", "缺少 goldenCaseTags",
                    "至少覆盖 normal、boundary、exception 或 adversarial"));
        }
    }

    private boolean inferHighRisk(String name, List<String> tags) {
        String text = ((name == null ? "" : name) + " " + String.join(" ", tags == null ? List.of() : tags))
                .toLowerCase(Locale.ROOT);
        return HIGH_RISK_PATTERNS.stream().anyMatch(text::contains);
    }

    private CapabilityAuditIssue issue(String type, String name, String severity, String category,
                                       String message, String recommendation) {
        return new CapabilityAuditIssue(type, name, severity, category, message, recommendation);
    }

    private List<String> recommendations(List<CapabilityAuditIssue> issues) {
        if (issues.isEmpty()) {
            return List.of("当前能力目录未发现契约层阻断问题，可以进入运行时策略接入阶段。");
        }
        return List.of(
                "优先补齐缺失的安全契约，避免前端和模型只能靠名称猜测权限。",
                "所有高风险能力必须设置二次确认，并在后续接入统一运行时守卫。",
                "为每个 Skill 建立 normal、boundary、adversarial 三类黄金用例。"
        );
    }
}

