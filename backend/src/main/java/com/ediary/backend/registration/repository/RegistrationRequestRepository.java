package com.ediary.backend.registration.repository;

import com.ediary.backend.registration.entity.RegistrationRequest;
import com.ediary.backend.registration.entity.RegistrationStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RegistrationRequestRepository extends JpaRepository<RegistrationRequest, Long> {

    boolean existsByEmailIgnoreCaseAndStatus(String email, RegistrationStatus status);

    long countByStatus(RegistrationStatus status);

    @EntityGraph(attributePaths = {"requestedClass", "requestedSubject", "requestedStudent.user", "requestedStudent.schoolClass"})
    List<RegistrationRequest> findTop4ByOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = {"requestedClass", "requestedSubject", "requestedStudent.user", "requestedStudent.schoolClass", "reviewedBy", "processedUser"})
    Page<RegistrationRequest> findAllByStatus(RegistrationStatus status, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"requestedClass", "requestedSubject", "requestedStudent.user", "requestedStudent.schoolClass", "reviewedBy", "processedUser"})
    Page<RegistrationRequest> findAll(Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select request
        from RegistrationRequest request
        left join fetch request.requestedClass
        left join fetch request.requestedSubject
        left join fetch request.requestedStudent student
        left join fetch student.user
        left join fetch student.schoolClass
        left join fetch request.reviewedBy
        left join fetch request.processedUser
        where request.id = :id
        """)
    Optional<RegistrationRequest> findByIdForUpdate(@Param("id") Long id);
}
