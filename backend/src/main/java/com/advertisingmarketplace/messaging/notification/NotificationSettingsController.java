package com.advertisingmarketplace.messaging.notification;

import com.advertisingmarketplace.security.CurrentUser;
import com.advertisingmarketplace.user.User;
import com.advertisingmarketplace.user.UserNotFoundException;
import com.advertisingmarketplace.user.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me/notification-settings")
@RequiredArgsConstructor
public class NotificationSettingsController {

    private final UserRepository userRepository;

    public record NotificationSettings(@NotNull Boolean emailMessageNotifications) {
    }

    @GetMapping
    @Transactional(readOnly = true)
    public NotificationSettings get(Authentication authentication) {
        return new NotificationSettings(find(CurrentUser.id(authentication)).isEmailMessageNotifications());
    }

    @PutMapping
    @Transactional
    public NotificationSettings update(@Valid @RequestBody NotificationSettings settings,
            Authentication authentication) {
        User user = find(CurrentUser.id(authentication));
        user.setEmailMessageNotifications(settings.emailMessageNotifications());
        return new NotificationSettings(user.isEmailMessageNotifications());
    }

    private User find(UUID id) {
        return userRepository.findById(id).orElseThrow(() -> new UserNotFoundException(id));
    }
}
