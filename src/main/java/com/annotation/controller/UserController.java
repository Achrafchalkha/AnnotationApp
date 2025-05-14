package com.annotation.controller;

import com.annotation.model.Role;
import com.annotation.model.User;
import com.annotation.repository.RoleRepository;
import com.annotation.repository.UserRepository;
import com.annotation.service.DefaultUserServiceImpl;
import com.annotation.service.UserManagementService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.Optional;
import java.util.Random;

@Controller
@RequestMapping("/admin/users")
@PreAuthorize("hasAuthority('ADMIN')")  // Ensure only ADMIN users can access this controller
public class UserController {
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private RoleRepository roleRepository;
    
    @Autowired
    private DefaultUserServiceImpl userService;
    
    @Autowired
    private UserManagementService userManagementService;
    
    @Autowired
    private BCryptPasswordEncoder passwordEncoder;
    
    // Display list of annotators
    @GetMapping
    public String listAnnotators(Model model) {
        // Get current authenticated user
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ADMIN"))) {
            return "redirect:/dashboard";
        }
        
        String currentUserName = userService.getCurrentUserName();
        Optional<Role> userRoleOpt = roleRepository.findByRole("USER");
        
        if (!userRoleOpt.isPresent()) {
            model.addAttribute("error", "Role 'USER' not found in the system");
            model.addAttribute("currentUserName", currentUserName);
            model.addAttribute("annotators", List.of());
            return "admin/users_management/annotators";
        }
        
        Role userRole = userRoleOpt.get();
        List<User> annotators = userRepository.findAllByRolesContaining(userRole);
        
        model.addAttribute("currentUserName", currentUserName);
        model.addAttribute("annotators", annotators);
        return "admin/users_management/annotators";
    }
    
    // Display form to add a new annotator
    @GetMapping("/add")
    public String showAddAnnotatorForm(Model model) {
        // Check user has ADMIN role
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ADMIN"))) {
            return "redirect:/dashboard";
        }
        
        String currentUserName = userService.getCurrentUserName();
        model.addAttribute("currentUserName", currentUserName);
        model.addAttribute("user", new User());
        return "admin/users_management/add_annotator";
    }
    
    // Process the form to add a new annotator
    @PostMapping("/save")
    public String saveAnnotator(@ModelAttribute User user, RedirectAttributes redirectAttributes) {
        // Check user has ADMIN role
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ADMIN"))) {
            return "redirect:/dashboard";
        }
        
        try {
            // Generate a random password
            String generatedPassword = generateRandomPassword();
            String rawPassword = generatedPassword; // Save unencrypted version for display
            
            // Encrypt the password
            user.setPassword(passwordEncoder.encode(generatedPassword));
            
            // Assign USER role
            Optional<Role> userRoleOpt = roleRepository.findByRole("USER");
            if (!userRoleOpt.isPresent()) {
                redirectAttributes.addFlashAttribute("error", "Role 'USER' not found in the system");
                return "redirect:/admin/users/add";
            }
            
            Role userRole = userRoleOpt.get();
            Set<Role> roles = new HashSet<>();
            roles.add(userRole);
            user.setRoles(roles);
            
            // Save user
            userRepository.save(user);
            
            // Add success message with generated password
            redirectAttributes.addFlashAttribute("success", 
                "Annotator added successfully. The generated password is: " + rawPassword);
                
            return "redirect:/admin/users";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to add annotator: " + e.getMessage());
            return "redirect:/admin/users/add";
        }
    }
    
    // Display form to edit an annotator
    @GetMapping("/edit/{id}")
    public String showEditAnnotatorForm(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        // Check user has ADMIN role
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ADMIN"))) {
            return "redirect:/dashboard";
        }
        
        User user = userRepository.findById(id).orElse(null);
        if (user == null) {
            redirectAttributes.addFlashAttribute("error", "Annotator not found");
            return "redirect:/admin/users";
        }
        
        String currentUserName = userService.getCurrentUserName();
        model.addAttribute("currentUserName", currentUserName);
        model.addAttribute("user", user);
        return "admin/users_management/edit_annotator";
    }
    
    // Process the form to update an annotator
    @PostMapping("/update")
    public String updateAnnotator(@ModelAttribute User user, @RequestParam(required = false) Boolean resetPassword, 
                                  RedirectAttributes redirectAttributes) {
        // Check user has ADMIN role
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ADMIN"))) {
            return "redirect:/dashboard";
        }
        
        try {
            User existingUser = userRepository.findById(user.getId()).orElse(null);
            if (existingUser == null) {
                redirectAttributes.addFlashAttribute("error", "Annotator not found");
                return "redirect:/admin/users";
            }
            
            // Update basic info
            existingUser.setFirstname(user.getFirstname());
            existingUser.setLastname(user.getLastname());
            existingUser.setUsername(user.getUsername());
            
            // Reset password if requested
            if (resetPassword != null && resetPassword) {
                String generatedPassword = generateRandomPassword();
                String rawPassword = generatedPassword; // Save unencrypted version for display
                existingUser.setPassword(passwordEncoder.encode(generatedPassword));
                redirectAttributes.addFlashAttribute("generatedPassword", rawPassword);
            }
            
            // Save updated user
            userRepository.save(existingUser);
            
            redirectAttributes.addFlashAttribute("success", "Annotator updated successfully");
            return "redirect:/admin/users";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to update annotator: " + e.getMessage());
            return "redirect:/admin/users";
        }
    }
    
    // Delete an annotator
    @PostMapping("/delete/{id}")
    public String deleteAnnotator(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        // Check user has ADMIN role
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ADMIN"))) {
            return "redirect:/dashboard";
        }
        
        try {
            User user = userRepository.findById(id).orElse(null);
            if (user == null) {
                redirectAttributes.addFlashAttribute("error", "Annotator not found");
                return "redirect:/admin/users";
            }
            
            // Delete the user
            userRepository.deleteById(id);
            redirectAttributes.addFlashAttribute("success", "Annotator deleted successfully");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to delete annotator: " + e.getMessage());
        }
        
        return "redirect:/admin/users";
    }
    
    // Helper method to generate a random password
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