package org.example.client;

import lombok.RequiredArgsConstructor;
import org.example.dto.Dto;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/client")
@RequiredArgsConstructor
public class ClientController {
    
    private final ClientService clientService;
    
    @GetMapping
    public List<Dto.ClientDto> all() {
        return clientService.retrieveAllClients();
    }
    
    @GetMapping("/{id}")
    public Dto.ClientDto byId(@PathVariable UUID id) {
        return clientService.getById(id);
    }
    
    @PostMapping
    public Dto.ClientDto create(@RequestBody Dto.CreateClientRequest request) {
        return clientService.createClient(request);
    }
    
    @PutMapping("/{id}")
    public Dto.ClientDto update(@PathVariable UUID id, @RequestBody Dto.CreateClientRequest request) {
        return clientService.updateClient(id, request);
    }
    
    @DeleteMapping("/{id}")
    public void delete(@PathVariable UUID id) {
        clientService.deleteClient(id);
    }
}
