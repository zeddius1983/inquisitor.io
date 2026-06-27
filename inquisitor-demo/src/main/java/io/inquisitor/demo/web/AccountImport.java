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

import io.inquisitor.demo.web.dto.CreateAccountRequest;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Trait mixed into {@link AccountController} that turns an uploaded CSV or plain-text
 * body into {@link CreateAccountRequest}s. Kept as default methods on an interface
 * rather than static helpers on the controller, so the parsing sits apart from the
 * web wiring.
 */
public interface AccountImport {

    record CsvPair(String owner, String currency) {
        static CsvPair from(String line) {
            String[] fields = line.strip().split(",", -1);
            String owner = fields[0].strip();
            // A row may omit the currency column entirely (e.g. "Carol"), not just leave
            // the cell blank ("Carol,"); treat both as a blank currency -> defaultCurrency.
            String currency = fields.length > 1 ? fields[1].strip() : "";
            return new CsvPair(owner, currency);
        }
    }

    /**
     * CSV import: {@code owner,currency} per line, with an optional {@code owner,currency}
     * header row. Every data row must carry at least owner; a row whose currency cell is
     * blank uses {@code defaultCurrency}.
     */
    default List<CreateAccountRequest> parseCsv(String body, String defaultCurrency) {
        Function<String, Optional<CreateAccountRequest>> toAccount = line ->
                switch (CsvPair.from(line)) {
                    case CsvPair(var owner, var currency)
                            when owner.equalsIgnoreCase("owner")
                            && currency.equalsIgnoreCase("currency") ->
                            Optional.empty();

                    case CsvPair(var owner, var currency)
                            when currency.isBlank() ->
                            Optional.of(
                                    new CreateAccountRequest(owner, defaultCurrency)
                            );

                    case CsvPair(var owner, var currency) ->
                            Optional.of(
                                    new CreateAccountRequest(owner, currency)
                            );
                };
        return parse(body, toAccount);
    }

    /**
     * Plain-text import: one owner per line, all opened in {@code defaultCurrency}.
     */
    default List<CreateAccountRequest> parsePlain(String body, String defaultCurrency) {
        return parse(body, (owner) -> Optional.of(new CreateAccountRequest(owner, defaultCurrency)));
    }

    default List<CreateAccountRequest> parse(String body, Function<String, Optional<CreateAccountRequest>> toAccount) {
        return body.lines()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .map(toAccount)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();
    }

}
