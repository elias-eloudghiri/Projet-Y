package com.example.back.room;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomRepository extends JpaRepository<Room, UUID> {

    boolean existsByCode(String code);

    @EntityGraph(attributePaths = {"story", "participants"})
    Optional<Room> findByCode(String code);
}
