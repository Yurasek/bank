package org.example.mapping;

import org.example.Account;
import org.example.Client;
import org.example.dto.Dto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface EntityMapper {

    // Auto-generated implementation at compile time
//    @Mapping(target = "id", ignore = true)
    Dto.ClientDto toDto(Client client);

    // Custom mapping for nested list + enum→String
    @Mapping(target = "accountType", expression = "java(entity.getAccountType().name())")
//    @Mapping(target = "id", ignore = true)
    Dto.AccountDto toDto(Account entity);

    // DTO → Entity (for creates/updates — partial, you handle ID separately)
    @Mapping(target = "accounts", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "id", ignore = true)
    Client toEntity(Dto.ClientDto dto);
}
