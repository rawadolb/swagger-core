package io.swagger.v3.oas.integration.afklm;

import com.fasterxml.jackson.annotation.JsonView;
import io.swagger.v3.core.util.ParameterProcessor;
import io.swagger.v3.core.util.ReflectionUtils;
import io.swagger.v3.oas.integration.api.OpenAPIConfiguration;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

public class ReaderUtils {
    protected static final String GET_METHOD = "get";
    protected static final String POST_METHOD = "post";
    protected static final String PUT_METHOD = "put";
    protected static final String DELETE_METHOD = "delete";
    protected static final String PATCH_METHOD = "patch";
    protected static final String HEAD_METHOD = "head";
    protected static final String OPTIONS_METHOD = "options";
    protected static final String PATH_DELIMITER = "/";

    /**
     * Collects constructor-level parameters from class.
     *
     * @param cls        is a class for collecting
     * @param components
     * @return the collection of supported parameters
     */
    public static List<Parameter> collectConstructorParameters(Class<?> cls, Components components, JsonView jsonViewAnnotation) {
        if (cls.isLocalClass() || (cls.isMemberClass() && !Modifier.isStatic(cls.getModifiers()))) {
            return Collections.emptyList();
        }

        List<Parameter> selected = Collections.emptyList();
        int maxParamsCount = 0;

        for (Constructor<?> constructor : cls.getDeclaredConstructors()) {
            if (!ReflectionUtils.isConstructorCompatible(constructor)
                    && !ReflectionUtils.isInject(Arrays.asList(constructor.getDeclaredAnnotations()))) {
                continue;
            }

            final Type[] genericParameterTypes = constructor.getGenericParameterTypes();
            final Annotation[][] annotations = constructor.getParameterAnnotations();

            int paramsCount = 0;
            final List<Parameter> parameters = new ArrayList<Parameter>();
            for (int i = 0; i < genericParameterTypes.length; i++) {
                final List<Annotation> tmpAnnotations = Arrays.asList(annotations[i]);
                if (isContext(tmpAnnotations)) {
                    paramsCount++;
                } else {
                    final Type genericParameterType = genericParameterTypes[i];
                    final List<Parameter> tmpParameters = collectParameters(genericParameterType, tmpAnnotations, components, jsonViewAnnotation);
                    if (tmpParameters.size() >= 1) {
                        for (Parameter tmpParameter : tmpParameters) {
                            Parameter processedParameter = ParameterProcessor.applyAnnotations(
                                    tmpParameter,
                                    genericParameterType,
                                    tmpAnnotations,
                                    components,
                                    new String[0],
                                    null,
                                    jsonViewAnnotation);
                            if (processedParameter != null) {
                                parameters.add(processedParameter);
                            }
                        }
                        paramsCount++;
                    }
                }
            }

            if (paramsCount >= maxParamsCount) {
                maxParamsCount = paramsCount;
                selected = parameters;
            }
        }

        return selected;
    }

    /**
     * Collects field-level parameters from class.
     *
     * @param cls        is a class for collecting
     * @param components
     * @return the collection of supported parameters
     */
    public static List<Parameter> collectFieldParameters(Class<?> cls, Components components, JsonView jsonViewAnnotation) {
        final List<Parameter> parameters = new ArrayList<Parameter>();
        for (Field field : ReflectionUtils.getDeclaredFields(cls)) {
            final List<Annotation> annotations = Arrays.asList(field.getAnnotations());
            final Type genericType = field.getGenericType();
            parameters.addAll(collectParameters(genericType, annotations, components, jsonViewAnnotation));
        }
        return parameters;
    }

    private static List<Parameter> collectParameters(Type type, List<Annotation> annotations, Components components, JsonView jsonViewAnnotation) {
        final Iterator<OpenAPIExtension> chain = OpenAPIExtensions.chain();
        return chain.hasNext() ? chain.next().extractParameters(annotations, type, new HashSet<>(), components, false, jsonViewAnnotation, chain).parameters :
                Collections.emptyList();
    }

