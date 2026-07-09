package org.factor_investing.quant_strategy.service;

import org.factor_investing.quant_strategy.configuration.LlmProviderProperties;
import org.factor_investing.quant_strategy.model.llm.IcaPromptRequest;
import org.factor_investing.quant_strategy.model.llm.LlmFileRef;
import org.factor_investing.quant_strategy.model.llm.LlmCompletionRequest;
import org.factor_investing.quant_strategy.model.llm.LlmCompletionResponse;
import org.factor_investing.quant_strategy.model.llm.LlmMessage;
import org.factor_investing.quant_strategy.model.llm.LlmProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Slf4j
@Service
public class LlmModelService {
    private static final String DEFAULT_ANTHROPIC_VERSION = "2023-06-01";
    private static final String DEFAULT_ICA_NAMESPACE = "chat-models";
    private static final List<String> ICA_NAMESPACES = List.of("assistants", "agents", "digital-workforce", "chat-models");
    private static final ParameterizedTypeReference<Map<String, Object>> MAP_RESPONSE =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient restClient;
    private final LlmProviderProperties properties;

    public LlmModelService(RestClient llmRestClient, LlmProviderProperties properties) {
        this.restClient = llmRestClient;
        this.properties = properties;
    }

    public LlmCompletionResponse complete(LlmCompletionRequest request, String authorizationHeader) {
        validateRequest(request);
        LlmProvider provider = request.getProvider() != null ? request.getProvider() : LlmProvider.ICA;
        return switch (provider) {
            case ICA -> callIca(request, authorizationHeader);
            case OPENAI -> callOpenAi(request);
            case ANTHROPIC -> callAnthropic(request);
            case GEMINI -> callGemini(request);
        };
    }

    public Map<String, Object> icaChatCompletion(String namespace, LlmCompletionRequest request, String authorizationHeader) {
        validateRequest(request);
        String resolvedNamespace = resolveIcaNamespace(namespace);
        Map<String, Object> body = buildIcaChatBody(request);
        return post(properties.getIca().getBaseUrl() + "/" + resolvedNamespace + "/chat/completions",
                body, Map.of(HttpHeaders.AUTHORIZATION, resolveIcaAuthorization(authorizationHeader)));
    }

    public Map<String, Object> icaPromptCompletion(String namespace, IcaPromptRequest promptRequest, String authorizationHeader) {
        LlmCompletionRequest request = new LlmCompletionRequest();
        request.setProvider(LlmProvider.ICA);
        request.setModel(promptRequest.getModel());
        request.setPrompt(promptRequest.getPrompt());
        request.setSystemPrompt(promptRequest.getSystemPrompt());
        request.setStream(promptRequest.getStream());
        request.setTemperature(promptRequest.getTemperature());
        request.setTopP(promptRequest.getTopP());
        request.setMaxTokens(promptRequest.getMaxTokens());
        request.setFiles(promptRequest.getFiles());
        return icaChatCompletion(namespace, request, authorizationHeader);
    }

    public Map<String, Object> icaModels(String namespace, String authorizationHeader) {
        String resolvedNamespace = resolveIcaNamespace(namespace);
        return get(properties.getIca().getBaseUrl() + "/" + resolvedNamespace + "/models",
                Map.of(HttpHeaders.AUTHORIZATION, resolveIcaAuthorization(authorizationHeader)));
    }

    private LlmCompletionResponse callIca(LlmCompletionRequest request, String authorizationHeader) {
        String namespace = StringUtils.hasText(request.getNamespace()) ? request.getNamespace() : DEFAULT_ICA_NAMESPACE;
        Map<String, Object> response = icaChatCompletion(namespace, request, authorizationHeader);
        return buildResponse(request, response, extractOpenAiContent(response), asMap(response.get("usage")));
    }

    private LlmCompletionResponse callOpenAi(LlmCompletionRequest request) {
        LlmProviderProperties.Provider provider = requireProviderConfig(request.getProvider(), properties.getOpenai());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", request.getModel());
        body.put("messages", buildChatMessages(request));
        putIfPresent(body, "temperature", request.getTemperature());
        putIfPresent(body, "max_tokens", request.getMaxTokens());

        Map<String, Object> response = post(provider.getBaseUrl() + "/chat/completions", body,
                Map.of(HttpHeaders.AUTHORIZATION, "Bearer " + provider.getApiKey()));

        return buildResponse(request, response, extractOpenAiContent(response), asMap(response.get("usage")));
    }

