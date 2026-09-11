package com.crowdfunding.backend.services;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.crowdfunding.backend.login.UserAuthDetails;
import com.crowdfunding.backend.persistence.UserRepository;

@Service
public class CustomUserDetailsService implements UserDetailsService {
    private final UserRepository repository;

    public CustomUserDetailsService(UserRepository repository) {
        this.repository = repository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UserAuthDetails result = repository.findByEmail(username).map(
                existingUser -> new UserAuthDetails(existingUser))
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado"));

        return result;
    }

}
