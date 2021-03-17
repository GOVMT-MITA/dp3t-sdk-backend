package org.dpppt.backend.sdk.ws.insertmanager;

import java.time.Duration;
import java.util.List;

import org.assertj.core.util.Lists;
import org.dpppt.backend.sdk.data.gaen.GAENDataService;
import org.dpppt.backend.sdk.model.gaen.GaenKey;
import org.dpppt.backend.sdk.model.gaen.GaenKeyInternal;
import org.dpppt.backend.sdk.utils.UTCInstant;

public class MockDataSource implements GAENDataService {

  @Override
  public void upsertExposees(List<GaenKeyInternal> keys, UTCInstant now) {
    throw new RuntimeException("UPSERT_EXPOSEES");
  }

  @Override
  public void upsertExposeesDelayed(
      List<GaenKeyInternal> keys, UTCInstant delayedReceivedAt, UTCInstant now) {
    throw new RuntimeException("UPSERT_EXPOSEESDelayed");
  }

  @Override
  public List<GaenKeyInternal> getSortedExposedForKeyDate(
      UTCInstant keyDate, UTCInstant publishedAfter, UTCInstant publishedUntil, UTCInstant now, boolean international) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public void cleanDB(Duration retentionPeriod) {}

  @Override
  public List<GaenKeyInternal> getSortedExposedSince(
      UTCInstant keysSince,
      UTCInstant now,
      List<String> countries) { // TODO Auto-generated method stub
    return null;
  }

  @Override
  public void upsertExposee(
	  GaenKeyInternal key,
      UTCInstant now) { // TODO Auto-generated method stub
  }


  @Override
  public List<GaenKeyInternal> getSortedExposedSince(UTCInstant keysSince, UTCInstant now, String origin) {
	return this.getSortedExposedSince(keysSince, now, Lists.emptyList());
  }

  @Override
  public List<GaenKeyInternal> getSortedExposedSince(UTCInstant keysSince, UTCInstant now, boolean international) {
	return this.getSortedExposedSince(keysSince, now, Lists.emptyList());
  }

@Override
public List<GaenKeyInternal> getSortedExposedSinceForOrigins(UTCInstant keysSince, UTCInstant now,
		List<String> origins) {
	return this.getSortedExposedSince(keysSince, now, Lists.emptyList());
}

@Override
public void markUploaded(List<GaenKeyInternal> gaenKeys, String batchTag) {
	// TODO Auto-generated method stub
	
}

@Override
public long efgsBatchExists(String batchTag) {
	// TODO Auto-generated method stub
	return 0;
}

@Override
public List<GaenKeyInternal> getSortedExposedForKeyDateForOrigins(UTCInstant keyDate, UTCInstant publishedAfter,
		UTCInstant publishedUntil, UTCInstant now, boolean international) {
	// TODO Auto-generated method stub
	return null;
}

}
