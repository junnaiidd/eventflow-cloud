package com.example.eventflow.dto;

/**
 * Data Transfer Object for Participant
 */
public class ParticipantDTO {

    private Long id;
    private String name;
    private String role;

    // No-args constructor
    public ParticipantDTO() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public static ParticipantDTOBuilder builder() {
        return new ParticipantDTOBuilder();
    }

    public static class ParticipantDTOBuilder {
        private Long id;
        private String name;
        private String role;

        public ParticipantDTOBuilder id(Long id) {
            this.id = id;
            return this;
        }

        public ParticipantDTOBuilder name(String name) {
            this.name = name;
            return this;
        }

        public ParticipantDTOBuilder role(String role) {
            this.role = role;
            return this;
        }

        public ParticipantDTO build() {
            ParticipantDTO dto = new ParticipantDTO();
            dto.id = this.id;
            dto.name = this.name;
            dto.role = this.role;
            return dto;
        }
    }
}