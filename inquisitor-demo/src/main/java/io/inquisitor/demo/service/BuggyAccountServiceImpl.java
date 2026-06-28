/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.inquisitor.demo.service;

import io.inquisitor.demo.model.Account;
import io.inquisitor.demo.model.Transaction;
import io.inquisitor.demo.model.TransactionType;
import io.inquisitor.demo.repository.AccountRepository;
import io.inquisitor.demo.repository.TransactionRepository;
import io.inquisitor.demo.web.dto.AccountFilter;
import io.inquisitor.demo.web.dto.CreateAccountRequest;
import io.inquisitor.demo.web.dto.TransactionFilter;
import lombok.RequiredArgsConstructor;
import lombok.val;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * A deliberately defective {@link AccountService}: each overridden operation
 * reproduces the matching {@link Bug}. The {@link AccountServiceRouter} routes to
 * this implementation only while that bug is enabled; the operations without a bug
 * delegate to the clean {@link AccountServiceImpl}, so the only thing wrong is the
 * one seeded defect. Used purely by the fault-detection suite — see
 * {@code tasks/task-07-fault-detection.md}.
 */
@Service
@RequiredArgsConstructor
public class BuggyAccountServiceImpl implements AccountService {

    private final AccountServiceImpl clean;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    /** {@link Bug#WRONG_CURRENCY}: persist a currency other than the one requested. */
    @Override
    public Account createAccount(String owner, String currency) {
        val wrong = currency.equalsIgnoreCase("EUR") ? "USD" : "EUR";
        return clean.createAccount(owner, wrong);
    }

    /** {@link Bug#DEPOSIT_NOT_PERSISTED}: record the transaction but never apply it to the balance. */
    @Override
    @Transactional
    public Account deposit(Long accountId, BigDecimal amount) {
        clean.findById(accountId); // 404 if absent, like the real path
        transactionRepository.save(Transaction.builder()
                .accountId(accountId)
                .type(TransactionType.DEPOSIT)
                .amount(amount)
                .build());
        return clean.findById(accountId);
    }

    /** {@link Bug#TRANSFER_CREDIT_DROPPED}: debit the source but never credit the destination. */
    @Override
    @Transactional
    public TransferSummary transfer(Long fromId, Long toId, BigDecimal amount) {
        val from = clean.findById(fromId);
        clean.findById(toId); // 404 if absent, like the real path
        if (from.balance().compareTo(amount) < 0) {
            throw new InsufficientFundsException(fromId, from.balance(), amount);
        }
        val reference = UUID.randomUUID().toString();
        accountRepository.save(from.debit(amount));
        // BUG: the destination is never credited.
        transactionRepository.save(Transaction.builder()
                .accountId(fromId)
                .type(TransactionType.TRANSFER_OUT)
                .amount(amount)
                .reference(reference)
                .build());
        transactionRepository.save(Transaction.builder()
                .accountId(toId)
                .type(TransactionType.TRANSFER_IN)
                .amount(amount)
                .reference(reference)
                .build());
        return new TransferSummary(reference, fromId, toId, amount);
    }

    // Operations without a seeded bug delegate unchanged to the clean implementation.

    @Override
    public List<Account> importAccounts(List<CreateAccountRequest> requests) {
        return clean.importAccounts(requests);
    }

    @Override
    public Account findById(Long id) {
        return clean.findById(id);
    }

    @Override
    public Page<Account> searchAccounts(AccountFilter filter, Pageable pageable) {
        return clean.searchAccounts(filter, pageable);
    }

    @Override
    public Account withdraw(Long accountId, BigDecimal amount) {
        return clean.withdraw(accountId, amount);
    }

    @Override
    public Page<Transaction> getTransactions(Long accountId, TransactionFilter filter, Pageable pageable) {
        return clean.getTransactions(accountId, filter, pageable);
    }
}
