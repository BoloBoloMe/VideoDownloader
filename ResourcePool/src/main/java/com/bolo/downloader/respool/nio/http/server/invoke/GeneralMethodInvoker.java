package com.bolo.downloader.respool.nio.http.server.invoke;

import com.bolo.downloader.respool.log.LoggerFactory;
import com.bolo.downloader.respool.log.MyLogger;
import com.bolo.downloader.respool.nio.http.server.HttpDistributeHandler;
import com.bolo.downloader.respool.nio.http.server.RequestContextHolder;
import com.bolo.downloader.respool.nio.http.server.scan.MethodMapper;
import com.bolo.downloader.respool.nio.http.server.scan.MethodMapperContainer;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.multipart.DefaultHttpDataFactory;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
import io.netty.handler.codec.http.multipart.InterfaceHttpData;
import io.netty.handler.codec.http.multipart.MemoryAttribute;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.function.Function;

/**
 * 方法调用的共用处理过程
 */
public class GeneralMethodInvoker implements MethodInvoker {
    private static final MyLogger log = LoggerFactory.getLogger(HttpDistributeHandler.class);
    private final ResultInterpreter interpreter;

    public GeneralMethodInvoker(ResultInterpreter interpreter) {
        this.interpreter = interpreter;
    }


    @Override
    public FullHttpResponse invoke(ChannelHandlerContext ctx, FullHttpRequest request) {
        Object result = doInvoke(ctx, request);
        return interpreter.interpret(result);
    }

    private Object doInvoke(ChannelHandlerContext ctx, FullHttpRequest request) {
        if (!request.decoderResult().isSuccess()) {
            return new ResponseEntity<>(HttpResponseStatus.BAD_REQUEST);
        }
        final String uri = request.uri();
        final HttpMethod requestMethod = request.method();
        if (Objects.isNull(uri) || Objects.isNull(requestMethod)) {
            return new ResponseEntity<>(HttpResponseStatus.BAD_REQUEST, "invalid request");
        }
        final MethodMapper methodMapper = MethodMapperContainer.get(uri);
        if (Objects.isNull(methodMapper)) {
            return new ResponseEntity<>(HttpResponseStatus.NOT_FOUND, "invalid path: " + uri);
        }
        final Method method = methodMapper.getTargetMethod();
        Optional<HttpMethod> allowedMethod = methodMapper.getIfExist(requestMethod);
        if (!allowedMethod.isPresent()) {
            return new ResponseEntity<>(HttpResponseStatus.METHOD_NOT_ALLOWED, "allowed method " + methodMapper.getAllowedMethods());
        }
        Optional<Object> targetOpt = methodMapper.getTargetInstance();
        final Object instance;
        if (targetOpt.isPresent()) {
            instance = targetOpt.get();
        } else {
            log.error("the mapper is missing an instance, remove the problematic mapper :" + methodMapper);
            MethodMapperContainer.remove(methodMapper);
            return new ResponseEntity<>(HttpResponseStatus.INTERNAL_SERVER_ERROR);
        }
        try {
            Map<String, List<String>> parameterMap = getParameters(ctx, request, uri, requestMethod);
            RequestContextHolder.setParameters(parameterMap);
            Object[] parameterList = alignParameters(ctx, request, method);
            return method.invoke(instance, parameterList);
        } catch (Exception e) {
            log.error("http request handler invoke failed. method=" + method.getName() + ",parameters={}." + Collections.emptyList(), e);
        } finally {
            RequestContextHolder.remove();
        }
        return new ResponseEntity<>(HttpResponseStatus.INTERNAL_SERVER_ERROR);
    }

    private Map<String, List<String>> getParameters(ChannelHandlerContext ctx, FullHttpRequest request, String uri, HttpMethod requestMethod) {
        Map<String, List<String>> params = new HashMap<>();
        if (HttpMethod.GET.equals(requestMethod)) {
            int beginIndex = uri.indexOf('?');
            if (beginIndex < 0) {
                return params;
            }
            String[] entryList = uri.substring(beginIndex + 1).split("&");
            for (String entry : entryList) {
                String[] entryArr = entry.split("=");
                if (entryArr.length != 2) {
                    continue;
                }
                List<String> list = params.computeIfAbsent(entryArr[0], k -> new LinkedList<>());
                list.add(entryArr[1]);
            }
            return params;
        } else if (HttpMethod.POST.equals(requestMethod)) {
            HttpPostRequestDecoder decoder = new HttpPostRequestDecoder(new DefaultHttpDataFactory(false), request);
            List<InterfaceHttpData> parmList = decoder.getBodyHttpDatas();
            for (InterfaceHttpData parm : parmList) {
                if (parm.getHttpDataType() == InterfaceHttpData.HttpDataType.Attribute) {
                    MemoryAttribute data = (MemoryAttribute) parm;
                    List<String> values;
                    if (null == (values = params.get(data.getName()))) {
                        values = new ArrayList<>();
                        values.add(data.getValue());
                        params.put(data.getName(), values);
                    } else {
                        values.add(data.getValue());
                    }
                }
            }
        }
        return params;
    }

    /**
     * 方法入参对齐——尝试初始化入参列表
     */
    private Object[] alignParameters(ChannelHandlerContext ctx, FullHttpRequest request, Method method) {
        // 使用jdk8新增的方法获取参数名和参数类型, 请在编译时添加参数：-parameters
        Parameter[] parameterDefineList = method.getParameters();
        int paramListLength = parameterDefineList.length;
        Object[] parameter = new Object[paramListLength];
        for (int index = 0; index < paramListLength; index++) {
            Parameter parameterDefine = parameterDefineList[index];
            Class<?> pClass = parameterDefineList[index].getType();
            if (pClass.isInstance(ctx)) {
                parameter[index] = ctx;
                continue;
            }
            if (pClass.isInstance(request)) {
                parameter[index] = request;
                continue;
            }
            String pName = parameterDefine.getName();
            if (Objects.nonNull(pName) && !pName.isEmpty()) {
                if (pClass.isArray()) {
                    Optional<? extends List<?>> valuesOpt = RequestContextHolder.getValues(pName, getParse(pClass));
                    final int i = index;
                    valuesOpt.ifPresent(values -> parameter[i] = values.toArray());
                } else if (pClass.isAssignableFrom(Collection.class)) {
                    Optional<? extends List<?>> valuesOpt = RequestContextHolder.getValues(pName, getParse(pClass));
                    final int i = index;
                    valuesOpt.ifPresent(values -> parameter[i] = values);
                } else {
                    parameter[index] = RequestContextHolder.getValue(pName, getParse(pClass));
                }
            }
        }
        return parameter;
    }

    private Function<String, ?> getParse(Class<?> pClass) {
        if (String.class.equals(pClass)) {
            return s -> s;
        }
        if (Integer.class.equals(pClass)) {
            return Integer::parseInt;
        }
        if (Long.class.equals(pClass)) {
            return Long::parseLong;
        }
        if (Byte.class.equals(pClass)) {
            return Byte::parseByte;
        }
        if (Character.class.equals(pClass)) {
            return s -> Objects.nonNull(s) && !s.isEmpty() ? null : s.charAt(0);
        }
        if (Short.class.equals(pClass)) {
            return Short::parseShort;
        }
        if (Float.class.equals(pClass)) {
            return Float::parseFloat;
        }
        if (Double.class.equals(pClass)) {
            return Double::parseDouble;
        }
        return s -> null;
    }
}