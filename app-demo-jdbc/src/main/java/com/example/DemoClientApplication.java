/*
 * Copyright (C) 2025 Szabolcs Bazil Papp
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with this program.
 * If not, see <http://www.gnu.org/licenses/>.
 */

package com.example;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootApplication
public class DemoClientApplication {

  private record Group(String name, String description) {

    private static Group fromResultSet(ResultSet rs) throws SQLException {
      return new Group(rs.getString("NAME"), rs.getString("DESC"));
    }

    @Override
    public String toString() {
      return "{ name: %s, description: %s }\n".formatted(name, description);
    }
  }


  private static final Logger log = LoggerFactory.getLogger(DemoClientApplication.class);

  public static void main(String[] args) {
    SpringApplication.run(DemoClientApplication.class, args);
  }


  @Bean
  CommandLineRunner init(JdbcTemplate jdbcTemplate) {
    return args -> {
      List<Group> groups = jdbcTemplate.query("""
          every 'Group'
           from 'org'
          
           show 'name' as 'NAME'
           show 'description' as 'DESC'""", (r, i) -> Group.fromResultSet(r));
      log.info("{}", groups);
      System.exit(0);
    };
  }
}
