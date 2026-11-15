package com.advertisingmarketplace.user;

import com.advertisingmarketplace.security.CurrentUser;
import com.advertisingmarketplace.user.dto.CreateUserRequest;
import com.advertisingmarketplace.user.dto.UpdateUserRequest;
import com.advertisingmarketplace.user.dto.UserResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private static final String SELF_OR_ADMIN = "hasRole('ADMIN') or #id.toString() == authentication.name";

    private final UserService userService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        UserResponse created = userService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/{id}")
    public UserResponse getById(@PathVariable UUID id) {
        return userService.getById(id);
    }

    @GetMapping
    public PagedModel<UserResponse> list(
            @RequestParam(required = false) UserType userType,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return new PagedModel<>(userService.list(userType, pageable));
    }

    @PutMapping("/{id}")
    @PreAuthorize(SELF_OR_ADMIN)
    public UserResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateUserRequest request,
            Authentication authentication) {
        return userService.update(id, request, CurrentUser.isAdmin(authentication));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(SELF_OR_ADMIN)
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        userService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
