package com.annotation.service;

import com.annotation.model.Role;
import com.annotation.model.User;
import com.annotation.repository.RoleRepository;
import com.annotation.repository.UserRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

@Service
public class UserManagementService {

    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private RoleRepository roleRepository;
    
    @Autowired
    private BCryptPasswordEncoder passwordEncoder;
    
    /**
     * Find all users with the USER role (annotators)
     */
    public List<User> findAllAnnotators() {
        Optional<Role> userRoleOpt = roleRepository.findByRole("USER");
        if (!userRoleOpt.isPresent()) {
            throw new RuntimeException("Role 'USER' not found in the system");
        }
        Role userRole = userRoleOpt.get();
        return userRepository.findAllByRolesContaining(userRole);
    }
    
    /**
     * Create a new annotator with a randomly generated password
     */
    @Transactional
    public User createAnnotator(String firstname, String lastname, String username) {
        User user = new User();
        user.setFirstname(firstname);
        user.setLastname(lastname);
        user.setUsername(username);
        
        // Generate random password
        String generatedPassword = generateRandomPassword();
        user.setPassword(passwordEncoder.encode(generatedPassword));
        
        // Assign USER role
        Optional<Role> userRoleOpt = roleRepository.findByRole("USER");
        if (!userRoleOpt.isPresent()) {
            throw new RuntimeException("Role 'USER' not found in the system");
        }
        Role userRole = userRoleOpt.get();
        Set<Role> roles = new HashSet<>();
        roles.add(userRole);
        user.setRoles(roles);
        
        return userRepository.save(user);
    }
    
    /**
     * Update an existing annotator
     */
    @Transactional
    public User updateAnnotator(Long id, String firstname, String lastname, String username) {
        User user = userRepository.findById(id).orElse(null);
        if (user == null) {
            throw new RuntimeException("User not found with ID: " + id);
        }
        
        user.setFirstname(firstname);
        user.setLastname(lastname);
        user.setUsername(username);
        
        return userRepository.save(user);
    }
    
    /**
     * Reset password for an annotator
     */
    @Transactional
    public String resetPassword(Long id) {
        User user = userRepository.findById(id).orElse(null);
        if (user == null) {
            throw new RuntimeException("User not found with ID: " + id);
        }
        
        String generatedPassword = generateRandomPassword();
        user.setPassword(passwordEncoder.encode(generatedPassword));
        userRepository.save(user);
        
        return generatedPassword;
    }
    
    /**
     * Delete an annotator
     */
    @Transactional
    public void deleteAnnotator(Long id) {
        User user = userRepository.findById(id).orElse(null);
        if (user == null) {
            throw new RuntimeException("User not found with ID: " + id);
        }
        
        userRepository.delete(user);
    }
    
    /**
     * Helper method to generate a random password
     */
    private String generateRandomPassword() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%";
        StringBuilder sb = new StringBuilder();
        Random random = new Random();
        
        // Generate a random password of length 10
        for (int i = 0; i < 10; i++) {
            int index = random.nextInt(chars.length());
            sb.append(chars.charAt(index));
        }
        
        return sb.toString();
    }
} 