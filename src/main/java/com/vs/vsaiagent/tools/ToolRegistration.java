package com.vs.vsaiagent.tools;

import com.vs.vsaiagent.context.BudgetedToolCallback;
import com.vs.vsaiagent.context.ToolResultCompressor;
import com.vs.vsaiagent.observability.service.ExecutionLogService;
import com.vs.vsaiagent.observability.tool.LoggingToolCallback;
import com.vs.vsaiagent.tools.astro.CloudCoverTool;
import com.vs.vsaiagent.tools.astro.LightPollutionTool;
import com.vs.vsaiagent.tools.astro.MilkyWayRiseTool;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbacks;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 集中的工具注册类
 */
@Configuration
public class ToolRegistration {

    @Value("${search-api.api-key}")
    private String searchApiKey;

    @Value("${image-api.api-key}")
    private String imageApiKey;

    private final ExecutionLogService executionLogService;
    private final ToolResultCompressor toolResultCompressor;

    public ToolRegistration(ExecutionLogService executionLogService,
                            ToolResultCompressor toolResultCompressor) {
        this.executionLogService = executionLogService;
        this.toolResultCompressor = toolResultCompressor;
    }

    @Bean
    public ToolCallback[] allTools() {
        FileOperationTool fileOperationTool = new FileOperationTool();
        WebSearchTool webSearchTool = new WebSearchTool(searchApiKey);
        WebScrapingTool webScrapingTool = new WebScrapingTool();
        ImageSearchTool imageSearchTool = new ImageSearchTool(imageApiKey);
        ResourceDownloadTool resourceDownloadTool = new ResourceDownloadTool();
        TerminalOperationTool terminalOperationTool = new TerminalOperationTool();
        PDFGenerationTool pdfGenerationTool = new PDFGenerationTool();
        TerminateTool terminateTool = new TerminateTool();
        MilkyWayRiseTool milkyWayRiseTool = new MilkyWayRiseTool();
        LightPollutionTool lightPollutionTool = new LightPollutionTool();
        CloudCoverTool cloudCoverTool = new CloudCoverTool();
        ToolCallback[] callbacks = ToolCallbacks.from(
                fileOperationTool,
                webSearchTool,
                webScrapingTool,
                imageSearchTool,
                resourceDownloadTool,
                terminalOperationTool,
                pdfGenerationTool,
                terminateTool,
                milkyWayRiseTool,
                lightPollutionTool,
                cloudCoverTool
        );
        ToolCallback[] wrapped = new ToolCallback[callbacks.length];
        for (int i = 0; i < callbacks.length; i++) {
            ToolCallback logged = new LoggingToolCallback(callbacks[i], executionLogService);
            wrapped[i] = new BudgetedToolCallback(logged, toolResultCompressor, executionLogService);
        }
        return wrapped;
    }
}
