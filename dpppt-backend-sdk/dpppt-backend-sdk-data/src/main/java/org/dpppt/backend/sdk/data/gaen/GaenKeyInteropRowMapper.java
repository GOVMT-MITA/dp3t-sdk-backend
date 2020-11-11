package org.dpppt.backend.sdk.data.gaen;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.dpppt.backend.sdk.model.gaen.GaenKey;
import org.dpppt.backend.sdk.model.gaen.GaenKeyInterop;
import org.springframework.jdbc.core.RowMapper;

public class GaenKeyInteropRowMapper implements RowMapper<GaenKeyInterop> {

  @Override
  public GaenKeyInterop mapRow(ResultSet rs, int rowNum) throws SQLException {
    var gaenKey = new GaenKeyInterop();
    gaenKey.setKeyData(rs.getString("key"));
    gaenKey.setRollingStartNumber(rs.getInt("rolling_start_number"));
    gaenKey.setRollingPeriod(rs.getInt("rolling_period"));
    gaenKey.setVisitedCountries(List.of(rs.getString("country")));
    return gaenKey;
  }
}
