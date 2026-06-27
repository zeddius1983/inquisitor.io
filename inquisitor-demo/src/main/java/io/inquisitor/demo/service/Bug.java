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

/**
 * A deliberate defect the {@link AccountServiceRouter} can route to. Each value
 * selects {@link BuggyAccountServiceImpl} over {@link AccountServiceImpl} for one
 * operation, so a correct scenario that exercises that operation fails — the
 * failure a trustworthy oracle must catch. See {@code tasks/task-07-fault-detection.md}.
 */
public enum Bug {

    /** {@code deposit} records the transaction but never updates the balance (stays unchanged). */
    DEPOSIT_NOT_PERSISTED,

    /** {@code transfer} debits the source but never credits the destination. */
    TRANSFER_CREDIT_DROPPED,

    /** {@code createAccount} persists and returns a currency other than the one requested. */
    WRONG_CURRENCY
}
