package com.ediary.backend.user.admin.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_import_lock")
public class UserImportLock {
    @Id
    private Integer id;

    protected UserImportLock() {}
}
