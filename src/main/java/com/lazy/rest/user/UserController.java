package com.lazy.rest.user;

import com.lazy.rest.common.api.PageResponse;
import com.lazy.rest.common.api.Pageables;
import com.lazy.rest.user.UserDtos.CreateUserRequest;
import com.lazy.rest.user.UserDtos.UpdateUserRequest;
import com.lazy.rest.user.UserDtos.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.Set;

@RestController
@RequestMapping("/api/users")
@Tag(name = "Users", description = "User and role administration (requires user:manage)")
public class UserController {

    private static final Set<String> SORTABLE = Set.of("id", "username", "createdAt");

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @Operation(summary = "List users", description = "Optional `q` matches username or email.")
    public PageResponse<UserResponse> list(@RequestParam(required = false) String q,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        Pageable safe = Pageables.restrictSort(pageable, SORTABLE, Sort.by("id"));
        return PageResponse.of(userService.list(q, safe));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a user")
    public UserResponse get(@PathVariable long id) {
        return userService.get(id);
    }

    @PostMapping
    @Operation(summary = "Create a user")
    public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        UserResponse created = userService.create(request);
        var location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").build(created.id());
        return ResponseEntity.created(location).body(created);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update display name, enabled flag or roles")
    public UserResponse update(@PathVariable long id, @Valid @RequestBody UpdateUserRequest request) {
        return userService.update(id, request);
    }
}
