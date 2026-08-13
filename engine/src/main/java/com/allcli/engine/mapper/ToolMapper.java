package com.allcli.engine.mapper;

import com.allcli.engine.model.ConvertRequest;
import com.allcli.engine.model.ConvertResult;
import com.allcli.engine.model.ToolDefinition;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 主编排：遍历 OpenAPI paths/operations，组装 {@link ToolDefinition}（对标原 Go 版 mapper）。
 *
 * <p>无 Spring 依赖。依赖 {@link SchemaConverter} / {@link InferenceRule} / {@link ToolIdSanitizer}。
 */
public final class ToolMapper {

    private final SchemaConverter schemaConverter;

    public ToolMapper(OpenAPI openAPI) {
        this.schemaConverter = new SchemaConverter(openAPI);
    }

    /** 转化整个 OpenAPI 文档为工具列表。 */
    public ConvertResult map(OpenAPI openAPI, ConvertRequest req) {
        List<ToolDefinition> tools = new ArrayList<>();
        // Paths 实现为 LinkedHashMap，遍历顺序遵循文档顺序
        for (Map.Entry<String, PathItem> entry : openAPI.getPaths().entrySet()) {
            String path = entry.getKey();
            PathItem pathItem = entry.getValue();
            for (Map.Entry<PathItem.HttpMethod, Operation> op : pathItem.readOperationsMap().entrySet()) {
                tools.add(mapOperation(path, op.getKey(), op.getValue(), pathItem, req));
            }
        }
        return new ConvertResult(tools);
    }

    private ToolDefinition mapOperation(String path, PathItem.HttpMethod method, Operation operation,
                                        PathItem pathItem, ConvertRequest req) {
        String methodName = method.name(); // GET / POST / ...
        InferenceRule.Inference inference = InferenceRule.infer(methodName, req.getOwner());

        String operationId = operation.getOperationId();
        String toolId = (operationId != null && !operationId.isEmpty())
                ? operationId
                : ToolIdSanitizer.sanitize(methodName, path);
        String name = firstNonBlank(operationId, operation.getSummary(), toolId);
        String description = firstNonBlank(operation.getSummary(), operation.getDescription());

        List<Parameter> params = mergeParameters(pathItem, operation);

        return ToolDefinition.builder()
                .toolId(toolId)
                .name(name)
                .version(req.getVersion())
                .status("draft")
                .systemRef(req.getSystemRef())
                .credentialRef(req.getCredentialRef())
                .description(description)
                .endpoint(endpoint(methodName, path, req.getTimeoutMs()))
                .inputSchema(buildInput(params, operation.getRequestBody()))
                .outputSchema(buildOutput(operation))
                .sideEffect(inference.getSideEffect())
                .reversibility(inference.getReversibility())
                .idempotencyKey(null)
                .hitl(inference.getHitl())
                .owner(req.getOwner())
                .build();
    }

    /** 合并 PathItem 级与 Operation 级参数，以 (name,in) 为键，operation 覆盖 pathItem。 */
    private List<Parameter> mergeParameters(PathItem pathItem, Operation operation) {
        Map<String, Parameter> byKey = new TreeMap<>();
        if (pathItem.getParameters() != null) {
            for (Parameter p : pathItem.getParameters()) {
                byKey.put(key(p), p);
            }
        }
        if (operation.getParameters() != null) {
            for (Parameter p : operation.getParameters()) {
                byKey.put(key(p), p);
            }
        }
        return new ArrayList<>(byKey.values());
    }

    private String key(Parameter p) {
        return p.getIn() + ":" + p.getName();
    }

    private Map<String, Object> buildInput(List<Parameter> params, RequestBody requestBody) {
        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();

        for (Parameter p : params) {
            properties.put(p.getName(), schemaConverter.toMap(p.getSchema()));
            if (Boolean.TRUE.equals(p.getRequired())) {
                required.add(p.getName());
            }
        }

        // 写操作的 requestBody 作为 body 字段
        if (requestBody != null) {
            Schema<?> bodySchema = extractSchema(requestBody);
            properties.put("body", schemaConverter.toMap(bodySchema));
            if (Boolean.TRUE.equals(requestBody.getRequired())) {
                required.add("body");
            }
        }

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("type", "object");
        input.put("properties", properties);
        input.put("required", required);
        return input;
    }

    private Map<String, Object> buildOutput(Operation operation) {
        Map<String, Object> output = new LinkedHashMap<>();
        if (operation.getResponses() == null) {
            return output;
        }
        for (Map.Entry<String, ApiResponse> r : operation.getResponses().entrySet()) {
            ApiResponse resp = r.getValue();
            Schema<?> schema = extractResponseSchema(resp);
            output.put(r.getKey(), schemaConverter.toMap(schema));
        }
        return output;
    }

    /** 取 requestBody 的 application/json（缺省取第一个）的 schema。 */
    private Schema<?> extractSchema(RequestBody requestBody) {
        if (requestBody == null || requestBody.getContent() == null
                || requestBody.getContent().isEmpty()) {
            return null;
        }
        io.swagger.v3.oas.models.media.MediaType mt = requestBody.getContent().get("application/json");
        if (mt == null) {
            mt = requestBody.getContent().values().iterator().next();
        }
        return mt.getSchema();
    }

    private Schema<?> extractResponseSchema(ApiResponse resp) {
        if (resp == null || resp.getContent() == null || resp.getContent().isEmpty()) {
            return null;
        }
        io.swagger.v3.oas.models.media.MediaType mt = resp.getContent().get("application/json");
        if (mt == null) {
            mt = resp.getContent().values().iterator().next();
        }
        return mt.getSchema();
    }

    private Map<String, Object> endpoint(String method, String path, long timeoutMs) {
        Map<String, Object> ep = new LinkedHashMap<>();
        ep.put("method", method);
        ep.put("path", path);
        ep.put("timeoutMs", timeoutMs);
        return ep;
    }

    private String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isEmpty()) {
                return v;
            }
        }
        return null;
    }
}
