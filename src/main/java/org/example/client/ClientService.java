package org.example.client;

import lombok.RequiredArgsConstructor;
import org.example.*;
import org.example.dto.Dto;
import org.example.mapping.EntityMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ClientService {
    
    private final ClientRepository clientRepository;
    private final EntityMapper entityMapper;
    
    // ═════════════════════════════════════════════════════════════════
    // READ
    // ═════════════════════════════════════════════════════════════════
    
    @Transactional(readOnly = true)
    public List<Dto.ClientDto> retrieveAllClients() {
        return clientRepository.findAll().stream()
        .map(entityMapper::toDto)
        .toList();
    }
    
    @Transactional(readOnly = true)
    public Dto.ClientDto getById(UUID id) {
        Client client = clientRepository.findById(id)
        .orElseThrow(() -> new ResponseStatusException(
        HttpStatus.NOT_FOUND, "Client not found: " + id));
        return entityMapper.toDto(client);
    }
    
    @Transactional(readOnly = true)
    public Dto.ClientDto getByEmail(String email) {
        Client client = clientRepository.findByEmail(email)
        .orElseThrow(() -> new ResponseStatusException(
        HttpStatus.NOT_FOUND, "Client not found: " + email));
        return entityMapper.toDto(client);
    }
    
    // ═════════════════════════════════════════════════════════════════
    // CREATE
    // ═════════════════════════════════════════════════════════════════
    
    @Transactional
    public Dto.ClientDto createClient(Dto.CreateClientRequest request) {
        if (clientRepository.existsByEmail(request.email())) {
            throw new ResponseStatusException(
            HttpStatus.CONFLICT, "Email already registered: " + request.email());
        }
        
        Client client = new Client();
        client.setFirstName(request.firstName());
        client.setLastName(request.lastName());
        client.setEmail(request.email());
        
        return entityMapper.toDto(create(client));
    }
    
    @Transactional
    public Client create(Client client){
        return clientRepository.save(client);
    }
    
    // ═════════════════════════════════════════════════════════════════
    // UPDATE
    // ═════════════════════════════════════════════════════════════════
    
    /**
     * Partial update — only non-null fields are applied.
     */
    @Transactional
    public Dto.ClientDto updateClient(UUID id, Dto.CreateClientRequest request) {
        Client client = clientRepository.findById(id)
        .orElseThrow(() -> new ResponseStatusException(
        HttpStatus.NOT_FOUND, "Client not found: " + id));
        
        if (request.email() != null && !request.email().equals(client.getEmail())
        && clientRepository.existsByEmail(request.email())) {
            throw new ResponseStatusException(
            HttpStatus.CONFLICT, "Email already registered: " + request.email());
        }
        
        if (request.firstName() != null) client.setFirstName(request.firstName());
        if (request.lastName() != null)  client.setLastName(request.lastName());
        if (request.email() != null)     client.setEmail(request.email());
        
        // No explicit save needed — @Transactional dirty checking
        return entityMapper.toDto(client);
    }
    
    // ═════════════════════════════════════════════════════════════════
    // DELETE
    // ═════════════════════════════════════════════════════════════════
    
    @Transactional
    public void deleteClient(UUID id) {
        Client client = clientRepository.findById(id)
        .orElseThrow(() -> new ResponseStatusException(
        HttpStatus.NOT_FOUND, "Client not found: " + id));
        
        if (!client.getAccounts().isEmpty()) {
            throw new ResponseStatusException(
            HttpStatus.CONFLICT, "Client has accounts — close them first");
        }
        
        clientRepository.delete(client);
    }
}
