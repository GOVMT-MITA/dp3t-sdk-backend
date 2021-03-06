/*
 * Copyright (c) 2020 Ubique Innovation AG <https://www.ubique.ch>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * SPDX-License-Identifier: MPL-2.0
 */
package org.dpppt.backend.sdk.ws.util;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import javax.sql.DataSource;

import org.dpppt.backend.sdk.data.gaen.GaenKeyInternalRowMapper;
import org.dpppt.backend.sdk.data.gaen.GaenKeyRowMapper;
import org.dpppt.backend.sdk.model.gaen.GaenKey;
import org.dpppt.backend.sdk.model.gaen.GaenKeyInternal;
import org.dpppt.backend.sdk.utils.UTCInstant;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.transaction.annotation.Transactional;

public class TestJDBCGaen {
  private static final String PGSQL = "pgsql";
  private final String dbType;
  private final NamedParameterJdbcTemplate jt;

  public TestJDBCGaen(String dbType, DataSource dataSource) {
    this.dbType = dbType;
    this.jt = new NamedParameterJdbcTemplate(dataSource);
  }

  @Transactional(readOnly = true)
  public List<GaenKeyInternal> getSortedExposedForKeyDate(
      Long keyDate, Long publishedAfter, Long publishedUntil) {
    MapSqlParameterSource params = new MapSqlParameterSource();
    params.addValue(
        "rollingPeriodStartNumberStart", UTCInstant.ofEpochMillis(keyDate).get10MinutesSince1970());
    params.addValue(
        "rollingPeriodStartNumberEnd",
        UTCInstant.ofEpochMillis(keyDate).plusDays(1).get10MinutesSince1970());
    params.addValue("publishedUntil", new Date(publishedUntil));

    String sql =
        "select pk_exposed_id, key, rolling_start_number, rolling_period, received_at, origin, 'CH' as country"
            + " from t_gaen_exposed where rolling_start_number >= :rollingPeriodStartNumberStart"
            + " and rolling_start_number < :rollingPeriodStartNumberEnd and received_at <"
            + " :publishedUntil";

    if (publishedAfter != null) {
      params.addValue("publishedAfter", new Date(publishedAfter));
      sql += " and received_at >= :publishedAfter";
    }

    sql += " order by pk_exposed_id desc";

    return jt.query(sql, params, new GaenKeyInternalRowMapper());
  }

  @Transactional(readOnly = false)
  public void upsertExposees(List<GaenKeyInternal> gaenKeys, UTCInstant receivedAt) {
    String sql = null;
    String sqlVisited = null;
    if (dbType.equals(PGSQL)) {
      sql =
          "insert into t_gaen_exposed (key, rolling_start_number, rolling_period,"
              + " received_at, expires_at, origin) values (:key, :rolling_start_number,"
              + " :rolling_period, :received_at, :expires_at, :origin) on conflict on"
              + " constraint gaen_exposed_key do nothing";
      sqlVisited =
              "insert into t_visited (pfk_exposed_id, country) values (:keyId, :country) on conflict on"
                  + " constraint PK_t_visited do nothing";
    } else {
      sql =
          "merge into t_gaen_exposed using (values(cast(:key as varchar(24)),"
              + " :rolling_start_number, :rolling_period, :received_at, :expires_at, cast(:origin as"
              + " varchar(10)))) as vals(key, rolling_start_number, rolling_period, received_at, expires_at,"
              + " origin) on t_gaen_exposed.key = vals.key when not matched then insert (key,"
              + " rolling_start_number, rolling_period, received_at, expires_at, origin) values (vals.key,"
              + " vals.rolling_start_number, vals.rolling_period, vals.received_at, vals.expires_at, vals.origin)";
      sqlVisited =
              "merge into t_visited using (values(:keyId, :country)) as vals(keyId, country) on"
                  + " t_visited.pfk_exposed_id = vals.keyId and t_visited.country = vals.country when"
                  + " not matched then insert (pfk_exposed_id, country) values (vals.keyId,"
                  + " vals.country)";
    }
    for (var gaenKey : gaenKeys) {
      MapSqlParameterSource params = new MapSqlParameterSource();
      params.addValue("key", gaenKey.getKeyData());
      params.addValue("rolling_start_number", gaenKey.getRollingStartNumber());
      params.addValue("rolling_period", gaenKey.getRollingPeriod());
      params.addValue("received_at", receivedAt.getDate());
      params.addValue("expires_at", UTCInstant.ofEpochMillis(((gaenKey.getRollingStartNumber() + gaenKey.getRollingPeriod()) * 10 * 60 + 7200L) * 1000).getDate());
      params.addValue("origin", "CH");
      KeyHolder keyHolder = new GeneratedKeyHolder();
      jt.update(sql, params, keyHolder);
      Object keyObject = keyHolder.getKeys().get("pk_exposed_id");
      if (keyObject != null) {
          int gaenKeyId = ((Integer) keyObject).intValue();
          MapSqlParameterSource visitedParams = new MapSqlParameterSource();
          visitedParams.addValue("keyId", gaenKeyId);
          visitedParams.addValue("country", "CH");
          jt.update(sqlVisited, visitedParams);
        }
      
    }
  }

  @Transactional(readOnly = false)
  public void upsertExposeesDebug(List<GaenKeyInternal> gaenKeys, UTCInstant receivedAt) {
    String sql = null;
    if (dbType.equals(PGSQL)) {
      sql =
          "insert into t_debug_gaen_exposed (key, rolling_start_number, rolling_period,"
              + " received_at, expires_at, device_name) values (:key,"
              + " :rolling_start_number, :rolling_period, :transmission_risk_level, :received_at, :expires_at, "
              + " 'test') on conflict on constraint debug_gaen_exposed_key do nothing";
    } else {
      sql =
          "merge into t_debug_gaen_exposed using (values(cast(:key as varchar(24)),"
              + " :rolling_start_number, :rolling_period, :transmission_risk_level, :received_at, :expires_at, "
              + " 'test')) as vals(key, rolling_start_number, rolling_period,"
              + " transmission_risk_level, received_at, expires_at, device_name) on t_debug_gaen_exposed.key ="
              + " vals.key when not matched then insert (key, rolling_start_number,"
              + " rolling_period, transmission_risk_level, received_at, expires_at, device_name) values"
              + " (vals.key, vals.rolling_start_number, vals.rolling_period,"
              + " vals.transmission_risk_level, vals.received_at, vals.expires_at, vals.device_name)";
    }
    var parameterList = new ArrayList<MapSqlParameterSource>();
    for (var gaenKey : gaenKeys) {
      MapSqlParameterSource params = new MapSqlParameterSource();
      params.addValue("key", gaenKey.getKeyData());
      params.addValue("rolling_start_number", gaenKey.getRollingStartNumber());
      params.addValue("rolling_period", gaenKey.getRollingPeriod());
      params.addValue("transmission_risk_level", gaenKey.getTransmissionRiskLevel());
      params.addValue("received_at", receivedAt.getDate());
      params.addValue("expires_at", UTCInstant.ofEpochMillis(((gaenKey.getRollingStartNumber() + gaenKey.getRollingPeriod()) * 10 * 60 + 7200L) * 1000).getDate());
      parameterList.add(params);
    }
    jt.batchUpdate(sql, parameterList.toArray(new MapSqlParameterSource[0]));
  }

  public void clear() {
    jt.update("DELETE FROM t_gaen_exposed;", new HashMap<>());
    jt.update("DELETE FROM t_debug_gaen_exposed;", new HashMap<>());
  }
}
