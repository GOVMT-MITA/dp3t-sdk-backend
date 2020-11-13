package org.dpppt.backend.sdk.ws.insertmanager.insertionfilters;

import java.util.List;
import java.util.stream.Collectors;
import org.dpppt.backend.sdk.model.gaen.GaenKey;
import org.dpppt.backend.sdk.model.gaen.GaenKeyInternal;
import org.dpppt.backend.sdk.semver.Version;
import org.dpppt.backend.sdk.utils.UTCInstant;
import org.dpppt.backend.sdk.ws.insertmanager.OSType;

/** Keep only Non-Fake keys, so that fake keys are not stored in the database. */
public class RemoveFakeKeys implements KeyInsertionFilter {

  @Override
  public List<GaenKeyInternal> filter(
      UTCInstant now,
      List<GaenKeyInternal> content,
      List<String> countries,
      OSType osType,
      Version osVersion,
      Version appVersion,
      Object principal) {
    return content.stream().filter(key -> key.getFake().equals(0)).collect(Collectors.toList());
  }
}
