/*
 * Copyright (c) 2020 Ubique Innovation AG <https://www.ubique.ch>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

package org.dpppt.backend.sdk.data.gaen;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.dpppt.backend.sdk.model.gaen.GaenKey;
import org.dpppt.backend.sdk.model.gaen.GaenKeyInternal;
import org.dpppt.backend.sdk.utils.UTCInstant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

public class DebugJDBCGAENDataServiceImpl implements DebugGAENDataService {

  private static final Logger logger = LoggerFactory.getLogger(DebugJDBCGAENDataServiceImpl.class);

  private static final String PGSQL = "pgsql";
  
  private final String dbType;
  private final String originCountry;
  private final NamedParameterJdbcTemplate jt;

  public DebugJDBCGAENDataServiceImpl(String dbType, String originCountry, DataSource dataSource) {
    this.dbType = dbType;
    this.originCountry = originCountry;
    this.jt = new NamedParameterJdbcTemplate(dataSource);    
  }

  @Override
  @Transactional(readOnly = false)
  public void upsertExposees(String deviceName, List<GaenKeyInternal> gaenKeys) {
    String sql = null;
    if (dbType.equals(PGSQL)) {
      sql =
          "insert into t_debug_gaen_exposed (device_name, key, rolling_start_number,"
              + " rolling_period, transmission_risk_level) values (:device_name, :key,"
              + " :rolling_start_number, :rolling_period, :transmission_risk_level) on conflict on"
              + " constraint debug_gaen_exposed_key do nothing";
    } else {
      sql =
          "merge into t_debug_gaen_exposed using (values(cast(:device_name as varchar(200)),"
              + " cast(:key as varchar(24)), :rolling_start_number, :rolling_period,"
              + " :transmission_risk_level)) as vals(device_name, key, rolling_start_number,"
              + " rolling_period, transmission_risk_level) on t_gaen_exposed.key = vals.key when"
              + " not matched then insert (device_name, key, rolling_start_number, rolling_period,"
              + " transmission_risk_level) values (vals.device_name, vals.key,"
              + " vals.rolling_start_number, vals.rolling_period, transmission_risk_level)";
    }
    var parameterList = new ArrayList<MapSqlParameterSource>();
    for (var gaenKey : gaenKeys) {
      MapSqlParameterSource params = new MapSqlParameterSource();
      params.addValue("device_name", deviceName);
      params.addValue("key", gaenKey.getKeyData());
      params.addValue("rolling_start_number", gaenKey.getRollingStartNumber());
      params.addValue("rolling_period", gaenKey.getRollingPeriod());
      params.addValue("transmission_risk_level", gaenKey.getTransmissionRiskLevel());
      parameterList.add(params);
    }
    jt.batchUpdate(sql, parameterList.toArray(new MapSqlParameterSource[0]));
  }

  @Override
  @Transactional(readOnly = true)
  public Map<String, List<GaenKeyInternal>> getSortedExposedForBatchReleaseTime(
      UTCInstant batchReleaseTime, Duration releaseBucketDuration) {
    String sql =
        "select pk_exposed_id, device_name, key, rolling_start_number, rolling_period,"
            + " transmission_risk_level, '" + originCountry + "' as country, '" + originCountry + "' as origin, received_at, received_at as expires_at, "
            + " 'CONFIRMED_TEST' as report_type, 14 as days_since_onset_of_symptoms, null as efgs_batch_tag"
            + " from t_debug_gaen_exposed where received_at >= :startBatch"
            + " and received_at < :batchReleaseTime order by pk_exposed_id desc";
    MapSqlParameterSource params = new MapSqlParameterSource();
    params.addValue("batchReleaseTime", batchReleaseTime.getDate());
    params.addValue("startBatch", batchReleaseTime.minus(releaseBucketDuration).getDate());
    return jt.query(sql, params, new DebugGaenKeyResultSetExtractor());
  }

}
