package org.dpppt.backend.sdk.ws.insertmanager;

import java.time.Duration;
import java.util.List;
import org.dpppt.backend.sdk.data.gaen.GAENDataService;
import org.dpppt.backend.sdk.model.gaen.GaenKey;
import org.dpppt.backend.sdk.model.gaen.GaenKeyInterop;
import org.dpppt.backend.sdk.utils.UTCInstant;

public class MockDataSource implements GAENDataService {

  @Override
  public void upsertExposees(List<GaenKey> keys, UTCInstant now, List<String> countries) {
    throw new RuntimeException("UPSERT_EXPOSEES");
  }

  @Override
  public void upsertExposeesDelayed(
      List<GaenKey> keys, UTCInstant delayedReceivedAt, UTCInstant now, List<String> countries) {
    throw new RuntimeException("UPSERT_EXPOSEESDelayed");
  }

  @Override
  public List<GaenKey> getSortedExposedForKeyDate(
      UTCInstant keyDate, UTCInstant publishedAfter, UTCInstant publishedUntil, UTCInstant now) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public void cleanDB(Duration retentionPeriod) {}

  @Override
  public List<GaenKey> getSortedExposedSince(
      UTCInstant keysSince,
      UTCInstant now,
      List<String> countries) { // TODO Auto-generated method stub
    return null;
  }

  @Override
  public void upsertExposeeFromInterops(
      GaenKey key,
      UTCInstant now,
      String origin,
      List<String> visitedCountries) { // TODO Auto-generated method stub
  }

@Override
public void upsertExposees(List<GaenKey> keys, UTCInstant now) {
	this.upsertExposees(keys, now, null);
}

@Override
public List<GaenKey> getSortedExposedSince(UTCInstant keysSince, UTCInstant now) {
	return this.getSortedExposedSince(keysSince, now, null);
}

@Override
public List<GaenKeyInterop> getSortedExposedSinceForInterop(UTCInstant keysSince, UTCInstant now) {
	return null;
}
}
