package org.example;

import org.springframework.data.jpa.repository.*;

import java.time.*;
import java.util.*;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByLogin(String login);

    boolean existsByLogin(String login);
}
