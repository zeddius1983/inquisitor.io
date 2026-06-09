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

package io.inquisitor.demo.model;

import lombok.Builder;
import org.jspecify.annotations.Nullable;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Builder(toBuilder = true)
@Table("account")
public record Account(
        @Id @Nullable Long id,
        String owner,
        String currency,
        BigDecimal balance,
        @Version Long version,
        @CreatedDate @Nullable Instant createdAt
) {
    public static Account open(String owner, String currency) {
        return Account.builder()
                .owner(owner)
                .currency(currency)
                .balance(BigDecimal.ZERO)
                .version(0L)
                .build();
    }

    public Account credit(BigDecimal amount) {
        return toBuilder().balance(balance.add(amount)).build();
    }

    public Account debit(BigDecimal amount) {
        return toBuilder().balance(balance.subtract(amount)).build();
    }
}
