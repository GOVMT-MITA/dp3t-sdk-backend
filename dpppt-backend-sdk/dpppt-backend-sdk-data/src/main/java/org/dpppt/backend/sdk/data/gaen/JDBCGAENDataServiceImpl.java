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
import java.util.stream.Collectors;

import javax.sql.DataSource;
import org.dpppt.backend.sdk.model.gaen.GaenKey;
import org.dpppt.backend.sdk.model.gaen.GaenKeyInternal;
import org.dpppt.backend.sdk.utils.UTCInstant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.transaction.annotation.Transactional;

public class JDBCGAENDataServiceImpl implements GAENDataService {

  private static final Logger logger = LoggerFactory.getLogger(JDBCGAENDataServiceImpl.class);

  private static final String PGSQL = "pgsql";
  private final String dbType;
  private final NamedParameterJdbcTemplate jt;
  private final Duration releaseBucketDuration;
  // Time skew means the duration for how long a key still is valid __after__ it has expired (e.g 2h
  // for now
  // https://developer.apple.com/documentation/exposurenotification/setting_up_a_key_server?language=objc)
  private final Duration timeSkew;

  // the origin country is used for the "default" visited country for all insertions that do not
  // provide the visited countries for the key, so all v1 and non-international v2 inserted keys.
  // the origin country is also the default for returning keys.
  private final String originCountry;

  // these are all other countries that are connected to the system. if requests must include all
  // international keys, then this list is added to the origin country.
  private final List<String> allOtherCountries;
  
  public JDBCGAENDataServiceImpl(
      String dbType,
      DataSource dataSource,
      Duration releaseBucketDuration,
      Duration timeSkew,
      String originCountry,
      List<String> allOtherCountries) {
    this.dbType = dbType;
    this.jt = new NamedParameterJdbcTemplate(dataSource);
    this.releaseBucketDuration = releaseBucketDuration;
    this.timeSkew = timeSkew;
    this.originCountry = originCountry;
    this.allOtherCountries = allOtherCountries;
  }

  @Override
  public void upsertExposee(
      GaenKeyInternal gaenKey, UTCInstant now) {
    internalUpsertKey(gaenKey, now);
  }

  @Override
  @Transactional(readOnly = false)
  public void upsertExposees(List<GaenKeyInternal> gaenKeys, UTCInstant now) {
    upsertExposeesDelayed(gaenKeys, null, now);
  }

  @Override
  @Transactional(readOnly = false)
  public void upsertExposeesDelayed(
      List<GaenKeyInternal> gaenKeys, UTCInstant delayedReceivedAt, UTCInstant now) {
    // Calculate the `receivedAt` just at the end of the current releaseBucket.
    var receivedAt =
        delayedReceivedAt == null
            ? now.roundToNextBucket(releaseBucketDuration).minus(Duration.ofMillis(1))
            : delayedReceivedAt;

    for (var gaenKey : gaenKeys) {
      internalUpsertKey(gaenKey, receivedAt);
    }
  }

