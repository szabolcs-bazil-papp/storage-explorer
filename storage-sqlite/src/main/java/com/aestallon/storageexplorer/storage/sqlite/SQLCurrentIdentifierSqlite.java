package com.aestallon.storageexplorer.storage.sqlite;

import java.util.List;
import org.smartbit4all.core.SB4FunctionImpl;
import org.smartbit4all.domain.service.identifier.CurrentIdentifier;
import org.springframework.jdbc.core.JdbcTemplate;

public class SQLCurrentIdentifierSqlite
    extends SB4FunctionImpl<String, Long>
    implements CurrentIdentifier {

  protected JdbcTemplate jdbcTemplate;

  public SQLCurrentIdentifierSqlite(final JdbcTemplate jdbcTemplate) {
    super();
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public void execute() throws Exception {
    final List<Long> ret = jdbcTemplate.query("""
            select value as "VALUE"
              from %s
             limit 1;""".formatted(input),
        (r, i) -> r.getLong("VALUE"));
    output = (ret.isEmpty()) ? 0L : ret.getFirst();
  }
}
