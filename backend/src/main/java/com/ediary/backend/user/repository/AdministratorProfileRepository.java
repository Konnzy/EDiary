package com.ediary.backend.user.repository;

import com.ediary.backend.user.entity.AdministratorProfile;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdministratorProfileRepository extends JpaRepository<AdministratorProfile, Long> {

    @EntityGraph(attributePaths = "user")
    Optional<AdministratorProfile> findByUserLoginIgnoreCase(String login);
}
