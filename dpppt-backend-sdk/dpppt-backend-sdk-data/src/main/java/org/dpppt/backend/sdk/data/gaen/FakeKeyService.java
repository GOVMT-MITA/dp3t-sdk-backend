package org.dpppt.backend.sdk.data.gaen;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.dpppt.backend.sdk.model.gaen.GaenKey;
import org.dpppt.backend.sdk.model.gaen.GaenKeyInternal;
import org.dpppt.backend.sdk.utils.UTCInstant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FakeKeyService {

  private final GAENDataService dataService;
  private final Integer minNumOfKeys;
  private final SecureRandom random;
  private final Integer keySize;
  private final Duration retentionPeriod;
  private final boolean isEnabled;
  private final String originCountry;

  private static final Logger logger = LoggerFactory.getLogger(FakeKeyService.class);

  public FakeKeyService(
      GAENDataService dataService,
      Integer minNumOfKeys,
      Integer keySize,
      Duration retentionPeriod,
      boolean isEnabled,
      String originCountry)
      throws NoSuchAlgorithmException {
    this.dataService = dataService;
    this.minNumOfKeys = minNumOfKeys;
    this.random = new SecureRandom();
    this.keySize = keySize;
    this.retentionPeriod = retentionPeriod;
    this.isEnabled = isEnabled;
    this.originCountry = originCountry;
    this.updateFakeKeys();
  }

  public void updateFakeKeys() {
    deleteAllKeys();
    var currentKeyDate = UTCInstant.today();
    var tmpDate = currentKeyDate.minusDays(retentionPeriod.toDays()).atStartOfDay();
    logger.debug("Fill Fake keys. Start: " + currentKeyDate + " End: " + tmpDate);
    do {
      var keys = new ArrayList<GaenKeyInternal>();
      for (int i = 0; i < minNumOfKeys; i++) {
        byte[] keyData = new byte[keySize];
        random.nextBytes(keyData);
        var keyGAENTime = (int) tmpDate.get10MinutesSince1970();
        var key = new GaenKeyInternal();
        key.setKeyData(Base64.getEncoder().encodeToString(keyData));
        key.setRollingStartNumber(keyGAENTime);
        key.setRollingPeriod(144);
        key.setOrigin(originCountry);
        key.setReportType("CONFIRMED_TEST");
        key.setDaysSinceOnsetOfSymptoms(14);
        keys.add(key);
      }
      // TODO: Check if currentKeyDate is indeed intended here
      this.dataService.upsertExposees(keys, currentKeyDate);
      tmpDate = tmpDate.plusDays(1);
    } while (tmpDate.isBeforeDateOf(currentKeyDate));
  }

  private void deleteAllKeys() {
    logger.debug("Delete all fake keys");
    this.dataService.cleanDB(Duration.ofDays(0));
  }

  public List<GaenKeyInternal> fillUpKeys(
      List<GaenKeyInternal> keys, UTCInstant publishedafter, UTCInstant keyDate, UTCInstant now) {
    if (!isEnabled) {
      return keys;
    }
    var today = now.atStartOfDay();
    var keyLocalDate = keyDate.atStartOfDay();
    if (today.hasSameDateAs(keyLocalDate)) {
      return keys;
    }
    var fakeKeys =
        this.dataService.getSortedExposedForKeyDate(
            keyDate, publishedafter, UTCInstant.today().plusDays(1), now, false);

    keys.addAll(fakeKeys);
    return keys;
  }

  public List<GaenKeyInternal> fillUpKeys(
	      List<GaenKeyInternal> keys, UTCInstant keysSince, UTCInstant now) {
	    if (!isEnabled) {
	      return keys;
	    }
	    var fakeKeys =
	        this.dataService.getSortedExposedSince(keysSince, now, originCountry);

	    keys.addAll(fakeKeys);
	    return keys;
	  }

}