    private LlmCompletionResponse callAnthropic(LlmCompletionRequest request) {
        LlmProviderProperties.Provider provider = requireProviderConfig(request.getProvider(), properties.getAnthropic());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", request.getModel());
        body.put("max_tokens", request.getMaxTokens() != null ? request.getMaxTokens() : 1024);
        putIfPresent(body, "system", request.getSystemPrompt());
        body.put("messages", buildAnthropicMessages(request));
        putIfPresent(body, "temperature", request.getTemperature());

        Map<String, Object> response = post(provider.getBaseUrl() + "/messages", body,
                Map.of(
                        "x-api-key", provider.getApiKey(),
                        "anthropic-version", StringUtils.hasText(provider.getApiVersion())
                                ? provider.getApiVersion()
                                : DEFAULT_ANTHROPIC_VERSION
                ));

        return buildResponse(request, response, extractAnthropicContent(response), asMap(response.get("usage")));
    }

    private LlmCompletionResponse callGemini(LlmCompletionRequest request) {
        LlmProviderProperties.Provider provider = requireProviderConfig(request.getProvider(), properties.getGemini());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("contents", buildGeminiContents(request));
        putIfPresent(body, "systemInstruction", buildGeminiSystemInstruction(request.getSystemPrompt()));

        Map<String, Object> generationConfig = new LinkedHashMap<>();
        putIfPresent(generationConfig, "temperature", request.getTemperature());
        putIfPresent(generationConfig, "maxOutputTokens", request.getMaxTokens());
        if (!generationConfig.isEmpty()) {
            body.put("generationConfig", generationConfig);
        }

        String modelPath = request.getModel().startsWith("models/") ? request.getModel() : "models/" + request.getModel();
        Map<String, Object> response = post(provider.getBaseUrl() + "/" + modelPath + ":generateContent?key=" + provider.getApiKey(),
                body, Map.of());

        return buildResponse(request, response, extractGeminiContent(response), asMap(response.get("usageMetadata")));
    }

    private Map<String, Object> post(String url, Map<String, Object> body, Map<String, String> headers) {
        try {
            Map<String, Object> response = restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(httpHeaders -> headers.forEach(httpHeaders::set))
                    .body(body)
                    .retrieve()
                    .body(MAP_RESPONSE);
            return response != null ? response : Map.of();
        } catch (RestClientResponseException ex) {
            log.warn("LLM provider POST failed. url={}, status={}, response={}", url, ex.getStatusCode(), ex.getResponseBodyAsString());
            throw new ResponseStatusException(ex.getStatusCode(), "LLM provider call failed: " + ex.getResponseBodyAsString(), ex);
        }
    }

    private Map<String, Object> get(String url, Map<String, String> headers) {
        try {
            Map<String, Object> response = restClient.get()
                    .uri(url)
                    .headers(httpHeaders -> headers.forEach(httpHeaders::set))
                    .retrieve()
                    .body(MAP_RESPONSE);
            return response != null ? response : Map.of();
        } catch (RestClientResponseException ex) {
            log.warn("LLM provider GET failed. url={}, status={}, response={}", url, ex.getStatusCode(), ex.getResponseBodyAsString());
            throw new ResponseStatusException(ex.getStatusCode(), "LLM provider call failed: " + ex.getResponseBodyAsString(), ex);
        }
    }

    private void validateRequest(LlmCompletionRequest request) {
        boolean hasPrompt = StringUtils.hasText(request.getPrompt());
        boolean hasMessages = request.getMessages() != null && !request.getMessages().isEmpty();
        if (!hasPrompt && !hasMessages) {
            throw new ResponseStatusException(BAD_REQUEST, "Either prompt or messages is required");
        }
    }

    private LlmProviderProperties.Provider requireProviderConfig(LlmProvider providerName, LlmProviderProperties.Provider provider) {
        if (provider == null || !StringUtils.hasText(provider.getApiKey()) || !StringUtils.hasText(provider.getBaseUrl())) {
            throw new ResponseStatusException(BAD_REQUEST, providerName + " apiKey and baseUrl must be configured");
        }
        return provider;
    }

    private String resolveIcaAuthorization(String authorizationHeader) {
        if (StringUtils.hasText(authorizationHeader)) {
            return authorizationHeader.startsWith("Bearer ") ? authorizationHeader : "Bearer " + authorizationHeader;
        }
        LlmProviderProperties.Provider provider = properties.getIca();
        if (provider != null && StringUtils.hasText(provider.getApiKey())) {
            return "Bearer " + provider.getApiKey();
        }
        throw new ResponseStatusException(BAD_REQUEST, "Authorization bearer token is required for ICA requests");
    }

    private String resolveIcaNamespace(String namespace) {
        String resolvedNamespace = StringUtils.hasText(namespace) ? namespace : DEFAULT_ICA_NAMESPACE;
        if (!ICA_NAMESPACES.contains(resolvedNamespace)) {
            throw new ResponseStatusException(BAD_REQUEST, "Unsupported ICA namespace: " + resolvedNamespace);
        }
        return resolvedNamespace;
    }

