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
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.sql.DataSource;
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
      String dbType, DataSource dataSource, Duration releaseBucketDuration, Duration timeSkew, String originCountry,
      List<String> allOtherCountries) {
    this.dbType = dbType;
    this.jt = new NamedParameterJdbcTemplate(dataSource);
    this.releaseBucketDuration = releaseBucketDuration;
    this.timeSkew = timeSkew;
    this.originCountry = originCountry;
    this.allOtherCountries = allOtherCountries;
  }

  @Override
  @Transactional(readOnly = false)
  public void upsertExposee(
      GaenKeyInternal gaenKey, UTCInstant now) {
	var receivedAt = now.roundToNextBucket(releaseBucketDuration).minus(Duration.ofMillis(1));
    internalUpsertKey(gaenKey, receivedAt);
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
    return getKeys(publishedAfter, now, keyDate, publishedUntil, countriesOfInterest(international), null);
  }

  @Override
  @Transactional(readOnly = true)
  public List<GaenKeyInternal> getSortedExposedForKeyDateForOrigins(
    UTCInstant keyDate, UTCInstant publishedAfter, UTCInstant publishedUntil, UTCInstant now, boolean international) {
    return getKeys(publishedAfter, now, keyDate, publishedUntil, null, countriesOfInterest(international));
  }
  
  @Override
  @Transactional(readOnly = true)
  public List<GaenKeyInternal> getSortedExposedSince(UTCInstant keysSince, UTCInstant now, List<String> countries) {
    return getKeys(keysSince, now, null, now.roundToBucketStart(releaseBucketDuration), countries, null);
  }

  @Override
  @Transactional(readOnly = true)
  public List<GaenKeyInternal> getSortedExposedSinceForOrigins(UTCInstant keysSince, UTCInstant now, List<String> origins) {
    return getKeys(keysSince, now, null, now.roundToBucketStart(releaseBucketDuration), null, origins);
  }
  
  private List<GaenKeyInternal> getKeys(
      UTCInstant keysSince, UTCInstant now, UTCInstant keyDate, UTCInstant maxBucket, List<String> countries, List<String> origins) {

    if (null != countries && null != origins) {
	  throw new IllegalArgumentException("Either countries or origin have to be null");
    }
    
	MapSqlParameterSource params = new MapSqlParameterSource();
    params.addValue("since", keysSince.getDate());
    params.addValue("maxBucket", maxBucket.getDate());
    params.addValue("timeSkewSeconds", timeSkew.toSeconds());
    params.addValue("countries", countries);
    if (origins == null) {
    	params.addValue("origins", countries);
    } else { 
    	params.addValue("origins", origins);
    }

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

    var sql =
        "select keys.pk_exposed_id, keys.key, keys.rolling_start_number, keys.rolling_period,"
            + " keys.origin, keys.days_since_onset_of_symptoms, keys.report_type, keys.transmission_risk_level, keys.efgs_batch_tag, keys.efgs_upload_tag, visited.country, keys.received_at, "
        	+ " keys.expires_at from t_gaen_exposed as keys "
            + " left join t_visited as visited on keys.pk_exposed_id = visited.pfk_exposed_id"
            + " where ( (keys.expires_at <= keys.received_at AND keys.received_at >= :since AND keys.received_at < :maxBucket)"
            + " OR (keys.expires_at > keys.received_at AND keys.expires_at >= :since AND keys.expires_at < :maxBucket) )"
            + getSQLExpressionForKeyDateFilter(keyDate, params)
            + getSQLExpressionForCountriesFilter(countries, origins);

    sql += " order by keys.pk_exposed_id desc";
    sql += " limit 100000";

    
    List<GaenKeyInternal> keys = jt.query(sql, params, new GaenKeyInternalRowMapper());
    
    return aggregateByCountry(keys);
    
  }

  private String getSQLExpressionForCountriesFilter(List<String> countries, List<String> origins) {
	  if (null != countries) {
		  return " and (keys.origin in (:origins) OR visited.country in (:countries))";
	  }
	  if (null != origins) {
		  return " and keys.origin in (:origins)";
	  }
	  return "";
	  
  }

  private String getSQLExpressionForKeyDateFilter(
      UTCInstant keyDate, MapSqlParameterSource params) {
    if (keyDate != null) {
      params.addValue("rollingPeriodStartNumberStart", keyDate.get10MinutesSince1970());
      params.addValue("rollingPeriodStartNumberEnd", keyDate.plusDays(1).get10MinutesSince1970());
      return " and rolling_start_number >= :rollingPeriodStartNumberStart and"
          + " rolling_start_number < :rollingPeriodStartNumberEnd";
    } else {
      return "";
    }
  }

  private String getSQLExpressionForKeyDateFilter2(
	      UTCInstant keyDate, MapSqlParameterSource params) {
	    if (keyDate != null) {
	      params.addValue("rollingPeriodStartNumberStart", keyDate.get10MinutesSince1970());
	      params.addValue("rollingPeriodStartNumberEnd", keyDate.plusDays(1).get10MinutesSince1970());
	      return " where rolling_start_number >= :rollingPeriodStartNumberStart and"
	          + " rolling_start_number < :rollingPeriodStartNumberEnd";
	    } else {
	      return "";
	    }
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
    
    final List<GaenKeyInternal> sortedKeys = finalKeys.stream()
    		.sorted(Comparator.comparingLong(GaenKeyInternal::getPk).reversed()).collect(Collectors.toList()); 
    
    return sortedKeys;
    
}
  
  private List<String> countriesOfInterest(boolean international) {
	List<String> forCountries = new ArrayList<>();
	forCountries.add(originCountry);
	if (international)
		forCountries.addAll(allOtherCountries);
	return forCountries;
  }

  @Override
  public List<GaenKeyInternal> getSortedExposedSince(UTCInstant keysSince, UTCInstant now, boolean international) {
	return getKeys(keysSince, now, null, now.roundToBucketStart(releaseBucketDuration), countriesOfInterest(international), null);
  }

  @Override
  public List<GaenKeyInternal> getSortedExposedSince(UTCInstant keysSince, UTCInstant now, String origin) {
	  return getKeys(keysSince, now, null, now.roundToBucketStart(releaseBucketDuration), null, List.of(origin));
  }
  
  private void internalUpsertKey(
	      GaenKeyInternal gaenKey, UTCInstant receivedAt) {
	    String sqlKey = null;
	    String sqlVisited = null;
	    if (dbType.equals(PGSQL)) {
	      sqlKey =
	          "insert into t_gaen_exposed (key, rolling_start_number, rolling_period,"
	              + " received_at, expires_at, origin, days_since_onset_of_symptoms, report_type, transmission_risk_level, efgs_batch_tag) values (:key, :rolling_start_number,"
	              + " :rolling_period, :received_at, :expires_at, :origin, :days_since_onset_of_symptoms, :report_type, :transmission_risk_level, :efgs_batch_tag) on conflict on"
	              + " constraint gaen_exposed_key do nothing";
	      sqlVisited =
	          "insert into t_visited (pfk_exposed_id, country) values (:keyId, :country) on conflict on"
	              + " constraint PK_t_visited do nothing";
	    } else {
	      sqlKey =
	          "merge into t_gaen_exposed using (values(cast(:key as varchar(24)),"
	              + " :rolling_start_number, :rolling_period, :received_at, :expires_at, cast(:origin as"
	              + " varchar(10)), :days_since_onset_of_symptoms, :report_type, :transmission_risk_level, :efgs_batch_tag)) as "
	              + " vals(key, rolling_start_number, rolling_period, received_at, expires_at, "
	              + " origin, days_since_onset_of_symptoms, report_type, transmission_risk_level, efgs_batch_tag) on t_gaen_exposed.key = vals.key"
	              + " when not matched then insert (key, rolling_start_number, rolling_period, received_at, expires_at,"
	              + " origin, days_since_onset_of_symptoms, report_type, transmission_risk_level, efgs_batch_tag) values (vals.key,"
	              + " vals.rolling_start_number, vals.rolling_period, vals.received_at, vals.expires_at, vals.origin," 
	              + " vals.days_since_onset_of_symptoms, vals.report_type, vals.transmission_risk_level, vals.efgs_batch_tag)";
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
	    params.addValue("expires_at", UTCInstant.ofEpochMillis(((gaenKey.getRollingStartNumber() + gaenKey.getRollingPeriod()) * 10 * 60 + timeSkew.getSeconds()) * 1000).getDate());
	    params.addValue("origin", gaenKey.getOrigin());
	    params.addValue("days_since_onset_of_symptoms", gaenKey.getDaysSinceOnsetOfSymptoms());
	    params.addValue("report_type", gaenKey.getReportType());
	    params.addValue("transmission_risk_level", gaenKey.getTransmissionRiskLevel());
	    params.addValue("efgs_batch_tag", gaenKey.getEfgsBatchTag());
	    
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


		public void markUploaded(List<GaenKeyInternal> gaenKeys, String batchTag) {

			String sql = "update t_gaen_exposed set efgs_upload_tag = :efgs_upload_tag"
					+ " where pk_exposed_id = :pk_exposed_id";

			List<MapSqlParameterSource> uploadBatch = new ArrayList<>();

			for (GaenKeyInternal gaenKey : gaenKeys) {
				MapSqlParameterSource params = new MapSqlParameterSource();
				params.addValue("pk_exposed_id", gaenKey.getPk());
				params.addValue("efgs_upload_tag", batchTag);
				uploadBatch.add(params);
			}

			if (!uploadBatch.isEmpty()) {
				jt.batchUpdate(sql, uploadBatch.toArray(new MapSqlParameterSource[uploadBatch.size()]));
			}
		}
		
		public long efgsBatchExists(String batchTag) {
			String sql = "select count(*) from t_gaen_exposed where efgs_batch_tag = :efgs_batch_tag";
			
			MapSqlParameterSource params = new MapSqlParameterSource();
			params.addValue("efgs_batch_tag", batchTag);
			
			return jt.queryForObject(sql, params, Long.class);
		}
  
}
