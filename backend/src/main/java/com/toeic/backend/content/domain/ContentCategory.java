package com.toeic.backend.content.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "content_categories")
public class ContentCategory {
    @Id
    @Column(name = "category_id")
    private Integer categoryId;
    @Column(nullable = false, length = 50)
    private String code;
    @Column(nullable = false, length = 100)
    private String name;
    @Column(columnDefinition = "TEXT")
    private String description;

    protected ContentCategory() { }
    public Integer getCategoryId() { return categoryId; }
    public String getCode() { return code; }
    public String getName() { return name; }
}
