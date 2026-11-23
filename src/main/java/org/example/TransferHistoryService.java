package org.example;

import org.springframework.stereotype.Service;

@Service
public class TransferHistoryService {
    private TransferHistoryRepository transferHistoryRepository;

    public TransferHistoryService(TransferHistoryRepository transferHistoryRepository) {
        this.transferHistoryRepository = transferHistoryRepository;
    }

    public TransferHistory save(TransferHistory transferHistory){
        return transferHistoryRepository.save(transferHistory);
    }
}
