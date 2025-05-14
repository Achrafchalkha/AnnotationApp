package com.annotation.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.*;

@Entity
@Table(name = "tasks")
@Data
@NoArgsConstructor
public class Task {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Date dateLimite;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")  // Changed from annotateur_id to user_id to match User entity
    private User user;

    // Simplified relationship with CoupleText - using only ManyToMany
    @ManyToMany
    @JoinTable(
        name = "task_couple",      // Changed from tache_couple to task_couple for clarity
        joinColumns = @JoinColumn(name = "task_id"),  // Changed from tache_id to task_id
        inverseJoinColumns = @JoinColumn(name = "couple_id")
    )
    private List<CoupleText> couples = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dataset_id")
    private Dataset dataset;

    // Constructor with essential fields
    public Task(Date dateLimite, User user, Dataset dataset) {
        this.dateLimite = dateLimite;
        this.user = user;
        this.dataset = dataset;
    }
}
