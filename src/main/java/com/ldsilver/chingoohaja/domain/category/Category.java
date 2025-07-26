package com.ldsilver.chingoohaja.domain.category;

import com.ldsilver.chingoohaja.domain.category.enums.CategoryType;
import com.ldsilver.chingoohaja.domain.common.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "categories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Category extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private Boolean isActive;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CategoryType categoryType;

    public static Category of(
            String name,
            Boolean isActive,
            CategoryType categoryType
    ) {
        Category category = new Category();
        category.name = name;
        category.isActive = isActive;
        category.categoryType = categoryType;
        return category;
    }

    public static Category from(String name) {
        return of (name, true, CategoryType.RANDOM);
    }
}
