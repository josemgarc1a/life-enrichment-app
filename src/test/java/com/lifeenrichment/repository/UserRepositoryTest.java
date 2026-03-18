package com.lifeenrichment.repository;

import com.lifeenrichment.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();

        userRepository.save(User.builder()
                .email("director@example.com")
                .passwordHash("$2a$10$hashedpassword1")
                .role(User.Role.DIRECTOR)
                .build());

        userRepository.save(User.builder()
                .email("staff1@example.com")
                .passwordHash("$2a$10$hashedpassword2")
                .role(User.Role.STAFF)
                .build());

        userRepository.save(User.builder()
                .email("staff2@example.com")
                .passwordHash("$2a$10$hashedpassword3")
                .role(User.Role.STAFF)
                .build());

        userRepository.save(User.builder()
                .email("family@example.com")
                .passwordHash("$2a$10$hashedpassword4")
                .role(User.Role.FAMILY_MEMBER)
                .build());
    }

    @Test
    void findByEmail_returnsUser_whenEmailExists() {
        Optional<User> result = userRepository.findByEmail("director@example.com");

        assertThat(result).isPresent();
        assertThat(result.get().getEmail()).isEqualTo("director@example.com");
        assertThat(result.get().getRole()).isEqualTo(User.Role.DIRECTOR);
    }

    @Test
    void findByEmail_returnsEmpty_whenEmailNotFound() {
        Optional<User> result = userRepository.findByEmail("unknown@example.com");

        assertThat(result).isEmpty();
    }

    @Test
    void findByRole_returnsAllUsersWithGivenRole() {
        List<User> staffUsers = userRepository.findByRole(User.Role.STAFF);

        assertThat(staffUsers).hasSize(2);
        assertThat(staffUsers).allMatch(u -> u.getRole() == User.Role.STAFF);
    }

    @Test
    void findByRole_returnsEmpty_whenNoUsersWithRole() {
        userRepository.deleteAll();
        List<User> result = userRepository.findByRole(User.Role.DIRECTOR);

        assertThat(result).isEmpty();
    }
}