  @Override
  @Transactional(readOnly = true)
  public List<GaenKeyInternal> getSortedExposedForKeyDate(
      UTCInstant keyDate, UTCInstant publishedAfter, UTCInstant publishedUntil, UTCInstant now, boolean international) {
    MapSqlParameterSource params = new MapSqlParameterSource();
    params.addValue("rollingPeriodStartNumberStart", keyDate.get10MinutesSince1970());
    params.addValue("rollingPeriodStartNumberEnd", keyDate.plusDays(1).get10MinutesSince1970());
    params.addValue("publishedUntil", publishedUntil.getDate());
    List<String> coi = countriesOfInterest(international);
    params.addValue("origins", coi);
    params.addValue("countries", coi);
    
    String sql =
        "select keys.pk_exposed_id, keys.key, keys.rolling_start_number, keys.rolling_period, "
    		+ " keys.report_type, keys.days_since_onset_of_symptoms, keys.origin, v.country"
            + " from t_gaen_exposed "
            + " as keys left outer join t_visited v on keys.pk_exposed_id = v.pfk_exposed_id"
        	+ " where keys.rolling_start_number >= :rollingPeriodStartNumberStart"
            + " and keys.rolling_start_number < :rollingPeriodStartNumberEnd and keys.received_at <"
            + " :publishedUntil and"
            + " (keys.origin in (:origins) OR v.country in (:countries))";
    // we need to subtract the time skew since we want to release it iff rolling_start_number +
    // rolling_period + timeSkew < NOW
    // note though that since we use `<` instead of `<=` a key which is valid until 24:00 will be
    // accepted until 02:00 (by the clients, so we MUST NOT release it before 02:00), but 02:00 lies
    // in the bucket of 04:00. So the key will be released
    // earliest 04:00.
    params.addValue(
        "maxAllowedStartNumber",
        now.roundToBucketStart(releaseBucketDuration).minus(timeSkew).get10MinutesSince1970());
    sql += " and rolling_start_number + rolling_period < :maxAllowedStartNumber";

    // note that received_at is always rounded to `next_bucket` - 1ms to difuse actual upload time
    if (publishedAfter != null) {
      params.addValue("publishedAfter", publishedAfter.getDate());
      sql += " and received_at >= :publishedAfter";
    }

    sql += " order by pk_exposed_id desc";

    List<GaenKeyInternal> keys = jt.query(sql, params, new GaenKeyInternalRowMapper());
    
    return aggregateByCountry(keys);
    
  }

  private List<String> countriesOfInterest(boolean international) {
	List<String> forCountries = new ArrayList<>();
	forCountries.add(originCountry);
	if (international)
		forCountries.addAll(allOtherCountries);
	return forCountries;
  }
  
  private List<GaenKeyInternal> aggregateByCountry(List<GaenKeyInternal> keys) {
	Map<String, List<GaenKeyInternal>> groupedKeys = keys.stream().collect(Collectors.groupingBy(GaenKeyInternal::getKeyData));
    
    final List<GaenKeyInternal> finalKeys = new ArrayList<>(); 
    groupedKeys.keySet().forEach(k -> {
    	finalKeys.add(groupedKeys.get(k).stream().reduce(null, (o, n) -> {
    		if (null == o) {
    			return n;
    		}
    		o.getCountries().addAll(n.getCountries());
    		return o;
    		
    	}));
    });
    return finalKeys;
}

  @Override
  @Transactional(readOnly = true)
  public List<GaenKeyInternal> getSortedExposedSince(UTCInstant keysSince, UTCInstant now, String origin) {
	
	    MapSqlParameterSource params = new MapSqlParameterSource();
	    params.addValue("since", keysSince.getDate());
	    params.addValue("maxBucket", now.roundToBucketStart(releaseBucketDuration).getDate());
	    params.addValue("timeSkewSeconds", timeSkew.toSeconds());
	    params.addValue("origin", origin);

	    // Select keys since the given date. We need to make sure, only keys are returned
	    // that are allowed to be published.
	    // For this, we calculate the expiry for each key in a sub query. The expiry is then used for
	    // the where clause:
	    // - if expiry <= received_at: the key was ready to publish when we received it. Release this
	    // key, if received_at in [since, maxBucket)
	    // - if expiry > received_at: we have to wait until expiry till we can release this key. This
	    // means we only release the key if expiry in [since, maxBucket)
	    // This problem arises, because we only want key with received_at after since, but we need to
	    // ensure that we relase ALL keys meaning keys which were still valid when they were received

	    // we need to add the time skew to calculate the expiry timestamp of a key:
	    // TO_TIMESTAMP((rolling_start_number + rolling_period) * 10 * 60 + :timeSkewSeconds

	    String sql =
	        "select keys.pk_exposed_id, keys.key, keys.rolling_start_number,"
	            + " keys.rolling_period, keys.origin, keys.days_since_onset_of_symptoms, keys.report_type, v.country"
	        	+ " from (select pk_exposed_id, key, rolling_start_number,"
	            + " rolling_period, received_at,  "
	            + getSQLExpressionForExpiry()
	            + " as expiry, days_since_onset_of_symptoms, report_type, origin from t_gaen_exposed)"
	            + " as keys left outer join t_visited v on keys.pk_exposed_id = v.pfk_exposed_id"
	            + " where keys.origin = :origin and ((keys.received_at >= :since AND"
	            + " keys.received_at < :maxBucket AND keys.expiry <= keys.received_at) OR (keys.expiry"
	            + " >= :since AND keys.expiry < :maxBucket AND keys.expiry > keys.received_at))";

	    sql += " order by keys.pk_exposed_id desc";

	    List<GaenKeyInternal> keys = jt.query(sql, params, new GaenKeyInternalRowMapper());
	    return aggregateByCountry(keys);

  }

