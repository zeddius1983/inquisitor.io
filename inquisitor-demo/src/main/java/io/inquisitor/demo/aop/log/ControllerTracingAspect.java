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

package io.inquisitor.demo.aop.log;

import java.util.Arrays;

import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Traces every REST controller invocation under {@code io.inquisitor.demo.web},
 * logging the method name, its arguments and the returned value — or the thrown
 * exception. Exists so we can confirm the Inquisitor harness's LLM is actually
 * driving the application through real HTTP calls, rather than fabricating
 * tool results.
 */
@Aspect
@Component
@Slf4j
public class ControllerTracingAspect {

    @Pointcut("within(io.inquisitor.demo.web..*) "
            + "&& @within(org.springframework.web.bind.annotation.RestController)")
    void restController() {
    }

    @Around("restController()")
    public @Nullable Object trace(ProceedingJoinPoint joinPoint) throws Throwable {
        val method = joinPoint.getSignature().getName();
        val args = Arrays.toString(joinPoint.getArgs());
        try {
            val result = joinPoint.proceed();
            log.info("{}({}) -> {}", method, args, result);
            return result;
        } catch (Throwable t) {
            log.info("{}({}) -> threw {}", method, args, t.toString());
            throw t;
        }
    }
}
