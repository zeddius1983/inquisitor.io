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
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccountService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    @Transactional
    public Account createAccount(String owner, String currency) {
        return accountRepository.save(Account.open(owner, currency));
    }

    /** Bulk-opens accounts, e.g. from a CSV or plain-text import. */
    @Transactional
    public List<Account> importAccounts(List<CreateAccountRequest> requests) {
        return requests.stream()
                .map(request -> accountRepository.save(Account.open(request.owner(), request.currency())))
                .toList();
    }

    public Account findById(Long id) {
        return accountRepository.findById(id)
                .orElseThrow(() -> new AccountNotFoundException(id));
    }

    public Page<Account> searchAccounts(AccountFilter filter, Pageable pageable) {
        val items = accountRepository.search(filter.owner(), filter.currency(), pageable);
        return PageableExecutionUtils.getPage(items, pageable,
                () -> accountRepository.countSearch(filter.owner(), filter.currency()));
    }

    @Transactional
    public Account deposit(Long accountId, BigDecimal amount) {
        val account = findById(accountId);
        accountRepository.save(account.credit(amount));
        transactionRepository.save(Transaction.builder()
                .accountId(accountId)
                .type(TransactionType.DEPOSIT)
                .amount(amount)
                .build());
        return findById(accountId);
    }

    @Transactional
    public Account withdraw(Long accountId, BigDecimal amount) {
        val account = findById(accountId);
        if (account.balance().compareTo(amount) < 0) {
            throw new InsufficientFundsException(accountId, account.balance(), amount);
        }
        accountRepository.save(account.debit(amount));
        transactionRepository.save(Transaction.builder()
                .accountId(accountId)
                .type(TransactionType.WITHDRAWAL)
                .amount(amount)
                .build());
        return findById(accountId);
    }

    @Transactional
    public TransferSummary transfer(Long fromId, Long toId, BigDecimal amount) {
        val from = findById(fromId);
        val to   = findById(toId);
        if (from.balance().compareTo(amount) < 0) {
            throw new InsufficientFundsException(fromId, from.balance(), amount);
        }
        val reference = UUID.randomUUID().toString();
        accountRepository.save(from.debit(amount));
        accountRepository.save(to.credit(amount));
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

    public Page<Transaction> getTransactions(Long accountId, TransactionFilter filter, Pageable pageable) {
        findById(accountId);
        val type = filter.type() != null ? filter.type().name() : null;
        val items = transactionRepository.search(accountId, type, filter.from(), filter.to(), pageable);
        return PageableExecutionUtils.getPage(items, pageable,
                () -> transactionRepository.countSearch(accountId, type, filter.from(), filter.to()));
    }

    public record TransferSummary(String reference, Long fromId, Long toId, BigDecimal amount) {}
}
