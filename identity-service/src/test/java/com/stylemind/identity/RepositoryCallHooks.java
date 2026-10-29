package com.stylemind.identity;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.framework.AopInfrastructureBean;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.test.context.TestConfiguration;

/** Test-only repository interception that always proceeds through the real Spring Data proxy. */
final class RepositoryCallHooks {
    @FunctionalInterface
    interface Invocation {
        Object proceed() throws Throwable;
    }

    @FunctionalInterface
    interface Hook {
        Object invoke(String beanName, Method method, Object[] arguments, Invocation invocation) throws Throwable;
    }

    private static final Map<String, AtomicInteger> CALLS = new ConcurrentHashMap<>();
    private static volatile Hook activeHook;

    private RepositoryCallHooks() { }

    static AutoCloseable install(Hook hook) {
        if (activeHook != null) throw new IllegalStateException("A repository hook is already installed");
        activeHook = hook;
        return () -> activeHook = null;
    }

    static Object invoke(String beanName, Method method, Object[] arguments, Invocation invocation) throws Throwable {
        CALLS.computeIfAbsent(key(beanName, method.getName()), ignored -> new AtomicInteger()).incrementAndGet();
        Hook hook = activeHook;
        return hook == null ? invocation.proceed() : hook.invoke(beanName, method, arguments, invocation);
    }

    static int calls(String beanName, String methodName) {
        AtomicInteger calls = CALLS.get(key(beanName, methodName));
        return calls == null ? 0 : calls.get();
    }

    static int callsFor(String beanName) {
        String prefix = beanName + "#";
        return CALLS.entrySet().stream().filter(entry -> entry.getKey().startsWith(prefix))
                .mapToInt(entry -> entry.getValue().get()).sum();
    }

    static void clearCalls() { CALLS.clear(); }

    static void clearCalls(String beanName) {
        String prefix = beanName + "#";
        CALLS.keySet().removeIf(key -> key.startsWith(prefix));
    }

    private static String key(String beanName, String methodName) { return beanName + "#" + methodName; }

    @TestConfiguration(proxyBeanMethods = false)
    static class ConfigurationForTests {
        @Bean
        static BeanPostProcessor wrapRepositoriesWithHooks() {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String beanName) {
                    if (!beanName.endsWith("Repository") || bean instanceof AopInfrastructureBean) return bean;
                    ProxyFactory proxyFactory = new ProxyFactory(bean);
                    proxyFactory.setProxyTargetClass(false);
                    proxyFactory.addAdvice((MethodInterceptor) invocation -> RepositoryCallHooks.invoke(
                            beanName,
                            invocation.getMethod(),
                            invocation.getArguments(),
                            invocation::proceed));
                    return proxyFactory.getProxy(bean.getClass().getClassLoader());
                }
            };
        }
    }
}
