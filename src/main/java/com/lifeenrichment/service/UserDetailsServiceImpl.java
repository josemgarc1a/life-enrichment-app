package com.lifeenrichment.service;

import com.lifeenrichment.entity.User;
import com.lifeenrichment.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Spring Security {@link UserDetailsService} implementation that loads user accounts
 * from the database by email address.
 *
 * <p>Bridges the application's {@link com.lifeenrichment.entity.User} entity to Spring
 * Security's {@link UserDetails} model. The user's role is mapped to a
 * {@code ROLE_<ROLE_NAME>} granted authority so that {@code @PreAuthorize("hasRole('...')")}
 * expressions work as expected.
 */
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    /**
     * Loads a {@link UserDetails} by email. The {@code username} parameter is treated as
     * an email address, consistent with how the JWT subject is populated.
     *
     * @param email the user's email address
     * @throws UsernameNotFoundException if no user exists with that email
     */
    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));

        return new org.springframework.security.core.userdetails.User(
                user.getEmail(),
                user.getPasswordHash(),
                user.isActive(),
                true,
                true,
                true,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
        );
    }
}
