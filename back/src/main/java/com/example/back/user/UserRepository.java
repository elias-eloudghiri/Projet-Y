package com.example.back.user;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByPseudo(String pseudo);

    boolean existsByPseudo(String pseudo);

    boolean existsByRole(Role role);
}
