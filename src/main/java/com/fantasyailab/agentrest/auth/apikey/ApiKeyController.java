package com.fantasyailab.agentrest.auth.apikey;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/api-keys")
@Tag(name = "API keys", description = "Personal API keys for AI agents / MCP clients (sent as X-API-Key)")
public class ApiKeyController {

    private final ApiKeyService apiKeyService;

    public ApiKeyController(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create an API key for the current user", description = "The plaintext key is returned once.")
    public ApiKeyDtos.CreatedApiKey create(@Valid @RequestBody ApiKeyDtos.CreateApiKeyRequest request) {
        return apiKeyService.create(request.name(), request.readOnly() == null || request.readOnly());
    }

    @GetMapping
    @Operation(summary = "List my API keys (without secrets)")
    public List<ApiKeyDtos.ApiKeyResponse> list() {
        return apiKeyService.listMine();
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Revoke one of my API keys")
    public void revoke(@PathVariable long id) {
        apiKeyService.revokeMine(id);
    }
}
