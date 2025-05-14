package com.annotation.repository;


import com.annotation.model.Role;
import com.annotation.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    
    // Find all users with a specific role
    List<User> findAllByRolesContaining(Role role);
}
