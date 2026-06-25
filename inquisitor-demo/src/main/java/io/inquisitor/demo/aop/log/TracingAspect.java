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
 * Traces every REST controller invocation under {@code io.inquisitor.demo.web} and
 * every Spring Data repository call, logging the method name, its arguments and the
 * returned value — or the thrown exception. Exists so we can confirm the Inquisitor
 * harness's LLM is actually driving the application through real HTTP and persistence
 * calls, rather than fabricating tool results.
 *
 * <p>Each line is logged under the invoked type's own category — a controller line
 * under {@code …web.AccountController}, a repository line under
 * {@code …repository.AccountRepository}, not this aspect's. The controller category
 * comes from the invoked method's declaring type; the repository category from the
 * proxied repository interface, so even inherited {@code CrudRepository} methods
 * ({@code save}, {@code findById}, …) report the concrete repository.
 *
 * <p>Tracing is a debug-level concern: when the resolved category has debug logging
 * disabled the advice proceeds immediately, adding no per-call overhead.
 */
@Aspect
@Component
public class TracingAspect {

    @Pointcut("within(io.inquisitor.demo.web..*) "
            + "&& @within(org.springframework.web.bind.annotation.RestController)")
    void restController() {
    }

    @Pointcut("target(org.springframework.data.repository.Repository)")
    void repository() {
    }

    @Around("restController()")
    public @Nullable Object traceController(ProceedingJoinPoint joinPoint) throws Throwable {
        return trace(joinPoint, joinPoint.getSignature().getDeclaringType());
    }

    @Around("repository()")
    public @Nullable Object traceRepository(ProceedingJoinPoint joinPoint) throws Throwable {
        return trace(joinPoint, repositoryType(joinPoint));
    }

    private static @Nullable Object trace(ProceedingJoinPoint joinPoint, Class<?> loggerType) throws Throwable {
        val log = LoggerFactory.getLogger(loggerType);
        if (!log.isDebugEnabled()) {
            return joinPoint.proceed();
        }
        val method = joinPoint.getSignature().getName();
        val args = Arrays.toString(joinPoint.getArgs());
        try {
            val result = joinPoint.proceed();
            log.debug("{}({}) -> {}", method, args, result);
            return result;
        } catch (Throwable t) {
            log.debug("{}({}) -> threw {}", method, args, t.toString());
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
