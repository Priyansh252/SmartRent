package com.smartrent.service;

import com.smartrent.config.AppProperties;
import com.smartrent.domain.ReportRun;
import com.smartrent.domain.ReportStatus;
import com.smartrent.domain.ReportType;
import com.smartrent.dto.Dtos.PageResponse;
import com.smartrent.dto.Dtos.ReportRunDto;
import com.smartrent.repo.ReportRunRepository;
import com.smartrent.web.error.ApiException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/**
 * Launches the Spring Batch report jobs and records every execution in {@code report_runs} (the admin history).
 * Intentionally NOT transactional: the RUNNING row must be visible/committed before the job starts, and Spring Batch
 * manages its own transactions.
 */
@Service
public class ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportService.class);
    public static final String SCHEDULER = "SCHEDULER";

    private final ReportRunRepository runs;
    private final JobLauncher launcher;
    private final Map<String, Job> jobs;
    private final AppProperties props;
    private final Clock clock;
    private final AuditService audit;
    /** Guards against two concurrent runs of the same report type inside one application instance. */
    private final Set<ReportType> inFlight = ConcurrentHashMap.newKeySet();

    public ReportService(ReportRunRepository runs, JobLauncher launcher, Map<String, Job> jobs, AppProperties props,
                         Clock clock, AuditService audit) {
        this.runs = runs;
        this.launcher = launcher;
        this.jobs = jobs;
        this.props = props;
        this.clock = clock;
        this.audit = audit;
    }

    /**
     * Runs one report synchronously. {@code from} is inclusive, {@code to} exclusive. {@code triggerSource} is the
     * admin's e-mail or {@link #SCHEDULER}; scheduled runs use a deterministic job key so a repeated schedule for the
     * same window is rejected by Spring Batch itself and recorded as SKIPPED.
     */
    public ReportRunDto trigger(ReportType type, LocalDate from, LocalDate to, String triggerSource) {
        if (from == null || to == null || !to.isAfter(from)) {
            throw ApiException.badRequest("INVALID_REPORT_RANGE", "'to' (exclusive) must be after 'from'.");
        }
        if (ChronoUnit.DAYS.between(from, to) > 366) {
            throw ApiException.badRequest("INVALID_REPORT_RANGE", "A report can cover at most 366 days.");
        }
        Job job = jobs.get(type.jobName());
        if (job == null) {
            throw new IllegalStateException("No batch job registered for " + type);
        }
        if (!inFlight.add(type)) {
            throw ApiException.conflict("REPORT_ALREADY_RUNNING", type + " is already being generated. Try again shortly.");
        }
        ReportRun run = new ReportRun();
        try {
            run.setReportType(type);
            run.setFromDate(from);
            run.setToDate(to);
            run.setTriggerSource(triggerSource);
            run.setStartedAt(clock.instant());
            run = runs.save(run);

            Path dir = Paths.get(props.getReports().getOutputDir()).toAbsolutePath().normalize();
            Files.createDirectories(dir);
            String fileName = type.name().toLowerCase() + "_" + from + "_" + to + "_run" + run.getId() + ".csv";
            Path file = dir.resolve(fileName);

            boolean scheduled = SCHEDULER.equals(triggerSource);
            JobParameters params = new JobParametersBuilder()
                    .addString("type", type.name())
                    .addString("from", from.toString())
                    .addString("to", to.toString())
                    .addString("today", LocalDate.now(clock).toString())
                    .addString("output", file.toString())
                    .addString("launchKey", scheduled ? "scheduled-" + LocalDate.now(clock) : UUID.randomUUID().toString())
                    .toJobParameters();

            JobExecution exec = launcher.run(job, params);
            run.setBatchExecutionId(exec.getId());
            run.setFinishedAt(clock.instant());
            if (exec.getStatus() == BatchStatus.COMPLETED) {
                run.setStatus(ReportStatus.COMPLETED);
                run.setFileName(fileName);
                run.setFilePath(file.toString());
                run.setRowCount(exec.getStepExecutions().stream().mapToLong(StepExecution::getWriteCount).sum());
            } else {
                run.setStatus(ReportStatus.FAILED);
                run.setErrorMessage(failureMessage(exec));
                log.error("Report {} (run {}) finished with status {}", type, run.getId(), exec.getStatus());
            }
        } catch (JobInstanceAlreadyCompleteException | JobExecutionAlreadyRunningException e) {
            run.setStatus(ReportStatus.SKIPPED);
            run.setFinishedAt(clock.instant());
            run.setErrorMessage("Skipped: this report was already generated for the same schedule window.");
        } catch (IOException e) {
            run.setStatus(ReportStatus.FAILED);
            run.setFinishedAt(clock.instant());
            run.setErrorMessage("Cannot write to the report output directory.");
            log.error("Report output directory problem", e);
        } catch (Exception e) {
            run.setStatus(ReportStatus.FAILED);
            run.setFinishedAt(clock.instant());
            run.setErrorMessage(truncate("Job could not be completed: " + e.getClass().getSimpleName()));
            log.error("Report {} failed", type, e);
        } finally {
            if (run.getId() != null) {
                run = runs.save(run);
            }
            inFlight.remove(type);
        }
        if (!SCHEDULER.equals(triggerSource)) {
            audit.log(triggerSource, "REPORT_TRIGGERED", "ReportRun", run.getId(), type + " " + from + ".." + to + " -> " + run.getStatus());
        }
        return Mappers.toDto(run);
    }

    public PageResponse<ReportRunDto> history(int page, int size) {
        return PageResponse.of(runs.findAllByOrderByIdDesc(PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100)))
                .map(Mappers::toDto));
    }

    /** Resolves a finished report's CSV; the path stored in the DB must stay inside the configured output directory. */
    public Resource file(Long runId) {
        ReportRun run = runs.findById(runId).orElseThrow(() -> ApiException.notFound("Report run", runId));
        if (run.getStatus() != ReportStatus.COMPLETED || run.getFilePath() == null) {
            throw ApiException.notFound("Report file for run", runId);
        }
        Path base = Paths.get(props.getReports().getOutputDir()).toAbsolutePath().normalize();
        Path file = Paths.get(run.getFilePath()).toAbsolutePath().normalize();
        if (!file.startsWith(base) || !Files.isRegularFile(file)) {
            throw ApiException.notFound("Report file for run", runId);
        }
        return new FileSystemResource(file);
    }

    public ReportRun requireRun(Long id) {
        return runs.findById(id).orElseThrow(() -> ApiException.notFound("Report run", id));
    }

    private static String failureMessage(JobExecution exec) {
        return exec.getAllFailureExceptions().stream().findFirst()
                .map(t -> truncate("Job failed: " + t.getClass().getSimpleName()))
                .orElse("Job finished with status " + exec.getStatus() + ".");
    }

    private static String truncate(String s) {
        return s.length() > 480 ? s.substring(0, 480) : s;
    }
}