    private Map<String, Object> buildIcaChatBody(LlmCompletionRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", request.getModel());
        body.put("messages", buildChatMessages(request));
        body.put("stream", request.getStream() != null ? request.getStream() : false);
        putIfPresent(body, "temperature", request.getTemperature());
        putIfPresent(body, "top_p", request.getTopP());
        putIfPresent(body, "max_tokens", request.getMaxTokens());
        if (request.getFiles() != null && !request.getFiles().isEmpty()) {
            body.put("files", buildFileRefs(request.getFiles()));
        }
        return body;
    }

    private List<Map<String, Object>> buildFileRefs(List<LlmFileRef> files) {
        return files.stream()
                .map(file -> {
                    Map<String, Object> fileRef = new LinkedHashMap<>();
                    fileRef.put("type", file.getType());
                    fileRef.put("id", file.getId());
                    putIfPresent(fileRef, "name", file.getName());
                    putIfPresent(fileRef, "context", file.getContext());
                    return fileRef;
                })
                .toList();
    }

    private List<Map<String, String>> buildChatMessages(LlmCompletionRequest request) {
        List<Map<String, String>> messages = new ArrayList<>();
        if (StringUtils.hasText(request.getSystemPrompt())) {
            messages.add(Map.of("role", "system", "content", request.getSystemPrompt()));
        }
        addRequestMessages(request, messages);
        return messages;
    }

    private List<Map<String, String>> buildAnthropicMessages(LlmCompletionRequest request) {
        List<Map<String, String>> messages = new ArrayList<>();
        addRequestMessages(request, messages);
        return messages.stream()
                .filter(message -> !"system".equalsIgnoreCase(message.get("role")))
                .map(message -> Map.of(
                        "role", "assistant".equalsIgnoreCase(message.get("role")) ? "assistant" : "user",
                        "content", message.get("content")))
                .toList();
    }

    private List<Map<String, Object>> buildGeminiContents(LlmCompletionRequest request) {
        List<Map<String, String>> messages = new ArrayList<>();
        addRequestMessages(request, messages);
        return messages.stream()
                .filter(message -> !"system".equalsIgnoreCase(message.get("role")))
                .map(message -> {
                    String role = "assistant".equalsIgnoreCase(message.get("role")) ? "model" : "user";
                    return Map.<String, Object>of(
                            "role", role,
                            "parts", List.of(Map.of("text", message.get("content")))
                    );
                })
                .toList();
    }

    private void addRequestMessages(LlmCompletionRequest request, List<Map<String, String>> messages) {
        if (request.getMessages() != null && !request.getMessages().isEmpty()) {
            for (LlmMessage message : request.getMessages()) {
                messages.add(Map.of("role", message.getRole(), "content", message.getContent()));
            }
            return;
        }
        messages.add(Map.of("role", "user", "content", request.getPrompt()));
    }

    private Map<String, Object> buildGeminiSystemInstruction(String systemPrompt) {
        if (!StringUtils.hasText(systemPrompt)) {
            return null;
        }
        return Map.of("parts", List.of(Map.of("text", systemPrompt)));
    }

    private String extractOpenAiContent(Map<String, Object> response) {
        List<?> choices = asList(response.get("choices"));
        if (choices.isEmpty()) {
            return null;
        }
        Map<String, Object> choice = asMap(choices.get(0));
        Map<String, Object> message = asMap(choice.get("message"));
        Object content = message.get("content");
        return content != null ? content.toString() : null;
    }

    private String extractAnthropicContent(Map<String, Object> response) {
        return asList(response.get("content")).stream()
                .map(this::asMap)
                .filter(part -> "text".equals(part.get("type")))
                .map(part -> part.get("text"))
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .findFirst()
                .orElse(null);
    }

    private String extractGeminiContent(Map<String, Object> response) {
        List<?> candidates = asList(response.get("candidates"));
        if (candidates.isEmpty()) {
            return null;
        }
        Map<String, Object> candidate = asMap(candidates.get(0));
        Map<String, Object> content = asMap(candidate.get("content"));
        StringBuilder builder = new StringBuilder();
        for (Object partValue : asList(content.get("parts"))) {
            Object text = asMap(partValue).get("text");
            if (text != null) {
                builder.append(text);
            }
        }
        return builder.isEmpty() ? null : builder.toString();
    }

    private LlmCompletionResponse buildResponse(LlmCompletionRequest request, Map<String, Object> rawResponse,
                                                String content, Map<String, Object> usage) {
        LlmCompletionResponse response = new LlmCompletionResponse();
        response.setProvider(request.getProvider() != null ? request.getProvider() : LlmProvider.ICA);
        response.setModel(request.getModel());
        response.setContent(content);
        response.setUsage(usage);
        response.setRawResponse(rawResponse);
        return response;
    }

    private void putIfPresent(Map<String, Object> body, String key, Object value) {
        if (value != null) {
            body.put(key, value);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        return value instanceof Map<?, ?> ? (Map<String, Object>) value : Map.of();
    }

    private List<?> asList(Object value) {
        return value instanceof List<?> list ? list : List.of();
    }
}
