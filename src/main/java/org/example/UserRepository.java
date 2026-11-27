package org.example;

import org.springframework.data.jpa.repository.*;

import java.util.*;

public interface UserRepository extends JpaRepository<User, UUID> {

//    Optional<User> findByLogin(String login);

    boolean existsBySecondNameAndFirstName(String first, String second);
    Optional<User> findByFirstNameAndSecondName(String first, String second);
}
