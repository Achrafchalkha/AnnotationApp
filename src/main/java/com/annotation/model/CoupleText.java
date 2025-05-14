package com.annotation.model;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Getter
@Setter
@EqualsAndHashCode(of = {"text1", "text2", "dataset"})
@ToString(exclude = {"annotations", "assignedTasks"})
@NoArgsConstructor
@AllArgsConstructor
public class CoupleText {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "text_1", columnDefinition = "LONGTEXT")
    private String text1;

    @Column(name = "text_2", columnDefinition = "LONGTEXT")
    private String text2;

    @Column(name = "original_id")
    private Long originalId;

    // Updated to match the new name in Task entity
    @ManyToMany(mappedBy = "couples")
    private Set<Task> assignedTasks = new HashSet<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dataset_id")
    private Dataset dataset;

    @OneToMany(mappedBy = "coupleText", fetch = FetchType.LAZY)
    private List<Annotation> annotations = new ArrayList<>();

    // Copy constructor without inheriting relationships
    public CoupleText(CoupleText couple) {
        this.text1 = couple.getText1();
        this.text2 = couple.getText2();
        this.dataset = couple.getDataset();
        this.originalId = couple.getId();
    }
}
