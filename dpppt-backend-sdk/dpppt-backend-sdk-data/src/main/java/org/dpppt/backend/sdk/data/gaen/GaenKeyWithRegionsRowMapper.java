package org.dpppt.backend.sdk.data.gaen;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.dpppt.backend.sdk.model.gaen.GaenKey;
import org.dpppt.backend.sdk.model.gaen.GaenKeyWithRegions;
import org.springframework.jdbc.core.RowMapper;

public class GaenKeyWithRegionsRowMapper implements RowMapper<GaenKeyWithRegions> {

  @Override
  public GaenKeyWithRegions mapRow(ResultSet rs, int rowNum) throws SQLException {
    var gaenKey = new GaenKeyWithRegions();
    gaenKey.setKeyData(rs.getString("key"));
    gaenKey.setRollingStartNumber(rs.getInt("rolling_start_number"));
    gaenKey.setRollingPeriod(rs.getInt("rolling_period"));
    gaenKey.setRegions(List.of(rs.getString("country")));
    return gaenKey;
  }
}
