package com.bolo.downloader.net;

import com.bolo.downloader.utils.FileDownloadHelper;
import com.bolo.downloader.utils.PageHelper;
import com.bolo.downloader.utils.ResponseHelper;
import com.bolo.downloader.utils.RestHelper;
import io.netty.channel.*;
import io.netty.handler.codec.http.*;

import java.io.*;
import java.net.URLDecoder;
import java.util.*;
import java.util.regex.Pattern;

/**
 * 根据请求地址，访问页面或下载文件
 */
public class HttpServerHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private FullHttpRequest request;

    @Override
    public void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
        this.request = request;
        if (!request.decoderResult().isSuccess()) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, request);
            return;
        }
        final String uri = sanitizeUri(request.uri());
        if (null == uri) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, request);
            return;
        }
        Map<String, List<String>> params = new QueryStringDecoder(request.uri()).parameters();
        if (HttpMethod.GET.equals(request.method())) {
            // access page apply GET method
            PageHelper.toPage(uri, params, ctx, request);
        } else if (HttpMethod.POST.equals(request.method())) {
            // restful & download file apply POST method
            if (uri.endsWith("/df")) {
                FileDownloadHelper.download(uri, params, ctx, request);
            } else {
                RestHelper.handle(uri, params, ctx, request);
            }
        } else {
            ResponseHelper.sendError(ctx, HttpResponseStatus.METHOD_NOT_ALLOWED, request);
            return;
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        if (ctx.channel().isActive()) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR, request);
        }
    }


    private static final Pattern INSECURE_URI = Pattern.compile(".*[<>&\"].*");

    private static String sanitizeUri(String uri) throws UnsupportedEncodingException {
        // Decode the path.
        uri = URLDecoder.decode(uri, "UTF-8");
        if (uri.isEmpty() || uri.charAt(0) != '/') {
            return null;
        }
        // Simplistic dumb security check.
        if (uri.contains("/.") ||
                uri.contains("./") ||
                uri.charAt(0) == '.' || uri.charAt(uri.length() - 1) == '.' ||
                INSECURE_URI.matcher(uri).matches()) {
            return null;
        }
        return uri;
    }
}
