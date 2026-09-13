package com.ediary.backend.common.dashboard;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminActivityRepository extends JpaRepository<AdminActivity, Long> {

    List<AdminActivity> findTop4ByOrderByOccurredAtDescIdDesc();
}
