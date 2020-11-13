package org.dpppt.backend.sdk.ws.insertmanager.insertionfilters;

import java.util.List;
import java.util.stream.Collectors;
import org.dpppt.backend.sdk.model.gaen.GaenKey;
import org.dpppt.backend.sdk.model.gaen.GaenKeyInternal;
import org.dpppt.backend.sdk.semver.Version;
import org.dpppt.backend.sdk.utils.UTCInstant;
import org.dpppt.backend.sdk.ws.insertmanager.OSType;
import org.dpppt.backend.sdk.ws.security.ValidateRequest;
import org.dpppt.backend.sdk.ws.security.ValidateRequest.ClaimIsBeforeOnsetException;
import org.dpppt.backend.sdk.ws.security.ValidateRequest.InvalidDateException;

/**
 * This filter compares the supplied keys from the exposed request with information found in the JWT
 * token: the key dates must be >= the onset date, which was set by the health authority and is
 * available as a claim in the JWT
 */
public class EnforceMatchingJWTClaimsForExposed implements KeyInsertionFilter {

  private final ValidateRequest validateRequest;

  public EnforceMatchingJWTClaimsForExposed(ValidateRequest validateRequest) {
    this.validateRequest = validateRequest;
  }

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
        .filter(key -> isValidKeyDate(key, principal, now))
        .collect(Collectors.toList());
  }

  private boolean isValidKeyDate(GaenKeyInternal key, Object principal, UTCInstant now) {
    try {
      validateRequest.validateKeyDate(now, principal, key);
      return true;
    } catch (InvalidDateException | ClaimIsBeforeOnsetException es) {
      return false;
    }
  }
}
