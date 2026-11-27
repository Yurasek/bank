package org.example;

import org.example.dto.Dto;
import org.example.mapping.EntityMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class BankService {
    @Autowired
    ClientRepository clientRepository;

    @Autowired
    EntityMapper entityMapper;

    public BankService(ClientRepository clientRepository) {
        this.clientRepository = clientRepository;
    }

//    @Transactional
    public List<Dto.ClientDto> retrieveAllClients(){
        return clientRepository.findAll().stream().map(it -> entityMapper.toDto(it)).toList();
    }
}
