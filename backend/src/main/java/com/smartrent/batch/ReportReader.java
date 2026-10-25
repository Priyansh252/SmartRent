package com.smartrent.batch;

import com.smartrent.domain.ReportType;
import java.sql.ResultSetMetaData;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemStreamException;
import org.springframework.batch.item.ItemStreamReader;
import org.springframework.batch.item.database.JdbcCursorItemReader;

/** Streams one report's rows straight from the database with a JDBC cursor (nothing is loaded into memory at once). */
public class ReportReader implements ItemStreamReader<List<String>> {

    private final JdbcCursorItemReader<List<String>> delegate = new JdbcCursorItemReader<>();

    public ReportReader(DataSource dataSource, ReportType type, LocalDate from, LocalDate to, LocalDate today) {
        ReportDefinitions.Definition def = ReportDefinitions.of(type);
        Object[] args = ReportDefinitions.bind(def.params(), from, to, today);
        delegate.setName("reader-" + type.name());
        delegate.setDataSource(dataSource);
        delegate.setSql(def.sql());
        delegate.setFetchSize(200);
        delegate.setSaveState(false);
        delegate.setPreparedStatementSetter(ps -> {
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
        });
        delegate.setRowMapper((rs, rowNum) -> {
            ResultSetMetaData md = rs.getMetaData();
            List<String> row = new ArrayList<>(md.getColumnCount());
            for (int i = 1; i <= md.getColumnCount(); i++) {
                String v = rs.getString(i);
                row.add(v == null ? "" : v);
            }
            return row;
        });
        try {
            delegate.afterPropertiesSet();
        } catch (Exception e) {
            throw new IllegalStateException("Invalid report reader configuration", e);
        }
    }

    @Override
    public List<String> read() throws Exception {
        return delegate.read();
    }

    @Override
    public void open(ExecutionContext executionContext) throws ItemStreamException {
        delegate.open(executionContext);
    }

    @Override
    public void update(ExecutionContext executionContext) throws ItemStreamException {
        delegate.update(executionContext);
    }

    @Override
    public void close() throws ItemStreamException {
        delegate.close();
    }
}
