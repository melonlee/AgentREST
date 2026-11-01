package com.fantasyailab.agentrest.user;

import com.fantasyailab.agentrest.audit.Audited;
import com.fantasyailab.agentrest.common.api.LikePatterns;
import com.fantasyailab.agentrest.common.error.BadRequestException;
import com.fantasyailab.agentrest.common.error.ConflictException;
import com.fantasyailab.agentrest.common.error.ResourceNotFoundException;
import com.fantasyailab.agentrest.common.security.SecurityUtils;
import com.fantasyailab.agentrest.user.UserDtos.CreateUserRequest;
import com.fantasyailab.agentrest.user.UserDtos.UpdateUserRequest;
import com.fantasyailab.agentrest.user.UserDtos.UserResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Set;

@Service
@Validated
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository users, RoleRepository roles, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.roles = roles;
        this.passwordEncoder = passwordEncoder;
    }

    @PreAuthorize("hasAuthority('user:manage')")
    public Page<UserResponse> list(String q, Pageable pageable) {
        Page<User> page = (q == null || q.isBlank())
                ? users.findAll(pageable)
                : users.searchByPattern(LikePatterns.contains(q), pageable);
        return page.map(UserResponse::from);
    }

    @PreAuthorize("hasAuthority('user:manage')")
    public UserResponse get(long id) {
        return users.findWithAuthoritiesById(id).map(UserResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
    }

    @Transactional
    @PreAuthorize("hasAuthority('user:manage')")
    @Audited(action = "USER_CREATE", resourceType = "user", resourceId = "#result.id")
    public UserResponse create(@Valid CreateUserRequest request) {
        Set<String> roleNames = request.roles() == null || request.roles().isEmpty()
                ? Set.of(Role.USER) : request.roles();
        return UserResponse.from(createUser(request.username(), request.email(), request.password(),
                request.displayName(), roleNames));
    }

    @Transactional
    @PreAuthorize("hasAuthority('user:manage')")
    @Audited(action = "USER_UPDATE", resourceType = "user", resourceId = "#id")
    public UserResponse update(long id, @Valid UpdateUserRequest request) {
        User user = users.findWithAuthoritiesById(id).orElseThrow(() -> new ResourceNotFoundException("User", id));
        boolean self = user.getUsername().equals(SecurityUtils.currentUsername().orElse(null));
        if (request.displayName() != null) {
            user.setDisplayName(request.displayName());
        }
        if (request.enabled() != null) {
            if (self && !request.enabled()) {
                throw new BadRequestException("You cannot disable your own account");
            }
            user.setEnabled(request.enabled());
        }
        if (request.roles() != null) {
            if (self && !request.roles().contains(Role.ADMIN) && user.authorities().contains(Role.ADMIN)) {
                throw new BadRequestException("You cannot remove your own admin role");
            }
            user.getRoles().clear();
            user.getRoles().addAll(resolveRoles(request.roles()));
        }
        return UserResponse.from(user);
    }

    /**
     * Creates a user without any permission check. Used by registration, the admin bootstrap and
     * dev seeding; public callers must go through {@link #create}.
     */
    @Transactional
    public User createUser(String username, String email, String rawPassword, String displayName,
            Set<String> roleNames) {
        if (users.existsByUsername(username)) {
            throw new ConflictException("Username '" + username + "' is already taken");
        }
        if (users.existsByEmail(email)) {
            throw new ConflictException("Email is already registered");
        }
        User user = new User(username, email, passwordEncoder.encode(rawPassword), displayName);
        user.getRoles().addAll(resolveRoles(roleNames));
        return users.save(user);
    }

    private List<Role> resolveRoles(Set<String> names) {
        List<Role> found = roles.findByNameIn(names);
        if (found.size() != names.size()) {
            throw new BadRequestException("Unknown role(s). Valid roles: ROLE_ADMIN, ROLE_EDITOR, ROLE_USER");
        }
        return found;
    }
}
