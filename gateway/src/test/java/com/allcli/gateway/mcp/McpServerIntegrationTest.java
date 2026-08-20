package com.allcli.gateway.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import com.allcli.gateway.catalog.api.dto.PublishRequest;
import com.allcli.gateway.catalog.core.CatalogService;
import com.allcli.gateway.model.Subscription;
import com.allcli.gateway.model.System;
import com.allcli.gateway.model.ToolDefinition;
import com.allcli.gateway.persistence.ApprovalRepository;
import com.allcli.gateway.persistence.InvocationRepository;
import com.allcli.gateway.persistence.SubscriptionRepository;
import com.allcli.gateway.persistence.SystemRepository;
import com.allcli.gateway.persistence.ToolDefinitionRepository;
import com.allcli.gateway.persistence.ToolVersionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.test.context.TestPropertySource;

/**
 * MCP Server 集成测试：streamable HTTP 端点 /mcp 裸 JSON-RPC（initialize → tools/list →
 * tools/call），验证工具清单/治理注解/调用走网关流水线/HITL 挂起/鉴权拒绝。
 *
 * <p>连真实 MySQL/Redis；mock 下游用 JDK HttpServer（随机端口）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "allcli.mcp.default-agent=")
class McpServerIntegrationTest {

    private static final String SYSTEM_ID = "mcp-it-system";
    private static final String RO_TOOL = "mcp_it_list_pets";
    private static final String WR_TOOL = "mcp_it_create_pet";
    private static final String AGENT = "mcp-it-agent";
    private static final String AUTH = "Bearer agent_" + AGENT;

    private static HttpServer mockServer;
    private static String mockBaseUrl;

    private final ObjectMapper mapper = new ObjectMapper();

    @Autowired
    private ToolDefinitionRepository toolRepo;
    @Autowired
    private SystemRepository systemRepo;
    @Autowired
    private SubscriptionRepository subRepo;
    @Autowired
    private InvocationRepository invRepo;
    @Autowired
    private ApprovalRepository apvRepo;
    @Autowired
    private ToolVersionRepository versionRepo;
    @Autowired
    private CatalogService catalogService;
    @Autowired
    private McpCatalogRefresher refresher;

    private TestRestTemplate rest;
    private String baseUrl;
    private String sessionId;
    private int reqCounter = 1;

    @BeforeAll
    static void startMock() throws IOException {
        mockServer = HttpServer.create(new InetSocketAddress(0), 0);
        mockServer.createContext("/pets", exchange -> {
            byte[] resp = "[{\"id\":1,\"name\":\"Rex\"}]".getBytes();
            exchange.getResponseHeaders().set("Content-Type", "application/json;charset=utf-8");
            exchange.sendResponseHeaders(200, resp.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp);
            }
        });
        mockServer.start();
        mockBaseUrl = "http://127.0.0.1:" + mockServer.getAddress().getPort();
    }

    @AfterAll
    static void stopMock() {
        if (mockServer != null) {
            mockServer.stop(0);
        }
    }

    @BeforeEach
    void seed(@Value("${local.server.port}") int port) throws IOException {
        rest = new TestRestTemplate();
        // MCP 响应 Content-Type 无 charset，默认 StringHttpMessageConverter 会按 ISO-8859-1 解出乱码
        rest.getRestTemplate().getMessageConverters().add(0,
                new StringHttpMessageConverter(StandardCharsets.UTF_8));
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(10_000);
        f.setReadTimeout(60_000);
        rest.getRestTemplate().setRequestFactory(f);
        baseUrl = "http://127.0.0.1:" + port + "/mcp";

        systemRepo.save(System.builder()
                .systemId(SYSTEM_ID).name("mcp-it").baseUrl(mockBaseUrl)
                .authType("None").owner("alice").status("active").build());
        toolRepo.save(ToolDefinition.builder()
                .toolId(RO_TOOL).name(RO_TOOL).version("1.0.0").status("published")
                .systemRef(SYSTEM_ID).description("列出宠物")
                .endpoint(Map.of("method", "GET", "path", "/pets", "timeoutMs", 30000))
                .inputSchema(Map.of("type", "object"))
                .sideEffect("readonly").reversibility("readonly")
                .hitl(Map.of("enabled", false))
                .owner("alice").build());
        toolRepo.save(ToolDefinition.builder()
                .toolId(WR_TOOL).name(WR_TOOL).version("1.0.0").status("published")
                .systemRef(SYSTEM_ID).description("创建宠物（写操作，HITL）")
                .endpoint(Map.of("method", "POST", "path", "/pets", "timeoutMs", 30000))
                .inputSchema(Map.of("type", "object",
                        "properties", Map.of("name", Map.of("type", "string")),
                        "required", List.of("name")))
                .sideEffect("write").reversibility("reversible")
                .hitl(Map.of("enabled", true, "approverType", "human",
                        "approverRef", "alice", "timeoutSec", 300))
                .owner("alice").build());
        // 未发布工具不得出现在 tools/list
        toolRepo.save(ToolDefinition.builder()
                .toolId("mcp_it_draft").name("mcp_it_draft").version("1.0.0").status("draft")
                .systemRef(SYSTEM_ID).description("草稿工具")
                .endpoint(Map.of("method", "GET", "path", "/pets", "timeoutMs", 30000))
                .sideEffect("readonly").reversibility("readonly")
                .owner("alice").build());
        subRepo.save(Subscription.builder()
                .subId("mcp_it_sub_ro").agentId(AGENT).toolId(RO_TOOL)
                .status("approved").approvedBy("alice").build());
        subRepo.save(Subscription.builder()
                .subId("mcp_it_sub_wr").agentId(AGENT).toolId(WR_TOOL)
                .status("approved").approvedBy("alice").build());

        // MCP server 注册表在启动时构建；种子直写 repo 不会发事件，手动全量同步
        refresher.resync();
        initializeSession();
    }

    @AfterEach
    void cleanup() {
        List<String> ownInvocations = invRepo.findAll().stream()
                .filter(i -> AGENT.equals(i.getAgentId()))
                .map(i -> i.getInvocationId())
                .toList();
        apvRepo.findAll().stream()
                .filter(a -> ownInvocations.contains(a.getInvocationId()))
                .forEach(apvRepo::delete);
        invRepo.findAll().stream()
                .filter(i -> AGENT.equals(i.getAgentId()))
                .forEach(invRepo::delete);
        subRepo.findAll().stream()
                .filter(s -> AGENT.equals(s.getAgentId()))
                .forEach(subRepo::delete);
        toolRepo.findAll().stream()
                .filter(t -> t.getToolId().startsWith("mcp_it_"))
                .forEach(toolRepo::delete);
        versionRepo.findAll().stream()
                .filter(v -> v.getToolId().startsWith("mcp_it_"))
                .forEach(versionRepo::delete);
        systemRepo.findAll().stream()
                .filter(s -> SYSTEM_ID.equals(s.getSystemId()))
                .forEach(systemRepo::delete);
        refresher.resync(); // 清掉 server 内存中残留的本用例工具
    }

    @Test
    @Timeout(120)
    void toolsListExposesPublishedToolsWithGovernanceAnnotations() throws Exception {
        ResponseEntity<String> resp = rpc("tools/list", Map.of(), true);
        JsonNode result = resultOf(resp);
        JsonNode tools = result.path("tools");
        assertThat(tools.isArray()).isTrue();

        JsonNode ro = findTool(tools, RO_TOOL);
        assertThat(ro.path("description").asText()).isEqualTo("列出宠物");
        assertThat(ro.path("annotations").path("readOnlyHint").asBoolean()).isTrue();
        assertThat(ro.path("_meta").path("sideEffect").asText()).isEqualTo("readonly");

        JsonNode wr = findTool(tools, WR_TOOL);
        assertThat(wr.path("annotations").path("readOnlyHint").asBoolean()).isFalse();
        assertThat(wr.path("annotations").path("destructiveHint").asBoolean()).isFalse();
        assertThat(wr.path("_meta").path("hitl").path("enabled").asBoolean()).isTrue();

        assertThat(findTool(tools, "mcp_it_draft")).isNull();
    }

    @Test
    @Timeout(120)
    void readonlyCallRunsThroughPipeline() throws Exception {
        ResponseEntity<String> resp = rpc("tools/call",
                Map.of("name", RO_TOOL, "arguments", Map.of("limit", 1)), true);
        JsonNode content = contentOf(resp);
        assertThat(content.path("isError").asBoolean(false)).isFalse();
        String text = content.path("content").get(0).path("text").asText();
        assertThat(text).contains("\"status\":\"succeeded\"").contains("Rex")
                .contains("\"invocationId\":\"inv_");
    }

    @Test
    @Timeout(120)
    void writeCallSuspendsWithApprovalId() throws Exception {
        ResponseEntity<String> resp = rpc("tools/call",
                Map.of("name", WR_TOOL, "arguments", Map.of("name", "Mochi")), true);
        JsonNode content = contentOf(resp);
        assertThat(content.path("isError").asBoolean(false)).isFalse();
        String text = content.path("content").get(0).path("text").asText();
        assertThat(text).contains("\"status\":\"pending_approval\"")
                .contains("\"approvalId\":\"apv_")
                .contains("/api/v1/hitl/");
    }

    @Test
    @Timeout(120)
    void callWithoutAgentTokenDenied() throws Exception {
        ResponseEntity<String> resp = rpc("tools/call",
                Map.of("name", RO_TOOL, "arguments", Map.of()), false);
        JsonNode content = contentOf(resp);
        assertThat(content.path("isError").asBoolean()).isTrue();
        String text = content.path("content").get(0).path("text").asText();
        assertThat(text).contains("AUTH_DENIED").contains("\"category\":\"auth\"");
    }

    @Test
    @Timeout(120)
    void schemaViolationRejected() throws Exception {
        ResponseEntity<String> resp = rpc("tools/call",
                Map.of("name", WR_TOOL, "arguments", Map.of()), true);
        String text = contentOf(resp).path("content").get(0).path("text").asText();
        assertThat(text).contains("INTENT_REJECTED");
    }

    @Test
    @Timeout(120)
    void newlyPublishedToolVisibleWithoutRestart() throws Exception {
        PublishRequest req = new PublishRequest();
        req.setTarget("published");
        catalogService.publish("mcp_it_draft", req); // 真实事务提交 → AFTER_COMMIT → addTool

        ResponseEntity<String> resp = rpc("tools/list", Map.of(), true);
        JsonNode tools = resultOf(resp).path("tools");
        assertThat(findTool(tools, "mcp_it_draft")).isNotNull();
    }

    // ---------------- JSON-RPC helpers ----------------

    /** initialize 握手并缓存会话 id。 */
    private void initializeSession() {
        ResponseEntity<String> resp = rpc("initialize", Map.of(
                "protocolVersion", "2025-06-18",
                "capabilities", Map.of(),
                "clientInfo", Map.of("name", "allcli-it", "version", "1.0")), true);
        assertThat(resp.getStatusCode().is2xxSuccessful())
                .as("initialize status=%s body=%s", resp.getStatusCode(), resp.getBody()).isTrue();
        List<String> session = resp.getHeaders().get("Mcp-Session-Id");
        if (session != null && !session.isEmpty()) {
            sessionId = session.get(0);
        }
        notification("notifications/initialized"); // 通知（无 id）
    }

    /** 发 JSON-RPC 请求（自动分配递增 id；无 id 会被 streamable 传输当作通知拒绝）。 */
    private ResponseEntity<String> rpc(String method, Object params, boolean withAuth) {
        return post(method, params, "req-" + reqCounter++, withAuth);
    }

    /** 发 JSON-RPC 通知（无 id；需已建立会话）。 */
    private ResponseEntity<String> notification(String method) {
        return post(method, null, null, true);
    }

    private ResponseEntity<String> post(String method, Object params, String id, boolean withAuth) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM));
        if (sessionId != null) {
            headers.set("Mcp-Session-Id", sessionId);
        }
        if (withAuth) {
            headers.set("Authorization", AUTH);
        }
        StringBuilder body = new StringBuilder("{\"jsonrpc\":\"2.0\"");
        if (id != null) {
            body.append(",\"id\":\"").append(id).append("\"");
        }
        body.append(",\"method\":\"").append(method).append("\"");
        if (params != null) {
            try {
                body.append(",\"params\":").append(mapper.writeValueAsString(params));
            } catch (Exception ignored) {
                throw new IllegalArgumentException("params 序列化失败", ignored);
            }
        }
        body.append("}");
        return rest.exchange(baseUrl, HttpMethod.POST, new HttpEntity<>(body.toString(), headers),
                String.class);
    }

    /** 解析 JSON-RPC result：兼容 application/json 单体与 text/event-stream 的 data: 行。 */
    private JsonNode resultOf(ResponseEntity<String> resp) throws IOException {
        JsonNode root = parseBody(resp);
        assertThat(root.has("result")).as("body=%s", resp.getBody()).isTrue();
        return root.path("result");
    }

    private JsonNode contentOf(ResponseEntity<String> resp) throws IOException {
        JsonNode root = parseBody(resp);
        assertThat(root.has("result")).as("body=%s", resp.getBody()).isTrue();
        return root.path("result");
    }

    private JsonNode parseBody(ResponseEntity<String> resp) throws IOException {
        String body = resp.getBody();
        assertThat(body).isNotBlank();
        if (body.trim().startsWith("{")) {
            return mapper.readTree(body);
        }
        // SSE：取首个 data: 行
        for (String line : body.split("\n")) {
            if (line.startsWith("data:")) {
                return mapper.readTree(line.substring(5).trim());
            }
        }
        throw new IllegalStateException("无法解析响应: " + body);
    }

    private static JsonNode findTool(JsonNode tools, String toolId) {
        for (JsonNode t : tools) {
            if (toolId.equals(t.path("name").asText())) {
                return t;
            }
        }
        return null;
    }
}
