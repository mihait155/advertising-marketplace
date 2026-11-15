package com.advertisingmarketplace.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.advertisingmarketplace.deal.DealRepository;
import com.advertisingmarketplace.user.dto.CreateUserRequest;
import com.advertisingmarketplace.user.dto.UpdateUserRequest;
import com.advertisingmarketplace.user.dto.UserResponse;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private DealRepository dealRepository;

    @InjectMocks
    private UserService userService;

    @Nested
    class Create {

        @Test
        void savesUserWithNormalizedEmailTrimmedFieldsAndHashedPassword() {
            when(userRepository.existsByEmail("jane@example.com")).thenReturn(false);
            when(passwordEncoder.encode("Sup3r-secret")).thenReturn("{bcrypt}hashed");
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            UserResponse response = userService.create(new CreateUserRequest(
                    "  Jane Doe ", " Jane@Example.COM ", " +40 712 345 678 ", UserType.MARKETER, "Sup3r-secret"));

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());
            User saved = captor.getValue();
            assertThat(saved.getFullName()).isEqualTo("Jane Doe");
            assertThat(saved.getEmail()).isEqualTo("jane@example.com");
            assertThat(saved.getPhone()).isEqualTo("+40 712 345 678");
            assertThat(saved.getUserType()).isEqualTo(UserType.MARKETER);
            assertThat(saved.getPasswordHash()).isEqualTo("{bcrypt}hashed");
            assertThat(response.email()).isEqualTo("jane@example.com");
        }

        @Test
        void storesBlankPhoneAsNull() {
            when(userRepository.existsByEmail(any())).thenReturn(false);
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            UserResponse response = userService.create(
                    new CreateUserRequest("Acme", "ads@acme.com", "   ", UserType.CUSTOMER, "Sup3r-secret"));

            assertThat(response.phone()).isNull();
        }

        @Test
        void rejectsDuplicateEmail() {
            when(userRepository.existsByEmail("taken@example.com")).thenReturn(true);

            assertThatThrownBy(() -> userService.create(
                    new CreateUserRequest("Someone", "Taken@example.com", null, UserType.CUSTOMER, "Sup3r-secret")))
                    .isInstanceOf(EmailAlreadyInUseException.class)
                    .hasMessageContaining("taken@example.com");
            verify(userRepository, never()).save(any());
        }
    }

    @Nested
    class GetById {

        @Test
        void returnsUserWhenFound() {
            UUID id = UUID.randomUUID();
            when(userRepository.findById(id)).thenReturn(Optional.of(
                    new User("Jane", "jane@example.com", null, UserType.ADMIN, "{bcrypt}hash")));

            assertThat(userService.getById(id).fullName()).isEqualTo("Jane");
        }

        @Test
        void throwsWhenMissing() {
            UUID id = UUID.randomUUID();
            when(userRepository.findById(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.getById(id))
                    .isInstanceOf(UserNotFoundException.class)
                    .hasMessageContaining(id.toString());
        }
    }

    @Nested
    class ListUsers {

        private final Pageable pageable = PageRequest.of(0, 10);

        @Test
        void listsAllWhenNoTypeGiven() {
            Page<User> page = new PageImpl<>(List.of(new User("A", "a@x.com", null, UserType.MARKETER, "{bcrypt}hash")));
            when(userRepository.findAll(pageable)).thenReturn(page);

            assertThat(userService.list(null, pageable).getContent()).hasSize(1);
            verify(userRepository, never()).findAllByUserType(any(), any());
        }

        @Test
        void filtersByTypeWhenGiven() {
            Page<User> page = new PageImpl<>(List.of(new User("B", "b@x.com", null, UserType.CUSTOMER, "{bcrypt}hash")));
            when(userRepository.findAllByUserType(UserType.CUSTOMER, pageable)).thenReturn(page);

            assertThat(userService.list(UserType.CUSTOMER, pageable).getContent())
                    .extracting(UserResponse::userType)
                    .containsExactly(UserType.CUSTOMER);
        }
    }

    @Nested
    class Update {

        @Test
        void updatesAllFields() {
            UUID id = UUID.randomUUID();
            User existing = new User("Old", "old@example.com", null, UserType.CUSTOMER, "{bcrypt}hash");
            when(userRepository.findById(id)).thenReturn(Optional.of(existing));
            when(userRepository.existsByEmailAndIdNot("new@example.com", id)).thenReturn(false);
            when(userRepository.saveAndFlush(existing)).thenReturn(existing);

            UserResponse response = userService.update(id,
                    new UpdateUserRequest("New Name", "NEW@example.com", "0712345678", UserType.MARKETER), true);

            assertThat(response.fullName()).isEqualTo("New Name");
            assertThat(response.email()).isEqualTo("new@example.com");
            assertThat(response.phone()).isEqualTo("0712345678");
            assertThat(response.userType()).isEqualTo(UserType.MARKETER);
        }

        @Test
        void keepingSameEmailSkipsUniquenessCheck() {
            UUID id = UUID.randomUUID();
            User existing = new User("Old", "same@example.com", null, UserType.CUSTOMER, "{bcrypt}hash");
            when(userRepository.findById(id)).thenReturn(Optional.of(existing));
            when(userRepository.saveAndFlush(existing)).thenReturn(existing);

            userService.update(id, new UpdateUserRequest("Old", "same@example.com", null, UserType.CUSTOMER), true);

            verify(userRepository, never()).existsByEmailAndIdNot(any(), any());
        }

        @Test
        void rejectsEmailOwnedByAnotherUser() {
            UUID id = UUID.randomUUID();
            when(userRepository.findById(id)).thenReturn(Optional.of(
                    new User("Old", "old@example.com", null, UserType.CUSTOMER, "{bcrypt}hash")));
            when(userRepository.existsByEmailAndIdNot("taken@example.com", id)).thenReturn(true);

            assertThatThrownBy(() -> userService.update(id,
                    new UpdateUserRequest("Old", "taken@example.com", null, UserType.CUSTOMER), true))
                    .isInstanceOf(EmailAlreadyInUseException.class);
            verify(userRepository, never()).saveAndFlush(any());
        }

        @Test
        void nonAdminCannotChangeUserType() {
            UUID id = UUID.randomUUID();
            User existing = new User("Me", "me@example.com", null, UserType.CUSTOMER, "{bcrypt}hash");
            when(userRepository.findById(id)).thenReturn(Optional.of(existing));

            assertThatThrownBy(() -> userService.update(id,
                    new UpdateUserRequest("Me", "me@example.com", null, UserType.ADMIN), false))
                    .isInstanceOf(AccessDeniedException.class);
            assertThat(existing.getUserType()).isEqualTo(UserType.CUSTOMER);
            verify(userRepository, never()).saveAndFlush(any());
        }

        @Test
        void nonAdminCanUpdateOtherFieldsWhenTypeUnchanged() {
            UUID id = UUID.randomUUID();
            User existing = new User("Me", "me@example.com", null, UserType.CUSTOMER, "{bcrypt}hash");
            when(userRepository.findById(id)).thenReturn(Optional.of(existing));
            when(userRepository.existsByEmailAndIdNot("new@example.com", id)).thenReturn(false);
            when(userRepository.saveAndFlush(existing)).thenReturn(existing);

            UserResponse response = userService.update(id,
                    new UpdateUserRequest("New Me", "new@example.com", null, UserType.CUSTOMER), false);

            assertThat(response.fullName()).isEqualTo("New Me");
            assertThat(existing.getPasswordHash()).isEqualTo("{bcrypt}hash");
        }

        @Test
        void throwsWhenMissing() {
            UUID id = UUID.randomUUID();
            when(userRepository.findById(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.update(id,
                    new UpdateUserRequest("X", "x@example.com", null, UserType.CUSTOMER), true))
                    .isInstanceOf(UserNotFoundException.class);
        }
    }

    @Nested
    class Delete {

        @Test
        void deletesExistingUser() {
            UUID id = UUID.randomUUID();
            User existing = new User("Jane", "jane@example.com", null, UserType.MARKETER, "{bcrypt}hash");
            when(userRepository.findById(id)).thenReturn(Optional.of(existing));

            userService.delete(id);

            verify(userRepository).delete(existing);
        }

        @Test
        void throwsWhenMissing() {
            UUID id = UUID.randomUUID();
            when(userRepository.findById(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.delete(id)).isInstanceOf(UserNotFoundException.class);
            verify(userRepository, never()).delete(any());
        }
    }
}
