package com.bolo.downloader.respool.nio.http.scan;

import com.bolo.downloader.respool.log.LoggerFactory;
import com.bolo.downloader.respool.log.MyLogger;
import com.bolo.downloader.respool.nio.http.annotate.Controller;
import com.bolo.downloader.respool.nio.http.annotate.RequestMapping;
import com.bolo.downloader.respool.nio.http.annotate.RequestMethod;
import com.bolo.downloader.respool.nio.http.annotate.Scope;
import io.netty.handler.codec.http.HttpMethod;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/**
 * 类路径扫描器，会扫描指定的包路径下的所有类
 */
public class ClasspathScanner implements MethodMapperScanner {
    private static final MyLogger log = LoggerFactory.getLogger(ClasspathScanner.class);

    private final String classPath;

    public ClasspathScanner(String classPath) {
        this.classPath = classPath;
    }

    @Override
    public void scan() {
        synchronized (MethodMapperScanner.class) {
            Set<Class<?>> classSet = getClasses(classPath);
            for (Class<?> targetClass : classSet) {
                try {
                    Optional<Controller> controllerOpt = getAnnotateFromClass(targetClass, Controller.class);
                    if (!controllerOpt.isPresent()) {
                        continue;
                    }
                    String scope = getAnnotateFromClass(targetClass, Scope.class).map(Scope::value).orElse(Scope.SCOPE_SINGLETON);
                    Method[] methods = targetClass.getDeclaredMethods();
                    for (Method method : methods) {
                        getAnnotateFromMethod(method, RequestMapping.class).ifPresent(mapping -> {
                            List<String> paths = Arrays.asList(mapping.value());
                            HttpMethod[] httpMethods = Stream.of(mapping.method()).map(RequestMethod::getHttpMethod).toArray(HttpMethod[]::new);
                            MethodMapper methodMapper = new MethodMapper(paths, httpMethods, method,
                                    buildTargetInstanceFactory(scope, targetClass), targetClass);
                            paths.forEach(path -> MethodMapperContainer.put(path, methodMapper));
                        });
                    }
                } catch (Exception e) {
                    log.error("scan class by classpath is failed. error:", e);
                }
            }
        }
    }

    private TargetInstanceFactory buildTargetInstanceFactory(String scope, Class<?> targetClass) {
        if (Scope.SCOPE_PROTOTYPE.equals(scope)) {
            return new PrototypeInstanceFactory(targetClass);
        } else if (Scope.SCOPE_SINGLETON.endsWith(scope)) {
            return new PrototypeInstanceFactory(targetClass);
        }
        return new SingletonInstanceFactory(targetClass);
    }

    private <A extends Annotation> Optional<A> getAnnotateFromClass(Class<?> targetClazz, Class<A> annotateClass) {
        return Optional.ofNullable(targetClazz).map(c -> c.getAnnotation(annotateClass));
    }

    private <A extends Annotation> Optional<A> getAnnotateFromMethod(Method method, Class<A> annotateClass) {
        return Optional.ofNullable(method).map(c -> c.getAnnotation(annotateClass));
    }

