package com.annotation.model;

import jakarta.persistence.*;
import lombok.*;


@Entity
@Getter
@Setter
@EqualsAndHashCode(of = "id")
@ToString(exclude = {"user", "coupleText"})
@AllArgsConstructor
@NoArgsConstructor
public class Annotation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "class_choisie", nullable = false)
    private String chosenClass;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id") // Ensure this matches the column in the database
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "couple_id") // Ensure this matches the column in the database
    private CoupleText coupleText;

    // Helper method
    public Dataset getDataset() {
        return coupleText != null ? coupleText.getDataset() : null;
    }
}
