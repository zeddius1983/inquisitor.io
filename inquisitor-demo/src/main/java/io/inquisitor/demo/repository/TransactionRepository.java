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

package io.inquisitor.demo.repository;

import io.inquisitor.demo.model.Transaction;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.ListPagingAndSortingRepository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface TransactionRepository
        extends ListCrudRepository<Transaction, Long>, ListPagingAndSortingRepository<Transaction, Long> {

    @Query("""
            SELECT * FROM transaction
            WHERE account_id = :accountId
              AND (CAST(:type AS text) IS NULL OR type = CAST(:type AS text))
              AND (CAST(:from AS timestamptz) IS NULL OR created_at >= CAST(:from AS timestamptz))
              AND (CAST(:to   AS timestamptz) IS NULL OR created_at <= CAST(:to   AS timestamptz))
            ORDER BY created_at DESC, id DESC
            """)
    List<Transaction> search(@Param("accountId") Long accountId,
                             @Param("type") @Nullable String type,
                             @Param("from") @Nullable Instant from,
                             @Param("to") @Nullable Instant to,
                             Pageable pageable);

    @Query("""
            SELECT COUNT(*) FROM transaction
            WHERE account_id = :accountId
              AND (CAST(:type AS text) IS NULL OR type = CAST(:type AS text))
              AND (CAST(:from AS timestamptz) IS NULL OR created_at >= CAST(:from AS timestamptz))
              AND (CAST(:to   AS timestamptz) IS NULL OR created_at <= CAST(:to   AS timestamptz))
            """)
    long countSearch(@Param("accountId") Long accountId,
                     @Param("type") @Nullable String type,
                     @Param("from") @Nullable Instant from,
                     @Param("to") @Nullable Instant to);
}
