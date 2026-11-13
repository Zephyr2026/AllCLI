package com.allcli.gateway.admin.core;

import jakarta.annotation.PreDestroy;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

/** 导入执行线程池（2 线程；PRD 性能口径 ≤30s/200 接口，重导不阻塞提交）。 */
@Component
public class ImportExecutor {

    private final ExecutorService executor = Executors.newFixedThreadPool(2, new ThreadFactory() {
        private final AtomicInteger seq = new AtomicInteger();

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "import-" + seq.incrementAndGet());
            t.setDaemon(true);
            return t;
        }
    });

    public void submit(Runnable task) {
        executor.submit(task);
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}
