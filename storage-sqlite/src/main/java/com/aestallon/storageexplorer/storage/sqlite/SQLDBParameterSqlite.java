package com.aestallon.storageexplorer.storage.sqlite;

import org.smartbit4all.domain.utility.SupportedDatabase;
import org.smartbit4all.sql.config.SQLDBParameterBase;

public class SQLDBParameterSqlite extends SQLDBParameterBase {
  
  public SQLDBParameterSqlite() {
    type = SupportedDatabase.SQLITE;
  }

  @Override
  public String getDatetimeSQL() {
    return "select current_timestamp;";
  }

  @Override
  public String getTableNamesSQL() {
    return """
        select name
          from sqlite_schema
         where type = 'table'
           and name not like 'sqlite_%';""";
  }
  
}
