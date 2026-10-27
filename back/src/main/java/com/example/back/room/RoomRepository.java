package com.example.back.room;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoomRepository extends JpaRepository<Room, UUID> {

    boolean existsByCode(String code);

    @EntityGraph(attributePaths = {"story", "participants"})
    Optional<Room> findByCode(String code);

    /**
     * SELECT … FOR UPDATE on the room row alone (PostgreSQL refuses it on the nullable side of an
     * outer join): participants are loaded lazily afterwards, under the lock.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Room r where r.code = :code")
    Optional<Room> lockByCode(@Param("code") String code);
}
