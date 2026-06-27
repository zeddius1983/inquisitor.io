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

package io.inquisitor.demo.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.inquisitor.demo.web.dto.CreateAccountRequest;

import org.junit.jupiter.api.Test;

class AccountImportTest {

    private final AccountImport parser = new AccountImport() {};

    @Test
    void csvUsesEachRowsCurrency() {
        var result = parser.parseCsv("Alice,USD\nBob,EUR", "GBP");

        assertThat(result).containsExactly(
                new CreateAccountRequest("Alice", "USD"),
                new CreateAccountRequest("Bob", "EUR"));
    }

    @Test
    void csvBlankCurrencyCellFallsBackToDefault() {
        var result = parser.parseCsv("Carol,", "GBP");

        assertThat(result).containsExactly(new CreateAccountRequest("Carol", "GBP"));
    }

    @Test
    void csvMissingCurrencyColumnFallsBackToDefault() {
        // Regression: a row that omits the currency column entirely ("Carol", no comma)
        // used to throw ArrayIndexOutOfBoundsException; it must behave like a blank cell.
        var result = parser.parseCsv("Carol", "GBP");

        assertThat(result).containsExactly(new CreateAccountRequest("Carol", "GBP"));
    }

    @Test
    void csvSkipsHeaderRow() {
        var result = parser.parseCsv("owner,currency\nAlice,USD", "GBP");

        assertThat(result).containsExactly(new CreateAccountRequest("Alice", "USD"));
    }

    @Test
    void plainTextOpensEveryOwnerInTheDefaultCurrency() {
        var result = parser.parsePlain("Dave\nErin", "JPY");

        assertThat(result).containsExactly(
                new CreateAccountRequest("Dave", "JPY"),
                new CreateAccountRequest("Erin", "JPY"));
    }
}
