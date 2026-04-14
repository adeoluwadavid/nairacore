package com.adewole.nairacore.accounts.service;

import com.adewole.nairacore.accounts.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class AccountNumberGenerator {

    private static final String BANK_CODE = "0123";
    private final AccountRepository accountRepository;

    @Transactional
    public String generate() {
        Long next = accountRepository.getNextAccountSequence();
        return BANK_CODE + String.format("%06d", next);
    }
}