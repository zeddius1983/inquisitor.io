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

import lombok.val;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.jspecify.annotations.Nullable;
import org.slf4j.LoggerFactory;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.data.repository.Repository;
import org.springframework.stereotype.Component;

/**
 * Traces every call to the Account and Transaction repositories, logging the method
 * name, its arguments and the returned value — or the thrown exception. The
 * data-layer counterpart to {@link ControllerTracingAspect}: it shows the persistence
 * calls behind each harness-driven HTTP request, so we can see exactly what the app
 * read or wrote while the LLM drove it.
 *
 * <p>The pointcut matches the Spring Data {@code Repository} marker on the target
 * (the demo's only repositories are Account and Transaction), so it catches every
 * call — including the inherited {@code CrudRepository} methods ({@code save},
 * {@code findById}, …) whose declaring type lives in Spring Data. A per-interface
 * {@code this(...)} pointcut matched these inherited methods unreliably.
 *
 * <p>Each line is logged under the repository's own category (e.g.
 * {@code …repository.AccountRepository}), not this aspect's, by resolving the proxied
 * repository interface — so even inherited methods report the concrete repository.
 */
@Aspect
@Component
public class RepositoryTracingAspect {

    @Pointcut("target(org.springframework.data.repository.Repository)")
    void repository() {
    }

    @Around("repository()")
    public @Nullable Object trace(ProceedingJoinPoint joinPoint) throws Throwable {
        val log = LoggerFactory.getLogger(repositoryType(joinPoint));
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

    /** The concrete repository interface behind the proxy (e.g. {@code AccountRepository}). */
    private static Class<?> repositoryType(ProceedingJoinPoint joinPoint) {
        for (val iface : AopProxyUtils.proxiedUserInterfaces(joinPoint.getTarget())) {
            if (Repository.class.isAssignableFrom(iface)) {
                return iface;
            }
        }
        return joinPoint.getSignature().getDeclaringType();
    }
}
