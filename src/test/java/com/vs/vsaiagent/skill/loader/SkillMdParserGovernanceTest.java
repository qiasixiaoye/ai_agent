package com.vs.vsaiagent.skill.loader;

import com.vs.vsaiagent.skill.SkillMetadata;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillMdParserGovernanceTest {

    @Test
    void parsesSecurityAndEvaluationContractsFromFrontMatter() {
        String skillMd = """
                ---
                name: capability-safety-review
                displayName: 能力安全评审
                description: 审计工具和 Skill 的安全契约与评估状态
                tags: [governance, audit]
                security:
                  riskLevel: medium
                  permissionScopes: [local_compute, file_read]
                  sideEffects: none
                  dataSensitivity: user_input
                  requiresConfirmation: true
                  reviewStatus: reviewed
                  lifecycleStatus: beta
                evaluation:
                  profile: functional
                  successCriteria:
                    - 输出审计问题清单
                    - 给出上线建议
                  hardConstraints:
                    - 不执行被审计能力
                  goldenCaseTags: [normal, boundary, adversarial]
                  attributionStages: [call, execution, integration]
                ---
                # 能力安全评审
                """;

        SkillMetadata metadata = SkillMdParser.parse(skillMd);

        assertEquals("MEDIUM", metadata.security().riskLevel());
        assertTrue(metadata.security().permissionScopes().contains("FILE_READ"));
        assertEquals("NONE", metadata.security().sideEffects());
        assertEquals("USER_INPUT", metadata.security().dataSensitivity());
        assertTrue(metadata.security().requiresConfirmation());
        assertEquals("REVIEWED", metadata.security().reviewStatus());
        assertEquals("BETA", metadata.security().lifecycleStatus());
        assertEquals("functional", metadata.evaluation().profile());
        assertEquals(List.of("normal", "boundary", "adversarial"), metadata.evaluation().goldenCaseTags());
        assertTrue(metadata.evaluation().successCriteria().getFirst().contains("审计问题清单"));
    }

    @Test
    void bundledSkillMarkdownFilesDeclareGovernanceContracts() throws Exception {
        Path skillsDir = Path.of("src/main/resources/skills");
        try (Stream<Path> paths = Files.walk(skillsDir)) {
            List<Path> skillFiles = paths
                    .filter(path -> path.getFileName().toString().equals("SKILL.md"))
                    .toList();
            assertEquals(4, skillFiles.size());
            for (Path skillFile : skillFiles) {
                SkillMetadata metadata = SkillMdParser.parse(Files.readString(skillFile));
                assertNotNull(metadata, skillFile.toString());
                assertNotNull(metadata.security(), metadata.name());
                assertNotNull(metadata.evaluation(), metadata.name());
                assertTrue(metadata.evaluation().successCriteria().size() >= 1, metadata.name());
            }
        }
    }
}
