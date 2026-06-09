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

package io.inquisitor.demo.web.dto;

import io.inquisitor.demo.model.TransactionType;
import org.jspecify.annotations.Nullable;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.Instant;

public record TransactionFilter(
        @Nullable TransactionType type,
        @Nullable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
        @Nullable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to
) {}
