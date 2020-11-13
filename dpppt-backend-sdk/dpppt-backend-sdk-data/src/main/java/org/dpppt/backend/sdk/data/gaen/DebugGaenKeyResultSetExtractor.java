package org.dpppt.backend.sdk.data.gaen;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.dpppt.backend.sdk.model.gaen.GaenKey;
import org.dpppt.backend.sdk.model.gaen.GaenKeyInternal;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ResultSetExtractor;

public class DebugGaenKeyResultSetExtractor
    implements ResultSetExtractor<Map<String, List<GaenKeyInternal>>> {

  @Override
  public Map<String, List<GaenKeyInternal>> extractData(ResultSet rs)
      throws SQLException, DataAccessException {
    Map<String, List<GaenKeyInternal>> result = new HashMap<String, List<GaenKeyInternal>>();
    GaenKeyInternalRowMapper gaenKeyRowMapper = new GaenKeyInternalRowMapper();
    while (rs.next()) {
      String deviceName = rs.getString("device_name");
      List<GaenKeyInternal> keysForDevice = result.get(deviceName);
      if (keysForDevice == null) {
        keysForDevice = new ArrayList<>();
        result.put(deviceName, keysForDevice);
      }
      GaenKeyInternal gaenKey = gaenKeyRowMapper.mapRow(rs, rs.getRow());
      keysForDevice.add(gaenKey);
    }
    return result;
  }
}
