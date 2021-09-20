package com.bolo.downloader.respool.nio.http.scan;

import java.util.Optional;

/**
 * 实例创建工厂，为实例的创建提供扩展接口。如单例实例、原形实例等
 */
public interface TargetInstanceFactory {
    Optional<Object> getInstance();
}
