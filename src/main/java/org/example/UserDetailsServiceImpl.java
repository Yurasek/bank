package org.example;

import lombok.*;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.*;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        org.example.User user = userRepository.findByFirstNameAndSecondName(username.split("/")[0], username.split("/")[1])
        .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        return org.springframework.security.core.userdetails.User.builder()
        .username(new StringBuilder(user.getFirstName()).append("/").append(user.getSecondName()).toString())
        .password(user.getPasswordHash())
        .build();
    }
}
