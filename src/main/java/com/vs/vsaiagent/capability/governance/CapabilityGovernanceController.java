package com.vs.vsaiagent.capability.governance;

import com.vs.vsaiagent.observability.vo.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/capability-governance")
public class CapabilityGovernanceController {

    private final CapabilityGovernanceService governanceService;
    private final CapabilityEvalExampleService exampleService;

    public CapabilityGovernanceController(CapabilityGovernanceService governanceService,
                                          CapabilityEvalExampleService exampleService) {
        this.governanceService = governanceService;
        this.exampleService = exampleService;
    }

    @GetMapping("/audit")
    public ApiResponse<CapabilityAuditReport> audit() {
        return ApiResponse.success(governanceService.audit());
    }

    @GetMapping("/examples")
    public ApiResponse<CapabilityEvalExampleReport> examples() {
        return ApiResponse.success(exampleService.generate());
    }
}
