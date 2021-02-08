package org.dpppt.backend.sdk.data.gaen;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.dpppt.backend.sdk.model.gaen.GaenKey;
import org.dpppt.backend.sdk.model.gaen.GaenKeyInternal;
import org.springframework.jdbc.core.RowMapper;

public class GaenKeyInternalRowMapper implements RowMapper<GaenKeyInternal> {

  @Override
  public GaenKeyInternal mapRow(ResultSet rs, int rowNum) throws SQLException {
    var gaenKey = new GaenKeyInternal();
    gaenKey.setPk(rs.getLong("pk_exposed_id"));
    gaenKey.setKeyData(rs.getString("key"));
    gaenKey.setRollingStartNumber(rs.getInt("rolling_start_number"));
    gaenKey.setRollingPeriod(rs.getInt("rolling_period"));
    
    List<String> countries = new ArrayList<>();
    if (null != rs.getObject("country")) {
        countries.add(rs.getString("country"));    	
    }    
    gaenKey.setCountries(countries);
    
    gaenKey.setFake(0);
    gaenKey.setTransmissionRiskLevel(0);
    gaenKey.setDaysSinceOnsetOfSymptoms(rs.getInt("days_since_onset_of_symptoms"));
    gaenKey.setOrigin(rs.getString("origin"));
    gaenKey.setReportType(rs.getString("report_type"));
    
    gaenKey.setReceivedAt(rs.getTimestamp("received_at").toInstant());
    gaenKey.setExpiresAt(rs.getTimestamp("expires_at").toInstant());
    gaenKey.setEfgsBatchTag(rs.getString("efgs_batch_tag"));
    gaenKey.setEfgsUploadTag(rs.getString("efgs_upload_tag"));
   	
    return gaenKey;
  }
}
