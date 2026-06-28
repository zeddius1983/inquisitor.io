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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;

/**
 * Banking operations the demo exposes. Extracted as an interface so a test-scoped
 * fault router can wrap the real {@link AccountServiceImpl} and inject deliberate
 * defects for oracle-calibration scenarios — see {@code tasks/task-07-fault-detection.md}.
 * Production and the positive suites only ever see {@link AccountServiceImpl}.
 */
public interface AccountService {

    Account createAccount(String owner, String currency);

    /** Bulk-opens accounts, e.g. from a CSV or plain-text import. */
    List<Account> importAccounts(List<CreateAccountRequest> requests);

    Account findById(Long id);

    Page<Account> searchAccounts(AccountFilter filter, Pageable pageable);

    Account deposit(Long accountId, BigDecimal amount);

    Account withdraw(Long accountId, BigDecimal amount);

    TransferSummary transfer(Long fromId, Long toId, BigDecimal amount);

    Page<Transaction> getTransactions(Long accountId, TransactionFilter filter, Pageable pageable);

    record TransferSummary(String reference, Long fromId, Long toId, BigDecimal amount) {}
}
