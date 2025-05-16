package com.annotation.model;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.SqlResultSetMapping;
import jakarta.persistence.ConstructorResult;
import jakarta.persistence.ColumnResult;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "tasks")
@Getter
@Setter
@EqualsAndHashCode(of = "id")
@ToString(exclude = {"couples", "user"})
@AllArgsConstructor
@NoArgsConstructor
@SqlResultSetMapping(
    name = "TaskDetailMapping",
    columns = {
        @ColumnResult(name = "id", type = Long.class),
        @ColumnResult(name = "date_limite", type = Date.class),
        @ColumnResult(name = "user_id", type = Long.class),
        @ColumnResult(name = "dataset_id", type = Long.class),
        @ColumnResult(name = "firstname", type = String.class),
        @ColumnResult(name = "lastname", type = String.class),
        @ColumnResult(name = "dataset_name", type = String.class)
    }
)
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "date_limite", nullable = false)
    private Date dateLimite;
    
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id")
    private User user;

    // Hibernate will create a join table between tasks and text pairs
    @ManyToMany(cascade = CascadeType.PERSIST, fetch = FetchType.LAZY)
    @JoinTable(
        name = "task_couple",
        joinColumns = @JoinColumn(name = "task_id"),
        inverseJoinColumns = @JoinColumn(name = "couple_id")
    )
    private List<CoupleText> couples = new ArrayList<>();
    
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "dataset_id")
    private Dataset dataset;
    
    // Helper methods for relationship management
    public void addCouple(CoupleText couple) {
        if (couples == null) {
            couples = new ArrayList<>();
        }
        couples.add(couple);
    }
    
    public void removeCoupleText(CoupleText couple) {
        if (couples != null) {
            couples.remove(couple);
        }
    }
} 