  @Override
  @Transactional(readOnly = true)
  public List<GaenKeyInternal> getSortedExposedSince(
      UTCInstant keysSince, UTCInstant now, List<String> countries) {
    MapSqlParameterSource params = new MapSqlParameterSource();
    params.addValue("since", keysSince.getDate());
    params.addValue("maxBucket", now.roundToBucketStart(releaseBucketDuration).getDate());
    params.addValue("timeSkewSeconds", timeSkew.toSeconds());
    params.addValue("countries", countries);
    params.addValue("origins", countries);

    // Select keys since the given date. We need to make sure, only keys are returned
    // that are allowed to be published.
    // For this, we calculate the expiry for each key in a sub query. The expiry is then used for
    // the where clause:
    // - if expiry <= received_at: the key was ready to publish when we received it. Release this
    // key, if received_at in [since, maxBucket)
    // - if expiry > received_at: we have to wait until expiry till we can release this key. This
    // means we only release the key if expiry in [since, maxBucket)
    // This problem arises, because we only want key with received_at after since, but we need to
    // ensure that we relase ALL keys meaning keys which were still valid when they were received

    // we need to add the time skew to calculate the expiry timestamp of a key:
    // TO_TIMESTAMP((rolling_start_number + rolling_period) * 10 * 60 + :timeSkewSeconds

    String sql =
        "select keys.pk_exposed_id, keys.key, keys.rolling_start_number,"
            + " keys.rolling_period, keys.origin, keys.days_since_onset_of_symptoms, keys.report_type, v.country"
            + " from (select pk_exposed_id, key, rolling_start_number,"
            + " rolling_period, received_at,  "
            + getSQLExpressionForExpiry()
            + " as expiry, days_since_onset_of_symptoms, report_type, origin from t_gaen_exposed)"
            + " as keys left outer join t_visited v on keys.pk_exposed_id = v.pfk_exposed_id"
            + " where (keys.origin in (:origins) OR v.country in (:countries)) and ((keys.received_at >= :since AND"
            + " keys.received_at < :maxBucket AND keys.expiry <= keys.received_at) OR (keys.expiry"
            + " >= :since AND keys.expiry < :maxBucket AND keys.expiry > keys.received_at))";

    sql += " order by keys.pk_exposed_id desc";

    List<GaenKeyInternal> keys = jt.query(sql, params, new GaenKeyInternalRowMapper());
    return aggregateByCountry(keys);
    
  }
  
  private String getSQLExpressionForExpiry() {
    if (this.dbType.equals(PGSQL)) {
      return "TO_TIMESTAMP((rolling_start_number + rolling_period) * 10 * 60 +"
          + " :timeSkewSeconds)";
    } else {
      return "TIMESTAMP_WITH_ZONE((rolling_start_number + rolling_period) * 10 * 60 +"
          + " :timeSkewSeconds)";
    }
  }

  @Override
  @Transactional(readOnly = false)
  public void cleanDB(Duration retentionPeriod) {
    var retentionTime = UTCInstant.now().minus(retentionPeriod);
    logger.info("Cleanup DB entries before: " + retentionTime);
    MapSqlParameterSource params =
        new MapSqlParameterSource("retention_time", retentionTime.getDate());
    String sqlExposed = "delete from t_gaen_exposed where received_at < :retention_time";
    jt.update(sqlExposed, params);
  }

