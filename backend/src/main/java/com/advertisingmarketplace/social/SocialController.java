package com.advertisingmarketplace.social;

import com.advertisingmarketplace.security.CurrentUser;
import com.advertisingmarketplace.social.dto.SocialAccountResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/social")
@RequiredArgsConstructor
public class SocialController {

    private final SocialAccountService service;

    public record ConnectRequest(@NotBlank @Size(max = 2_000) String code, @NotBlank @Size(max = 4_000) String state) {
    }

    public record AuthorizationUrl(URI url) {
    }

    @GetMapping("/providers")
    public List<SocialAccountService.ProviderInfo> providers() {
        return service.providers();
    }

    @PostMapping("/{provider}/authorize")
    public AuthorizationUrl authorize(@PathVariable SocialProvider provider, Authentication authentication) {
        return new AuthorizationUrl(service.authorizationUrl(CurrentUser.id(authentication), provider));
    }

    @PostMapping("/connect")
    @ResponseStatus(HttpStatus.CREATED)
    public SocialAccountResponse connect(@Valid @RequestBody ConnectRequest request, Authentication authentication) {
        return service.connect(CurrentUser.id(authentication), request.code(), request.state());
    }

    @GetMapping("/accounts")
    public List<SocialAccountResponse> mine(Authentication authentication) {
        return service.accountsOf(CurrentUser.id(authentication));
    }

    @DeleteMapping("/accounts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disconnect(@PathVariable UUID id, Authentication authentication) {
        service.disconnect(CurrentUser.id(authentication), id);
    }
}
