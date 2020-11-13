package org.dpppt.backend.sdk.ws.insertmanager.insertionfilters;

import java.util.List;
import java.util.stream.Collectors;
import org.dpppt.backend.sdk.model.gaen.GaenKey;
import org.dpppt.backend.sdk.model.gaen.GaenKeyInternal;
import org.dpppt.backend.sdk.semver.Version;
import org.dpppt.backend.sdk.utils.UTCInstant;
import org.dpppt.backend.sdk.ws.insertmanager.OSType;

/**
 * This filter checks for valid rolling period. The rolling period must always be in [1..144],
 * otherwise the key is not valid and is filtered out. See <a href=
 * "https://github.com/google/exposure-notifications-server/blob/main/docs/server_functional_requirements.md#publishing-temporary-exposure-keys"
 * >EN documentation</a>
 */
public class EnforceValidRollingPeriod implements KeyInsertionFilter {

  @Override
  public List<GaenKeyInternal> filter(
      UTCInstant now,
      List<GaenKeyInternal> content,
      List<String> countries,
      OSType osType,
      Version osVersion,
      Version appVersion,
      Object principal) {
    return content.stream()
        .filter(key -> key.getRollingPeriod() >= 1 && key.getRollingPeriod() <= 144)
        .collect(Collectors.toList());
  }
}
