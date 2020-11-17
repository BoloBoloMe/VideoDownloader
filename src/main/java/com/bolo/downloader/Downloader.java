package com.bolo.downloader;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 下载器实现
 */
@Slf4j
@Service
public class Downloader {
    final private TaskList taskList = new TaskList();
    final static private ExecutorService taskRunner = Executors.newFixedThreadPool(5);

    @Value("${video-path}")
    private String videoPath;

    @Value("${youtube-dl-path}")
    private String youtubeDLPath;

    /**
     * 新增任务
     *
     * @return 0:已在列表中，1:添加成功
     */
    public int addTask(String url) {
        return taskList.add(url) && submitTask(url) ? 1 : 0;
    }

    public Map<String, String> listTasks() {
        return taskList.list();
    }

    /**
     * 清空任务列表
     */
    public void clearTasks() {
        taskList.clear();
    }


    /**
     * 提交任务
     */
    private boolean submitTask(String url) {
        taskRunner.submit(() -> {
            taskList.lockNextPending(url);
            taskList.closure(url, Terminal.execYoutubeDL(url, youtubeDLPath));
        });
        return true;
    }

    /**
     * 返回视频存放路径下的文件列表
     */
    List<String> listVideo() {
        String[] list = new File(new File("").getAbsolutePath()).list((dir, name) -> Pattern.matches(".+(\\.mp4|\\.webm|\\.wmv|\\.avi|\\.dat|\\.asf|\\.mpeg|\\.mpg|\\.rm|\\.rmvb|\\.ram|\\.flv|\\.3gp|\\.mov|\\.divx|\\.dv|\\.vob|\\.mkv|\\.qt|\\.cpk|\\.fli|\\.flc|\\.f4v|\\.m4v|\\.mod|\\.m2t|\\.swf|\\.mts|\\.m2ts|\\.3g2|\\.mpe|\\.ts|\\.div|\\.lavf|\\.dirac){1}", name.toLowerCase()));
        return null == list ? new ArrayList<>() : Stream.of(list).collect(Collectors.toList());
    }
}
