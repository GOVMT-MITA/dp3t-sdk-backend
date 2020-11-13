package org.dpppt.backend.sdk.data.gaen;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.dpppt.backend.sdk.model.gaen.GaenKey;
import org.dpppt.backend.sdk.model.gaen.GaenKeyInternal;
import org.springframework.jdbc.core.RowMapper;

public class GaenKeyInternalRowMapper implements RowMapper<GaenKeyInternal> {

  @Override
  public GaenKeyInternal mapRow(ResultSet rs, int rowNum) throws SQLException {
    var gaenKey = new GaenKeyInternal();
    gaenKey.setKeyData(rs.getString("key"));
    gaenKey.setRollingStartNumber(rs.getInt("rolling_start_number"));
    gaenKey.setRollingPeriod(rs.getInt("rolling_period"));    
    gaenKey.setCountries(List.of(rs.getString("country")));
    gaenKey.setFake(0);
    gaenKey.setTransmissionRiskLevel(0);
    gaenKey.setDaysSinceOnsetOfSymptoms(rs.getInt("days_since_onset_of_symptoms"));
    gaenKey.setOrigin(rs.getString("origin"));
    gaenKey.setReportType(rs.getString("report_type"));
    
    return gaenKey;
  }
}
