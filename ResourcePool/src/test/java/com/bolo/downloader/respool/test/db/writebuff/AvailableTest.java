package com.bolo.downloader.respool.test.db.writebuff;


import com.bolo.downloader.respool.db.LogWriteBuff;

import java.util.ArrayList;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class AvailableTest {

    private static int workThreadNum = 300;
    private static int dataCount = 1000;
    private static int dataCountSum = workThreadNum * dataCount;
    private static CyclicBarrier cyclicBarrier = new CyclicBarrier(workThreadNum + 1);
    private static ExecutorService workers = Executors.newCachedThreadPool();
    private static LogWriteBuff writeBuff = new LogWriteBuff(dataCount);

    public static void main(String[] args) throws BrokenBarrierException, InterruptedException {
        final AtomicInteger threadNum = new AtomicInteger(0);
        // 工作线程分别往writeBuff中写入数据
        for (int i = 1; i <= workThreadNum; i++) {
            workers.execute(() -> {
                // 准备测试数据
                String[] datas = new String[dataCount];
                int min = dataCount * threadNum.incrementAndGet();
                for (int dc = 0; dc < dataCount; dc++) datas[dc] = "" + (min + dc);
                try {
                    // 等待系统就绪后写入缓冲
                    System.out.println("线程" + Thread.currentThread().getName() + "测试数据准备完毕");
                    cyclicBarrier.await();
                    for (String data : datas) writeBuff.push(data);
                    cyclicBarrier.await();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }

        // 主线程从writeBuff中获取数据，并检查数据是否正确
        long startTime = System.currentTimeMillis();
        cyclicBarrier.await();
        System.out.println("开始操作缓存");
        ArrayList<String> result = new ArrayList<>(dataCountSum);
        while (result.size() < dataCountSum) {
            String line = writeBuff.pop();
            if (null != line) result.add(line);
        }
        cyclicBarrier.await();
        long endTime = System.currentTimeMillis();
        System.out.println("工作线程运行完毕，运行时间:" + (endTime - startTime) / 1000 + "秒，开始检查操作结果");
        System.out.println(String.format("预计获取数据量：%d 个，实际获取数据量：%d 个，丢失数据：%d 个", dataCountSum, result.size(), dataCountSum - result.size()));
        for (int i = 0; i < result.size(); i++) {
            for (int j = i + 1; j < result.size(); j++) {
                if (result.get(i).equals(result.get(j))) System.out.println("重复数据：" + result.get(j));
            }
        }
        System.out.println("结果检查完毕");
    }
}