  private void internalUpsertKey(
      GaenKeyInternal gaenKey, UTCInstant receivedAt) {
    String sqlKey = null;
    String sqlVisited = null;
    if (dbType.equals(PGSQL)) {
      sqlKey =
          "insert into t_gaen_exposed (key, rolling_start_number, rolling_period,"
              + " received_at, origin, days_since_onset_of_symptoms, report_type) values (:key, :rolling_start_number,"
              + " :rolling_period, :received_at, :origin, :days_since_onset_of_symptoms, :report_type) on conflict on"
              + " constraint gaen_exposed_key do nothing";
      sqlVisited =
          "insert into t_visited (pfk_exposed_id, country) values (:keyId, :country) on conflict on"
              + " constraint PK_t_visited do nothing";
    } else {
      sqlKey =
          "merge into t_gaen_exposed using (values(cast(:key as varchar(24)),"
              + " :rolling_start_number, :rolling_period, :received_at, cast(:origin as"
              + " varchar(10)), :days_since_onset_of_symptoms, :report_type)) as "
              + " vals(key, rolling_start_number, rolling_period, received_at,"
              + " origin, days_since_onset_of_symptoms, report_type) on t_gaen_exposed.key = vals.key"
              + " when not matched then insert (key, rolling_start_number, rolling_period, received_at, "
              + " origin, days_since_onset_of_symptoms, report_type) values (vals.key,"
              + " vals.rolling_start_number, vals.rolling_period, vals.received_at, vals.origin," 
              + " vals.days_since_onset_of_symptoms, vals.report_type)";
      sqlVisited =
          "merge into t_visited using (values(:keyId, :country)) as vals(keyId, country) on"
              + " t_visited.pfk_exposed_id = vals.keyId and t_visited.country = vals.country when"
              + " not matched then insert (pfk_exposed_id, country) values (vals.keyId,"
              + " vals.country)";
    }

    List<MapSqlParameterSource> visitedBatch = new ArrayList<>();

    MapSqlParameterSource params = new MapSqlParameterSource();
    params.addValue("key", gaenKey.getKeyData());
    params.addValue("rolling_start_number", gaenKey.getRollingStartNumber());
    params.addValue("rolling_period", gaenKey.getRollingPeriod());
    params.addValue("received_at", receivedAt.getDate());
    params.addValue("origin", gaenKey.getOrigin());
    params.addValue("days_since_onset_of_symptoms", gaenKey.getDaysSinceOnsetOfSymptoms());
    params.addValue("report_type", gaenKey.getReportType());
    KeyHolder keyHolder = new GeneratedKeyHolder();
    jt.update(sqlKey, params, keyHolder);

    // if the key already exists, no ids are returned. in this case we assume that we do not need
    // to modify the visited countries also
    if (keyHolder.getKeys() != null && !keyHolder.getKeys().isEmpty()) {
      Object keyObject = keyHolder.getKeys().get("pk_exposed_id");
      if (keyObject != null) {
        int gaenKeyId = ((Integer) keyObject).intValue();
        for (String country : gaenKey.getCountries()) {
          MapSqlParameterSource visitedParams = new MapSqlParameterSource();
          visitedParams.addValue("keyId", gaenKeyId);
          visitedParams.addValue("country", country);
          visitedBatch.add(visitedParams);
        }
      }
    }
    if (!visitedBatch.isEmpty()) {
      jt.batchUpdate(
          sqlVisited, visitedBatch.toArray(new MapSqlParameterSource[visitedBatch.size()]));
    }
  }

  @Override
  public List<GaenKeyInternal> getSortedExposedSince(UTCInstant keysSince, UTCInstant now, boolean international) {
	return this.getSortedExposedSince(keysSince, now, countriesOfInterest(international));
  }

}

