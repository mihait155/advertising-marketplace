package com.advertisingmarketplace.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.advertisingmarketplace.support.TestcontainersConfiguration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class UserRepositoryIT {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void flywayMigrationsWereApplied() {
        List<String> applied = jdbcTemplate.queryForList(
                "SELECT script FROM flyway_schema_history WHERE success ORDER BY installed_rank", String.class);
        assertThat(applied).containsExactly(
                "V1__create_users_table.sql", "V2__add_authentication.sql", "V3__create_postings_and_images.sql",
                "V4__add_posting_status_pay_range_and_offers.sql", "V5__add_currencies.sql", "V6__add_exchange_rates.sql", "V7__add_messaging.sql",
                "V8__add_deals_reviews_social_and_hardening.sql");
    }

    @Test
    void deletingUserCascadesToRefreshTokens() {
        User user = userRepository.saveAndFlush(new User("Jane", "jane@example.com", null, UserType.MARKETER,
                "{bcrypt}hash"));
        jdbcTemplate.update("INSERT INTO refresh_tokens (id, user_id, token_hash, expires_at) "
                + "VALUES (?, ?, 'abc', now() + interval '1 day')", UUID.randomUUID(), user.getId());

        userRepository.delete(user);
        userRepository.flush();

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM refresh_tokens", Integer.class)).isZero();
    }

    @Test
    void savesAndLoadsUserWithGeneratedIdAndTimestamps() {
        User saved = userRepository.saveAndFlush(
                new User("Jane Doe", "jane@example.com", "+40712345678", UserType.MARKETER, "{bcrypt}hash"));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isEqualTo(saved.getCreatedAt());
        assertThat(userRepository.findByEmail("jane@example.com"))
                .get()
                .extracting(User::getFullName, User::getUserType)
                .containsExactly("Jane Doe", UserType.MARKETER);
    }

    @Test
    void enforcesUniqueEmailAtDatabaseLevel() {
        userRepository.saveAndFlush(new User("First", "dup@example.com", null, UserType.CUSTOMER, "{bcrypt}hash"));

        assertThatThrownBy(() -> userRepository.saveAndFlush(
                new User("Second", "dup@example.com", null, UserType.MARKETER, "{bcrypt}hash")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsUnknownUserTypeAtDatabaseLevel() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO users (id, full_name, email, user_type, password_hash) "
                        + "VALUES (?, 'X', 'x@example.com', 'WIZARD', 'h')",
                UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void existsQueries() {
        User user = userRepository.saveAndFlush(new User("Jane", "jane@example.com", null, UserType.ADMIN, "{bcrypt}hash"));

        assertThat(userRepository.existsByEmail("jane@example.com")).isTrue();
        assertThat(userRepository.existsByEmail("nobody@example.com")).isFalse();
        assertThat(userRepository.existsByEmailAndIdNot("jane@example.com", user.getId())).isFalse();
        assertThat(userRepository.existsByEmailAndIdNot("jane@example.com", UUID.randomUUID())).isTrue();
    }

    @Test
    void findsAllByUserType() {
        userRepository.save(new User("M1", "m1@example.com", null, UserType.MARKETER, "{bcrypt}hash"));
        userRepository.save(new User("M2", "m2@example.com", null, UserType.MARKETER, "{bcrypt}hash"));
        userRepository.save(new User("C1", "c1@example.com", null, UserType.CUSTOMER, "{bcrypt}hash"));
        userRepository.flush();

        assertThat(userRepository.findAllByUserType(UserType.MARKETER, PageRequest.of(0, 10)).getContent())
                .extracting(User::getFullName)
                .containsExactlyInAnyOrder("M1", "M2");
    }
}
