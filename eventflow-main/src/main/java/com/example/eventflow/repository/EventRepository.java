package com.example.eventflow.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;

import com.example.eventflow.model.Event;

/**
 * Repository interface for Event entity
 * Provides CRUD operations and custom queries
 */
@Repository
public interface EventRepository extends JpaRepository<Event, Long> {

    /**
     * Loads the event with a row lock. Registration, cancellation and waitlist promotion all take this
     * lock first, so concurrent requests for the same event are processed one at a time and capacity
     * can never be exceeded.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM Event e WHERE e.id = :id")
    Optional<Event> findByIdForUpdate(@Param("id") Long id);

    List<Event> findByOrganizerId(Long organizerId);

    List<Event> findByStatus(String status);

    List<Event> findByType(String type);

    List<Event> findByEventDateBetween(LocalDate startDate, LocalDate endDate);

    List<Event> findByEventDateAfterOrderByEventDateAsc(LocalDate date);

    @Query("SELECT e FROM Event e WHERE YEAR(e.eventDate) = :year AND MONTH(e.eventDate) = :month ORDER BY e.eventDate")
    List<Event> findByMonthAndYear(@Param("year") int year, @Param("month") int month);

    @Query("SELECT SUM(e.budget) FROM Event e")
    Double getTotalBudget();

    @Query("SELECT SUM(e.attendees) FROM Event e")
    Integer getTotalAttendees();

    Long countByStatus(String status);

    List<Event> findByNameContainingIgnoreCase(String name);

    @Query("SELECT e FROM Event e WHERE " +
            "LOWER(e.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(e.location) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(e.type) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(e.description) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
            "ORDER BY e.eventDate DESC")
    List<Event> searchEventsAcrossFields(@Param("searchTerm") String searchTerm);
}
