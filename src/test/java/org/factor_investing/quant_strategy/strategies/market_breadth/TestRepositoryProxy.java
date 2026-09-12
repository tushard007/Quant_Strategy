package org.factor_investing.quant_strategy.strategies.market_breadth;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Optional;

final class TestRepositoryProxy {

    @FunctionalInterface
    interface InvocationHandler {
        Object invoke(Method method, Object[] arguments);
    }

    @SuppressWarnings("unchecked")
    static <T> T create(Class<T> repositoryType, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(repositoryType.getClassLoader(), new Class<?>[]{repositoryType},
                (proxy, method, arguments) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return switch (method.getName()) {
                            case "toString" -> repositoryType.getSimpleName() + " test proxy";
                            case "hashCode" -> System.identityHashCode(proxy);
                            case "equals" -> proxy == arguments[0];
                            default -> null;
                        };
                    }
                    Object result = handler.invoke(method, arguments == null ? new Object[0] : arguments);
                    if (result != null) {
                        return result;
                    }
                    if (method.getReturnType() == Optional.class) {
                        return Optional.empty();
                    }
                    if (method.getReturnType() == List.class) {
                        return List.of();
                    }
                    if (method.getReturnType() == boolean.class) {
                        return false;
                    }
                    if (method.getReturnType() == int.class || method.getReturnType() == long.class) {
                        return 0;
                    }
                    return null;
                });
    }

    private TestRepositoryProxy() {
    }
}
