package org.example;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.*;

@Repository
public interface ClientRepository extends CrudRepository<Client, UUID> {
     List<Client> findAll();
    
    boolean existsByEmail(String email);
    
    Optional<Client> findByEmail(String email);
}
