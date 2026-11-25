package com.example.eventflow.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.eventflow.model.AppUser;

@Repository
public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByUsnAndIdNot(String usn, Long id);

    boolean existsByUsn(String usn);

    long countByRole(com.example.eventflow.model.Role role);
}
