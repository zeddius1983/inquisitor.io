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

import io.inquisitor.demo.model.Account;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.ListPagingAndSortingRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AccountRepository
        extends ListCrudRepository<Account, Long>, ListPagingAndSortingRepository<Account, Long> {

    @Query("""
            SELECT * FROM account
            WHERE (:owner IS NULL OR LOWER(owner) LIKE LOWER('%' || :owner || '%'))
              AND (:currency IS NULL OR currency = :currency)
            """)
    List<Account> search(@Param("owner") @Nullable String owner,
                         @Param("currency") @Nullable String currency,
                         Pageable pageable);

    @Query("""
            SELECT COUNT(*) FROM account
            WHERE (:owner IS NULL OR LOWER(owner) LIKE LOWER('%' || :owner || '%'))
              AND (:currency IS NULL OR currency = :currency)
            """)
    long countSearch(@Param("owner") @Nullable String owner,
                     @Param("currency") @Nullable String currency);
}