    /**
     * 从包package中获取所有的Class
     *
     * @param pack 类路径
     */
    public static Set<Class<?>> getClasses(String pack) {

        // 第一个class类的集合
        Set<Class<?>> classes = new LinkedHashSet<>();
        // 是否循环迭代
        boolean recursive = true;
        // 获取包的名字 并进行替换
        String packageName = pack;
        String packageDirName = packageName.replace('.', '/');
        // 定义一个枚举的集合 并进行循环来处理这个目录下的things
        Enumeration<URL> dirs;
        try {
            dirs = Thread.currentThread()
                    .getContextClassLoader()
                    .getResources(packageDirName);
            // 循环迭代下去
            while (dirs.hasMoreElements()) {
                // 获取下一个元素
                URL url = dirs.nextElement();
                // 得到协议的名称
                String protocol = url.getProtocol();
                // 如果是以文件的形式保存在服务器上
                if ("file".equals(protocol)) {
                    System.err.println("file类型的扫描");
                    // 获取包的物理路径
                    String filePath = URLDecoder.decode(url.getFile(), "UTF-8");
                    // 以文件的方式扫描整个包下的文件 并添加到集合中
                    findAndAddClassesInPackageByFile(packageName, filePath, recursive, classes);
                } else if ("jar".equals(protocol)) {
                    // 如果是jar包文件
                    // 定义一个JarFile
                    System.err.println("jar类型的扫描");
                    JarFile jar;
                    try {
                        // 获取jar
                        jar = ((JarURLConnection) url.openConnection()).getJarFile();
                        // 从此jar包 得到一个枚举类
                        Enumeration<JarEntry> entries = jar.entries();
                        // 同样的进行循环迭代
                        while (entries.hasMoreElements()) {
                            // 获取jar里的一个实体 可以是目录 和一些jar包里的其他文件 如META-INF等文件
                            JarEntry entry = entries.nextElement();
                            String name = entry.getName();
                            // 如果是以/开头的
                            if (name.charAt(0) == '/') {
                                // 获取后面的字符串
                                name = name.substring(1);
                            }
                            // 如果前半部分和定义的包名相同
                            if (name.startsWith(packageDirName)) {
                                int idx = name.lastIndexOf('/');
                                // 如果以"/"结尾 是一个包
                                if (idx != -1) {
                                    // 获取包名 把"/"替换成"."
                                    packageName = name.substring(0, idx).replace('/', '.');
                                }
                                // 如果可以迭代下去 并且是一个包
                                if ((idx != -1) || recursive) {
                                    // 如果是一个.class文件 而且不是目录
                                    if (name.endsWith(".class") && !entry.isDirectory()) {
                                        // 去掉后面的".class" 获取真正的类名
                                        String className = name.substring(
                                                packageName.length() + 1, name.length() - 6);
                                        try {
                                            // 添加到classes
                                            classes.add(Class.forName(packageName + '.' + className));
                                        } catch (ClassNotFoundException e) {
                                            // log
                                            // .error("添加用户自定义视图类错误 找不到此类的.class文件");
                                            e.printStackTrace();
                                        }
                                    }
                                }
                            }
                        }
                    } catch (IOException e) {
                        log.error("scan class by classpath is failed. error:", e);
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return classes;
    }

    /**
     * 以文件的形式来获取包下的所有Class
     *
     * @param packageName 包名
     * @param packagePath 包路径
     * @param recursive
     * @param classes
     */

    public static void findAndAddClassesInPackageByFile(String packageName,
                                                        String packagePath, final boolean recursive, Set<Class<?>> classes) {
        // 获取此包的目录 建立一个File
        File dir = new File(packagePath);
        // 如果不存在或者 也不是目录就直接返回
        if (!dir.exists() || !dir.isDirectory()) {
            // log.warn("用户定义包名 " + packageName + " 下没有任何文件");
            return;
        }
        // 如果存在 就获取包下的所有文件 包括目录
        // 自定义过滤规则 如果可以循环(包含子目录) 或则是以.class结尾的文件(编译好的java类文件)
        File[] dirfiles = dir.listFiles(file -> (recursive && file.isDirectory()) ||
                (file.getName().endsWith(".class")));
        // 循环所有文件
        if (Objects.nonNull(dirfiles)) {
            for (File file : dirfiles) {
                // 如果是目录 则继续扫描
                if (file.isDirectory()) {
                    findAndAddClassesInPackageByFile(packageName + "."
                                    + file.getName(), file.getAbsolutePath(), recursive,
                            classes);
                } else {
                    // 如果是java类文件 去掉后面的.class 只留下类名
                    String className = file.getName().substring(0,
                            file.getName().length() - 6);
                    try {
                        // 添加到集合中去
                        //classes.add(Class.forName(packageName + '.' + className));
                        //经过回复同学的提醒，这里用forName有一些不好，会触发static方法，
                        //没有使用classLoader的load干净
                        classes.add(Thread.currentThread()
                                .getContextClassLoader()
                                .loadClass(packageName + '.' + className));
                    } catch (ClassNotFoundException e) {
                        log.error("scan class by classpath is failed. error:", e);
                    }
                }
            }
        }
    }
}
