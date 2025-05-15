package com.annotation.model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;


@Entity
@Table(name = "dataset")
@Getter
@Setter
@EqualsAndHashCode(of = "id")
@ToString(exclude = {"tasks", "classesPossibles", "coupleTexts"})
@AllArgsConstructor
@NoArgsConstructor
public class Dataset {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private String description;
    private String filePath;
    private String fileType;


    //relation taches/dataset
    @OneToMany(mappedBy="dataset", fetch = FetchType.LAZY, cascade = CascadeType.REMOVE)
    private List<Task> tasks = new ArrayList<>();

    //relation classe/dataset
    @OneToMany(mappedBy="dataset", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<ClassPossible> classesPossibles = new HashSet<>();

    //relation coupleText/dataset
    @OneToMany(mappedBy="dataset", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<CoupleText> coupleTexts = new HashSet<>();

    // Helper methods for bidirectional relationship management
    public void addTask(Task task) {
        tasks.add(task);
        task.setDataset(this);
    }

    public void removeTask(Task task) {
        tasks.remove(task);
        task.setDataset(null);
    }

    public void addCoupleText(CoupleText coupleText) {
        coupleTexts.add(coupleText);
        coupleText.setDataset(this);
    }

    public void removeCoupleText(CoupleText coupleText) {
        coupleTexts.remove(coupleText);
        coupleText.setDataset(null);
    }

    public void addClass(ClassPossible classPossible) {
        classesPossibles.add(classPossible);
        classPossible.setDataset(this);
    }

    public void removeClass(ClassPossible classPossible) {
        classesPossibles.remove(classPossible);
        classPossible.setDataset(null);
    }
}