package com.toeic.backend.identity.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "permissions")
public class Permission {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "permission_id")
    private Integer permissionId;

    @Column(nullable = false, unique = true, length = 100)
    private String code;

    @Column(columnDefinition = "TEXT")
    private String description;

    protected Permission() {
    }

    public Integer getPermissionId() { return permissionId; }
    public String getCode() { return code; }
    public String getDescription() { return description; }
}
