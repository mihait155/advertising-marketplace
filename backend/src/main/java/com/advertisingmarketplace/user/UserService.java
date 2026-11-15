package com.advertisingmarketplace.user;

import com.advertisingmarketplace.deal.DealExceptions.ActiveDealsException;
import com.advertisingmarketplace.deal.DealRepository;
import com.advertisingmarketplace.deal.DealStatus;
import com.advertisingmarketplace.user.dto.CreateUserRequest;
import com.advertisingmarketplace.user.dto.UpdateUserRequest;
import com.advertisingmarketplace.user.dto.UserResponse;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final DealRepository dealRepository;

    @Transactional
    public UserResponse create(CreateUserRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyInUseException(email);
        }
        User user = new User(
                request.fullName().trim(),
                email,
                normalizePhone(request.phone()),
                request.userType(),
                passwordEncoder.encode(request.password()));
        return UserResponse.from(userRepository.save(user));
    }

    public UserResponse getById(UUID id) {
        return UserResponse.from(findOrThrow(id));
    }

    public Page<UserResponse> list(UserType userType, Pageable pageable) {
        Page<User> page = userType == null
                ? userRepository.findAll(pageable)
                : userRepository.findAllByUserType(userType, pageable);
        return page.map(UserResponse::from);
    }

    @Transactional
    public UserResponse update(UUID id, UpdateUserRequest request, boolean allowUserTypeChange) {
        User user = findOrThrow(id);
        if (!allowUserTypeChange && request.userType() != user.getUserType()) {
            throw new AccessDeniedException("Only administrators can change a user's type");
        }
        String email = normalizeEmail(request.email());
        if (!email.equals(user.getEmail()) && userRepository.existsByEmailAndIdNot(email, id)) {
            throw new EmailAlreadyInUseException(email);
        }
        user.setFullName(request.fullName().trim());
        user.setEmail(email);
        user.setPhone(normalizePhone(request.phone()));
        user.setUserType(request.userType());
        return UserResponse.from(userRepository.saveAndFlush(user));
    }

    @Transactional
    public void delete(UUID id) {
        User user = findOrThrow(id);
        if (dealRepository.existsForParticipantWithStatusIn(id, DealStatus.ACTIVE)) {
            throw new ActiveDealsException("Finish or cancel your active deals before deleting the account");
        }
        userRepository.delete(user);
    }

    private User findOrThrow(UUID id) {
        return userRepository.findById(id).orElseThrow(() -> new UserNotFoundException(id));
    }

    public static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizePhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        return phone.trim();
    }
}
