package com.ediary.backend.user.admin.repository;

import com.ediary.backend.user.admin.entity.UserImportLock;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

public interface UserImportLockRepository extends Repository<UserImportLock, Integer> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select lockRow from UserImportLock lockRow where lockRow.id = 1")
    Optional<UserImportLock> acquire();
}
