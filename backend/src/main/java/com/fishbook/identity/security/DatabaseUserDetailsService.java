package com.fishbook.identity.security;

import com.fishbook.identity.domain.User;
import com.fishbook.identity.domain.UserRepository;
import com.fishbook.identity.domain.UserStatus;
import java.util.Locale;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public final class DatabaseUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public DatabaseUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        String normalizedEmail = username.trim().toLowerCase(Locale.ROOT);
        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));

        return org.springframework.security.core.userdetails.User
                .withUsername(user.email())
                .password(user.passwordHash())
                .roles(user.role().name())
                .disabled(user.status() != UserStatus.ACTIVE)
                .build();
    }
}
