package com.bolo.downloader.respool.nio.http.scan;

import java.util.concurrent.ConcurrentHashMap;

public class MethodMapperContainer {
    private static final ConcurrentHashMap<String, MethodMapper> mapperTable = new ConcurrentHashMap<>();

    public static void put(String path, MethodMapper mapper) {
        mapperTable.put(path, mapper);
    }

    public static MethodMapper get(String path) {
        return mapperTable.get(path);
    }
}