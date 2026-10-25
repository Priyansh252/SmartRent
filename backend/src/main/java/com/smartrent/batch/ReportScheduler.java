package com.smartrent.batch;

import com.smartrent.config.AppProperties;
import com.smartrent.domain.ReportType;
import com.smartrent.service.ReportService;
import java.time.Clock;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Generates every report on a configurable cron (smartrent.reports.schedule-cron) for the look-back window. */
@Component
@ConditionalOnProperty(prefix = "smartrent.reports", name = "scheduler-enabled", havingValue = "true", matchIfMissing = true)
public class ReportScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReportScheduler.class);

    private final ReportService reports;
    private final AppProperties props;
    private final Clock clock;

    public ReportScheduler(ReportService reports, AppProperties props, Clock clock) {
        this.reports = reports;
        this.props = props;
        this.clock = clock;
    }

    @Scheduled(cron = "${smartrent.reports.schedule-cron:0 0 2 * * *}")
    public void generateScheduledReports() {
        LocalDate to = LocalDate.now(clock);
        LocalDate from = to.minusDays(props.getReports().getLookbackDays());
        for (ReportType type : ReportType.values()) {
            try {
                var run = reports.trigger(type, from, to, ReportService.SCHEDULER);
                log.info("Scheduled report {} -> {} (run {})", type, run.status(), run.id());
            } catch (Exception e) {
                log.error("Scheduled report {} could not be started", type, e);
            }
        }
    }
}
