package org.factor_investing.quant_strategy.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.factor_investing.quant_strategy.configuration.OpenApiConfig;
import org.factor_investing.quant_strategy.model.llm.IcaPromptRequest;
import org.factor_investing.quant_strategy.model.llm.LlmCompletionRequest;
import org.factor_investing.quant_strategy.model.llm.LlmCompletionResponse;
import org.factor_investing.quant_strategy.service.LlmModelService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/llm-models")
public class LlmModelController {
    private final LlmModelService llmModelService;

    public LlmModelController(LlmModelService llmModelService) {
        this.llmModelService = llmModelService;
    }

    @Operation(summary = "Call configured LLM provider", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @PostMapping("/complete")
    public ResponseEntity<LlmCompletionResponse> complete(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody LlmCompletionRequest request) {
        log.info("Calling {} model {}", request.getProvider(), request.getModel());
        return ResponseEntity.ok(llmModelService.complete(request, authorization));
    }

    @Operation(
            summary = "Call ICA chat model using simple prompt form",
            description = "Use Swagger Authorize to provide your ICA API key. Select model from dropdown and enter prompt text.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    )
    @PostMapping("/{namespace}/prompt")
    public ResponseEntity<Map<String, Object>> promptCompletion(
            @PathVariable String namespace,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody IcaPromptRequest request) {
        log.info("Calling ICA namespace {} prompt model {}", namespace, request.getModel());
        return ResponseEntity.ok(llmModelService.icaPromptCompletion(namespace, request, authorization));
    }

    @Operation(summary = "Call ICA chat completions", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @PostMapping("/{namespace}/chat/completions")
    public ResponseEntity<Map<String, Object>> chatCompletion(
            @PathVariable String namespace,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody LlmCompletionRequest request) {
        log.info("Calling ICA namespace {} model {}", namespace, request.getModel());
        return ResponseEntity.ok(llmModelService.icaChatCompletion(namespace, request, authorization));
    }

    @Operation(summary = "List ICA models by namespace", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @GetMapping("/{namespace}/models")
    public ResponseEntity<Map<String, Object>> listModels(
            @PathVariable String namespace,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        log.info("Listing ICA models for namespace {}", namespace);
        return ResponseEntity.ok(llmModelService.icaModels(namespace, authorization));
    }
}
