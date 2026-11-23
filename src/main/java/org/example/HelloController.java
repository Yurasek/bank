package org.example;

import org.example.dto.Dto;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/client")
//@CrossOrigin(origins = "http://localhost:4200")
@CrossOrigin(origins = "*")
public class HelloController {

    private BankService bankService;
    private AccountService accountService;

    public HelloController(BankService bankService, AccountService accountService) {
        this.bankService = bankService;
        this.accountService = accountService;
    }

    @GetMapping("/all")
    public List<Dto.ClientDto> index() {
        List<Integer> numbers = Arrays.asList(1, 2, 3, 4, 5, 6);
        int result = numbers
                .stream()
                .reduce(0, (subtotal, element) -> subtotal + 1);
//        assertThat(result).isEqualTo(21);
//        return String.format("Hello, World! Your plain Spring Boot app is running. %s", result);
        return bankService.retrieveAllClients();
    }

    @PostMapping("/transfer")
    public Account index(@RequestBody Dto.TransferRequest request) {

        accountService.transfer(request);

        return accountService.findById(UUID.fromString(request.fromAccountId()));
    }
}