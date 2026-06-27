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
import io.inquisitor.demo.web.dto.AccountFilter;
import io.inquisitor.demo.web.dto.CreateAccountRequest;
import io.inquisitor.demo.web.dto.TransactionFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * The {@link AccountService} the controllers actually call (it is {@code @Primary}).
 * It only <em>routes</em>: each operation goes to the clean {@link AccountServiceImpl}
 * unless its {@link Bug} is enabled, in which case it goes to the defective
 * {@link BuggyAccountServiceImpl} — a strategy switch with a facade in front. With no
 * bugs enabled (the default, and always so for {@code bootRun} and the positive
 * suites) every call routes to the clean implementation, so behaviour is unchanged.
 *
 * <p>The fault-detection suite enables a bug before running a correct scenario and
 * resets afterwards; see {@code tasks/task-07-fault-detection.md}.
 */
@Service
@Primary
@RequiredArgsConstructor
public class AccountServiceRouter implements AccountService {

    private final AccountServiceImpl clean;
    private final BuggyAccountServiceImpl buggy;

    private final Set<Bug> activeBugs = EnumSet.noneOf(Bug.class);

    public void enableBug(Bug bug) {
        activeBugs.add(bug);
    }

    public void disableBug(Bug bug) {
        activeBugs.remove(bug);
    }

    public void disableAllBugs() {
        activeBugs.clear();
    }

    /** The clean implementation, or the buggy one when {@code bug} is enabled. */
    private AccountService route(Bug bug) {
        return activeBugs.contains(bug) ? buggy : clean;
    }

    @Override
    public Account createAccount(String owner, String currency) {
        return route(Bug.WRONG_CURRENCY).createAccount(owner, currency);
    }

    @Override
    public Account deposit(Long accountId, BigDecimal amount) {
        return route(Bug.DEPOSIT_NOT_PERSISTED).deposit(accountId, amount);
    }

    @Override
    public TransferSummary transfer(Long fromId, Long toId, BigDecimal amount) {
        return route(Bug.TRANSFER_CREDIT_DROPPED).transfer(fromId, toId, amount);
    }

    // Operations with no seeded bug always use the clean implementation.

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
