package com.bolo.downloader.util;


import com.bolo.downloader.factory.ConfFactory;
import com.bolo.downloader.respool.log.LoggerFactory;
import com.bolo.downloader.respool.log.MyLogger;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class PageUtil {
    private static final MyLogger log = LoggerFactory.getLogger(PageUtil.class);
    private static final ConcurrentHashMap<String, Page> cache = new ConcurrentHashMap<>(32);
    private static final AtomicInteger allHitCount = new AtomicInteger(0);
    private static String basic = null;
    private static final Page PAGE_NOT_FUND = new Page(null,
            "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1, maximum-scale=1\"><title>404</title><link rel=\"stylesheet\" href=\"../layui/css/layui.css\"></head><body><div style=\"width:100%; height:400px;position:absolute; left:50%; top:50%; margin-left: -300px; margin-top: -200px;\"><i class=\"layui-icon layui-icon-face-cry\" style=\"font-size: 200px; color: crimson;\"></i><span style=\"font-size: 50px;\">404:未曾设想的道路</span></div></body></html>".getBytes(Charset.forName("utf8")),
            "text/html");
    private static final Page SERVER_ERROR = new Page(null,
            "<!DOCTYPE html><html><head>\n<meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1, maximum-scale=1\"><title>500</title><link rel=\"stylesheet\" href=\"../layui/css/layui.css\"></head><body><div style=\"width:100%; height:400px;position:absolute; left:50%; top:50%; margin-left: -300px; margin-top: -200px;\"><i class=\"layui-icon layui-icon-face-surprised\" style=\"font-size: 200px; color: crimson;\"></i><span style=\"font-size: 50px;\">ERROR: 一袋米要抗几楼</span></div></body></html>".getBytes(Charset.forName("UTF-8")),
            "text/html");

    public static Page findPage(String uri, Map<String, List<String>> params) {
        // 统一的uri
        String uUri = uri;
        if (uri.equals("/v") && params.get("p") != null) {
            String pageName = params.get("p").get(0);
            uUri = "page" + File.separator + pageName + ".html";
        }
        // find cache
        Page page = cache.get(uri);
        // find disk
        if (null == page) {
            File target = new File(basic == null ? (basic = ConfFactory.get("staticFilePath")) : basic, uUri);
            if (target.exists()) {
                try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(target))) {
                    int len = (int) target.length();
                    byte[] content = new byte[len];
                    in.read(content);
                    page = new Page(uUri, content, getContentType(uUri));
                    caching(page);
                } catch (IOException e) {
                    log.error("page file read error!", e);
                    return SERVER_ERROR;
                }
            }
        } else {
            allHitCount.incrementAndGet();
            page.hit();
        }
        return page == null ? PAGE_NOT_FUND : page;
    }

    private static String getContentType(String uri) {
        String flag;
        int flagIndex;
        if ((flagIndex = uri.lastIndexOf('.')) > 0) {
            flag = uri.substring(flagIndex, uri.length());
        } else {
            return "text/html";
        }

        if (flag.equals(".html")) {
            return "text/html";
        }
        if (flag.equals(".css")) {
            return "text/css";
        }
        if (flag.equals(".js")) {
            return "application/javascript";
        }
        if (flag.equals(".ico")) {
            return "application/octet-stream";
        }
        if (flag.equals(".woff2")) {
            return "application/font-woff2";
        }
        return "text/plain";
    }

    private static void caching(Page page) {
        if (cache.size() < 32) {
            cache.put(page.getUri(), page);
        } else {
            int average = allHitCount.get() / cache.size();
            for (String uri : cache.keySet()) {
                Page cPage = cache.get(uri);
                if (cPage != null && cPage.getHitCount() < average) {
                    cache.remove(uri);
                }
            }
            cache.put(page.getUri(), page);
        }
    }


    public static class Page {
        String uri;
        byte[] content;
        String contentType;
        AtomicInteger hitCount = new AtomicInteger(0);

        Page(String uri, byte[] content, String contentType) {
            this.uri = uri;
            this.content = content;
            this.contentType = contentType;
        }

        public String getUri() {
            return uri;
        }

        public byte[] getContent() {
            return content;
        }

        public String getContentType() {
            return contentType;
        }

        int getHitCount() {
            return hitCount.get();
        }

        void hit() {
            this.hitCount.incrementAndGet();
        }
    }
}
