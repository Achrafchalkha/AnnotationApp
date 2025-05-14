package com.annotation.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.*;

@Entity
@Table(name = "tasks")
@NoArgsConstructor
@EqualsAndHashCode(of = "id") // Only use ID for equality checks
@ToString(exclude = {"couples"}) // Exclude collections from toString to avoid circular references
public class Task {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Date dateLimite;

    @ManyToOne(fetch = FetchType.EAGER) // Changed to EAGER loading for consistent behavior
    @JoinColumn(name = "user_id")
    private User user;

    // Simplified relationship with CoupleText - using only ManyToMany
    @ManyToMany(fetch = FetchType.EAGER, cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinTable(
        name = "task_couple",      // Changed from tache_couple to task_couple for clarity
        joinColumns = @JoinColumn(name = "task_id"),  // Changed from tache_id to task_id
        inverseJoinColumns = @JoinColumn(name = "couple_id")
    )
    private List<CoupleText> couples = new ArrayList<>();

    @ManyToOne(fetch = FetchType.EAGER) // Changed to EAGER loading for consistent behavior
    @JoinColumn(name = "dataset_id")
    private Dataset dataset;

    // Constructor with essential fields
    public Task(Date dateLimite, User user, Dataset dataset) {
        this.dateLimite = dateLimite;
        this.user = user;
        this.dataset = dataset;
    }

    // Getters and setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Date getDateLimite() {
        return dateLimite;
    }

    public void setDateLimite(Date dateLimite) {
        this.dateLimite = dateLimite;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public List<CoupleText> getCouples() {
        return couples;
    }

    public void setCouples(List<CoupleText> couples) {
        this.couples = couples;
    }

    public Dataset getDataset() {
        return dataset;
    }

    public void setDataset(Dataset dataset) {
        this.dataset = dataset;
    }
}
