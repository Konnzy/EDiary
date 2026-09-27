package com.ediary.backend.registration.repository;

import com.ediary.backend.registration.entity.RegistrationRequest;
import com.ediary.backend.registration.entity.RegistrationStatus;
import com.ediary.backend.user.entity.UserRole;
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

    long deleteByRequestedStudent_User_Id(Long userId);

    long deleteByProcessedUser_Id(Long userId);

    boolean existsByEmailIgnoreCaseAndStatus(String email, RegistrationStatus status);

    long countByStatus(RegistrationStatus status);

    @EntityGraph(attributePaths = {"requestedClass", "requestedSubject", "requestedStudent.user", "requestedStudent.schoolClass"})
    List<RegistrationRequest> findTop4ByOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = {"requestedClass", "requestedSubject", "requestedStudent.user", "requestedStudent.schoolClass", "reviewedBy", "processedUser"})
    Page<RegistrationRequest> findAllByStatus(RegistrationStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"requestedClass", "requestedSubject", "requestedStudent.user", "requestedStudent.schoolClass", "reviewedBy", "processedUser"})
    Page<RegistrationRequest> findAllByStatusNot(RegistrationStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"requestedClass", "requestedSubject", "requestedStudent.user", "requestedStudent.schoolClass", "reviewedBy", "processedUser"})
    @Query(value = """
        select request from RegistrationRequest request
        order by case when cast(request.status as string) = 'PENDING' then 0 else 1 end,
                 request.createdAt desc, request.id desc
        """, countQuery = "select count(request) from RegistrationRequest request")
    Page<RegistrationRequest> findAllWithPendingPriority(Pageable pageable);

    @EntityGraph(attributePaths = {"requestedClass", "requestedSubject", "requestedStudent.user", "requestedStudent.schoolClass", "reviewedBy", "processedUser"})
    @Query(value = """
        select request from RegistrationRequest request
        where request.status = :status
        order by case when cast(request.status as string) = 'PENDING' then 0 else 1 end,
                 request.createdAt desc, request.id desc
        """, countQuery = "select count(request) from RegistrationRequest request where request.status = :status")
    Page<RegistrationRequest> findAllWithPendingPriorityByStatus(@Param("status") RegistrationStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"requestedClass", "requestedSubject", "requestedStudent.user", "requestedStudent.schoolClass", "reviewedBy", "processedUser"})
    @Query(value = """
        select request from RegistrationRequest request
        where request.requestedRole = :role
        order by case when cast(request.status as string) = 'PENDING' then 0 else 1 end,
                 request.createdAt desc, request.id desc
        """, countQuery = "select count(request) from RegistrationRequest request where request.requestedRole = :role")
    Page<RegistrationRequest> findAllWithPendingPriorityByRole(@Param("role") UserRole role, Pageable pageable);

    @EntityGraph(attributePaths = {"requestedClass", "requestedSubject", "requestedStudent.user", "requestedStudent.schoolClass", "reviewedBy", "processedUser"})
    @Query(value = """
        select request from RegistrationRequest request
        where request.status = :status and request.requestedRole = :role
        order by case when cast(request.status as string) = 'PENDING' then 0 else 1 end,
                 request.createdAt desc, request.id desc
        """, countQuery = "select count(request) from RegistrationRequest request where request.status = :status and request.requestedRole = :role")
    Page<RegistrationRequest> findAllWithPendingPriorityByStatusAndRole(
        @Param("status") RegistrationStatus status,
        @Param("role") UserRole role,
        Pageable pageable
    );

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
