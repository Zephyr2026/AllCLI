package com.allcli.gateway.admin.core;

import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * 导入 job 内存表（一期无持久化：重启丢 job，PRD 允许）。
 * 超过 {@value #MAX_JOBS} 时淘汰最旧的已完结 job，防内存泄漏。
 */
@Component
public class ImportJobStore {

    public static final int MAX_JOBS = 200;

    private final Map<String, ImportJob> jobs = new ConcurrentHashMap<>();

    public ImportJob create(String jobId, String systemRef) {
        ImportJob job = new ImportJob();
        job.setJobId(jobId);
        job.setSystemRef(systemRef);
        jobs.put(jobId, job);
        evictIfNeeded();
        return job;
    }

    public ImportJob get(String jobId) {
        return jobs.get(jobId);
    }

    private void evictIfNeeded() {
        if (jobs.size() <= MAX_JOBS) {
            return;
        }
        jobs.values().stream()
                .filter(j -> !"running".equals(j.getStatus()))
                .min(Comparator.comparing(ImportJob::getFinishedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .ifPresent(j -> jobs.remove(j.getJobId()));
    }
}