    private static boolean isContext(List<Annotation> annotations) {
        /*
        for (Annotation annotation : annotations) {
            if (annotation instanceof Context) {
                return true;
            }
        }*/
        return false;
    }

    public static Optional<List<String>> getStringListFromStringArray(String[] array) {
        if (array == null) {
            return Optional.empty();
        }
        List<String> list = new ArrayList<>();
        boolean isEmpty = true;
        for (String value : array) {
            if (StringUtils.isNotBlank(value)) {
                isEmpty = false;
            }
            list.add(value);
        }
        if (isEmpty) {
            return Optional.empty();
        }
        return Optional.of(list);
    }

    public static boolean isIgnored(String path, OpenAPIConfiguration config) {
        if (config.getIgnoredRoutes() == null) {
            return false;
        }
        for (String item : config.getIgnoredRoutes()) {
            final int length = item.length();
            if (path.startsWith(item) && (path.length() == length || path.startsWith(PATH_DELIMITER, length))) {
                return true;
            }
        }
        return false;
    }

    public static String getPath(RequestMapping classLevelPath, Method method, String parentPath, boolean isSubresource) {
        if (classLevelPath == null && method == null && StringUtils.isEmpty(parentPath)) {
            return null;
        }
        StringBuilder b = new StringBuilder();
        appendPathComponent(parentPath, b);
        if (classLevelPath != null && !isSubresource) {
            appendPathComponent(classLevelPath.value()[0], b);
        }
        if (method != null) {
            appendPathComponent(extractMethodPath(method, OpenAPIExtensions.chain()), b);
        }
        return b.length() == 0 ? "/" : b.toString();
    }

    /**
     * appends a path component string to a StringBuilder
     * guarantees:
     * <ul>
     *     <li>nulls, empty strings and "/" are nops</li>
     *     <li>output will always start with "/" and never end with "/"</li>
     * </ul>
     *
     * @param component component to be added
     * @param to        output
     */
    private static void appendPathComponent(String component, StringBuilder to) {
        if (component == null || component.isEmpty() || "/".equals(component)) {
            return;
        }
        if (!component.startsWith("/") && (to.length() == 0 || '/' != to.charAt(to.length() - 1))) {
            to.append("/");
        }
        if (component.endsWith("/")) {
            to.append(component, 0, component.length() - 1);
        } else {
            to.append(component);
        }
    }

    public static String extractOperationMethod(Method method, Iterator<OpenAPIExtension> chain) {
        if (method.getAnnotation(GetMapping.class) != null) {
            return GET_METHOD;
        } else if (method.getAnnotation(PutMapping.class) != null) {
            return PUT_METHOD;
        } else if (method.getAnnotation(PostMapping.class) != null) {
            return POST_METHOD;
        } else if (method.getAnnotation(DeleteMapping.class) != null) {
            return DELETE_METHOD;
        } else if (method.getAnnotation(PatchMapping.class) != null) {
            return PATCH_METHOD;
        } else if (chain != null && chain.hasNext()) {
            return chain.next().extractOperationMethod(method, chain);
        } else {
            return null;
        }
    }

    private static String extractMethodPath(Method method, Iterator<OpenAPIExtension> chain) {
        if (method.getAnnotation(GetMapping.class) != null) {
            return method.getAnnotation(GetMapping.class).value()[0];
        } else if (method.getAnnotation(PutMapping.class) != null) {
            return method.getAnnotation(PutMapping.class).value()[0];
        } else if (method.getAnnotation(PostMapping.class) != null) {
            return method.getAnnotation(PostMapping.class).value()[0];
        } else if (method.getAnnotation(DeleteMapping.class) != null) {
            return method.getAnnotation(DeleteMapping.class).value()[0];
        } else if (method.getAnnotation(PatchMapping.class) != null) {
            return method.getAnnotation(PatchMapping.class).value()[0];
        } else if (chain != null && chain.hasNext()) {
            return chain.next().extractOperationMethod(method, chain);
        } else {
            return null;
        }
    }

}
