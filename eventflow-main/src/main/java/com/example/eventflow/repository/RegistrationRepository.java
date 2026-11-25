package com.example.eventflow.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.eventflow.model.Registration;
import com.example.eventflow.model.RegistrationStatus;

@Repository
public interface RegistrationRepository extends JpaRepository<Registration, Long> {

    Optional<Registration> findByEventIdAndUserId(Long eventId, Long userId);

    Optional<Registration> findByTicketId(String ticketId);

    boolean existsByTicketId(String ticketId);

    long countByEventIdAndStatus(Long eventId, RegistrationStatus status);

    @Query("select count(r) from Registration r where r.event.id = :eventId and r.checkedInAt is not null")
    long countCheckedIn(@Param("eventId") Long eventId);

    List<Registration> findByUserIdOrderByRegisteredAtDesc(Long userId);

    List<Registration> findByEventIdOrderByRegisteredAtAsc(Long eventId);

    /** Next person in line: oldest queue time first, id as tie-breaker. */
    Optional<Registration> findFirstByEventIdAndStatusOrderByQueuedAtAscIdAsc(Long eventId, RegistrationStatus status);

    @Query("select r.event.id from Registration r where r.id = :id")
    Optional<Long> findEventIdById(@Param("id") Long id);

    /** Number of waitlisted people ahead of the given queue position. */
    @Query("select count(r) from Registration r where r.event.id = :eventId and r.status = :status "
            + "and r.id <> :id and (r.queuedAt < :queuedAt or (r.queuedAt = :queuedAt and r.id < :id))")
    long countAheadInQueue(@Param("eventId") Long eventId, @Param("status") RegistrationStatus status,
            @Param("queuedAt") Instant queuedAt, @Param("id") Long id);

    /**
     * Atomic check-in: only succeeds if the registration is CONFIRMED and has not been checked in
     * yet, so two simultaneous scans of the same ticket cannot both succeed.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Registration r set r.checkedInAt = :now "
            + "where r.id = :id and r.checkedInAt is null and r.status = :status")
    int markCheckedIn(@Param("id") Long id, @Param("now") Instant now, @Param("status") RegistrationStatus status);

    @Modifying
    @Query("delete from Registration r where r.event.id = :eventId")
    void deleteByEventId(@Param("eventId") Long eventId);

    /** Per event and status: [eventId, status, count, checkedInCount]. */
    @Query("select r.event.id, r.status, count(r), sum(case when r.checkedInAt is not null then 1 else 0 end) "
            + "from Registration r where r.event.id in :eventIds group by r.event.id, r.status")
    List<Object[]> summarizeByEvent(@Param("eventIds") Collection<Long> eventIds);

    @Query("select r.registeredAt from Registration r where r.event.id in :eventIds")
    List<Instant> findRegisteredAtByEventIds(@Param("eventIds") Collection<Long> eventIds);
}
