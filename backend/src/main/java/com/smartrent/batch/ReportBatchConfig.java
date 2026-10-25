package com.smartrent.batch;

import com.smartrent.config.AppProperties;
import com.smartrent.domain.ReportType;
import com.smartrent.repo.BookingRepository;
import com.smartrent.util.CsvUtil;
import java.time.LocalDate;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.file.FlatFileItemWriter;
import org.springframework.batch.item.file.builder.FlatFileItemWriterBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Five Spring Batch jobs (one per {@link ReportType}); each is a single chunk-oriented step:
 * JDBC cursor reader -> processor -> CSV FlatFileItemWriter. Reader, processor and writer are step-scoped, so they are
 * built per execution from the job parameters (type, from, to, today, output).
 */
@Configuration
public class ReportBatchConfig {

    // -------------------------------------------------------------- step-scoped components

    @Bean
    @StepScope
    public ReportReader reportReader(DataSource dataSource,
                                     @Value("#{jobParameters['type']}") String type,
                                     @Value("#{jobParameters['from']}") String from,
                                     @Value("#{jobParameters['to']}") String to,
                                     @Value("#{jobParameters['today']}") String today) {
        return new ReportReader(dataSource, ReportType.valueOf(type), LocalDate.parse(from), LocalDate.parse(to),
                LocalDate.parse(today));
    }

    @Bean
    @StepScope
    public ReportProcessor reportProcessor(BookingRepository bookings,
                                           @Value("#{jobParameters['type']}") String type,
                                           @Value("#{jobParameters['from']}") String from,
                                           @Value("#{jobParameters['to']}") String to) {
        return new ReportProcessor(ReportType.valueOf(type), LocalDate.parse(from), LocalDate.parse(to), bookings);
    }

    @Bean
    @StepScope
    public FlatFileItemWriter<List<String>> reportWriter(@Value("#{jobParameters['type']}") String type,
                                                         @Value("#{jobParameters['output']}") String output) {
        List<String> headers = ReportDefinitions.of(ReportType.valueOf(type)).headers();
        return new FlatFileItemWriterBuilder<List<String>>()
                .name("writer-" + type)
                .resource(new FileSystemResource(output))
                .lineAggregator(CsvUtil::line)
                .headerCallback(w -> w.write(String.join(",", headers)))
                .lineSeparator("\r\n")
                .encoding("UTF-8")
                .shouldDeleteIfExists(true)
                .saveState(false)
                .build();
    }

    // -------------------------------------------------------------- jobs (bean name = ReportType.jobName())

    @Bean(name = "revenue-summary-job")
    public Job revenueSummaryJob(JobRepository repo, PlatformTransactionManager tm, AppProperties props,
                                 ReportReader reader, ReportProcessor processor, FlatFileItemWriter<List<String>> writer) {
        return job(ReportType.REVENUE_SUMMARY, repo, tm, props, reader, processor, writer);
    }

    @Bean(name = "booking-counts-job")
    public Job bookingCountsJob(JobRepository repo, PlatformTransactionManager tm, AppProperties props,
                                ReportReader reader, ReportProcessor processor, FlatFileItemWriter<List<String>> writer) {
        return job(ReportType.BOOKING_COUNTS, repo, tm, props, reader, processor, writer);
    }

    @Bean(name = "fleet-availability-job")
    public Job fleetAvailabilityJob(JobRepository repo, PlatformTransactionManager tm, AppProperties props,
                                    ReportReader reader, ReportProcessor processor, FlatFileItemWriter<List<String>> writer) {
        return job(ReportType.FLEET_AVAILABILITY, repo, tm, props, reader, processor, writer);
    }

    @Bean(name = "vehicle-usage-job")
    public Job vehicleUsageJob(JobRepository repo, PlatformTransactionManager tm, AppProperties props,
                               ReportReader reader, ReportProcessor processor, FlatFileItemWriter<List<String>> writer) {
        return job(ReportType.VEHICLE_USAGE, repo, tm, props, reader, processor, writer);
    }

    @Bean(name = "fleet-utilization-job")
    public Job fleetUtilizationJob(JobRepository repo, PlatformTransactionManager tm, AppProperties props,
                                   ReportReader reader, ReportProcessor processor, FlatFileItemWriter<List<String>> writer) {
        return job(ReportType.FLEET_UTILIZATION, repo, tm, props, reader, processor, writer);
    }

    private Job job(ReportType type, JobRepository repo, PlatformTransactionManager tm, AppProperties props,
                    ReportReader reader, ReportProcessor processor, FlatFileItemWriter<List<String>> writer) {
        Step step = new StepBuilder(type.jobName() + "-step", repo)
                .<List<String>, List<String>>chunk(props.getReports().getChunkSize(), tm)
                .reader(reader)
                .processor(processor)
                .writer(writer)
                .build();
        return new JobBuilder(type.jobName(), repo).start(step).build();
    }
}
