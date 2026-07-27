package com.vs.vsaiagent.dify.client;

import com.vs.vsaiagent.dify.config.DifyConsoleProperties;
import com.vs.vsaiagent.dify.dto.DifyImportResult;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.http.HttpStatus.BAD_GATEWAY;

class DifyConsoleClientTest {

    @Test
    void importReportsActionableMessageWhenConsoleGatewayIsUnavailable() {
        DifyConsoleProperties properties = new DifyConsoleProperties();
        properties.setBaseUrl("http://dify.test");
        properties.setEmail("admin@example.test");
        properties.setPassword("secret");
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(once(), requestTo("http://dify.test/console/api/login"))
                .andRespond(withStatus(BAD_GATEWAY)
                        .contentType(MediaType.TEXT_HTML)
                        .body("<html><h1>502 Bad Gateway</h1></html>"));

        DifyImportResult result = new DifyConsoleClient(properties, restTemplate).importDsl("app: {}" );

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage())
                .contains("无法连接 Dify 控制台")
                .contains("HTTP 502")
                .doesNotContain("<html>");
        server.verify();
    }
}
