package com.annotation.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "roles")
@Data
@NoArgsConstructor
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String role;


    public Role(String name) {
        this.role = name;
    }

    // Existing getters
    public Long getId() {
        return id;
    }

    public String getRole() {
        return role;
    }
}
