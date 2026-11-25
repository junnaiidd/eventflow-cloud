package com.example.eventflow.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.eventflow.model.Certificate;

@Repository
public interface CertificateRepository extends JpaRepository<Certificate, Long> {

    Optional<Certificate> findByRegistrationId(Long registrationId);

    Optional<Certificate> findByCertificateCode(String certificateCode);

    boolean existsByCertificateCode(String certificateCode);

    List<Certificate> findByRegistrationIdIn(Collection<Long> registrationIds);

    @Query("select c from Certificate c where c.registration.event.id = :eventId")
    List<Certificate> findByEventId(@Param("eventId") Long eventId);

    @Query("select c from Certificate c where c.registration.user.id = :userId order by c.issuedAt desc")
    List<Certificate> findByUserId(@Param("userId") Long userId);

    @Query("select c.storageKey from Certificate c where c.registration.event.id = :eventId and c.storageKey is not null")
    List<String> findStorageKeysByEventId(@Param("eventId") Long eventId);

    @Modifying
    @Query("delete from Certificate c where c.registration.event.id = :eventId")
    void deleteByEventId(@Param("eventId") Long eventId);

    @Query("select c.registration.id from Certificate c where c.registration.id in :ids")
    Collection<Long> findRegistrationIdsWithCertificate(@Param("ids") Collection<Long> ids);
}
