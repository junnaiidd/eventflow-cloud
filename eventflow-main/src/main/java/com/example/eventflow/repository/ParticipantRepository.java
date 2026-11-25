package com.example.eventflow.repository;

import com.example.eventflow.model.Participant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository interface for Participant entity
 */
@Repository
public interface ParticipantRepository extends JpaRepository<Participant, Long> {

    // Find participants by event ID
    List<Participant> findByEventId(Long eventId);

    // Find participants by role
    List<Participant> findByRole(String role);
}