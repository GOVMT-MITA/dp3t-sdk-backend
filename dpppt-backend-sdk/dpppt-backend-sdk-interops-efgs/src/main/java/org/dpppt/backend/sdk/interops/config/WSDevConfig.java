/*
 * Copyright (c) 2020 Ubique Innovation AG <https://www.ubique.ch>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

package org.dpppt.backend.sdk.interops.config;

import java.time.Duration;

import javax.sql.DataSource;

import org.dpppt.backend.sdk.data.gaen.FakeKeyService;
import org.dpppt.backend.sdk.data.gaen.GAENDataService;
import org.dpppt.backend.sdk.data.gaen.JDBCGAENDataServiceImpl;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

@Configuration
@Profile("dev")
public class WSDevConfig extends WSBaseConfig {

  @Bean
  @Override
  public DataSource dataSource() {
    return new EmbeddedDatabaseBuilder()
        .generateUniqueName(true)
        .setType(EmbeddedDatabaseType.HSQL)
        .build();
  }

  @Bean
  @Override
  public Flyway flyway() {
    Flyway flyWay =
        Flyway.configure()
            .dataSource(dataSource())
            .locations("classpath:/db/migration/hsqldb")
            .load();
    flyWay.migrate();
    return flyWay;
  }

  @Override
  public String getDbType() {
    return "hsqldb";
  }

  @Value("${ws.gaen.randomkeysenabled: true}")
  boolean randomkeysenabled;

  @Value("${ws.gaen.randomkeyamount: 10}")
  int randomkeyamount;

  @Value("${ws.app.gaen.key_size: 16}")
  int gaenKeySizeBytes;
  
  @Bean
  public FakeKeyService fakeKeyService(GAENDataService fakeGaenService) {
    try {
      return new FakeKeyService(
          fakeGaenService,
          Integer.valueOf(randomkeyamount),
          Integer.valueOf(gaenKeySizeBytes),
          Duration.ofDays(retentionDays),
          randomkeysenabled);
    } catch (Exception ex) {
      throw new RuntimeException("FakeKeyService could not be instantiated", ex);
    }
  }
  
}
