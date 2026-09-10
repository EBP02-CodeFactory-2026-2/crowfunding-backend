package com.crowdfunding.backend.persistence;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.crowdfunding.backend.entity.User;


public interface UserRepository extends JpaRepository<User, Long>{

    Optional<User> findByEmail(String email);

}
