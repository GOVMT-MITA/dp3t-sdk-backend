package org.dpppt.backend.sdk.data.gaen;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import org.dpppt.backend.sdk.data.config.FlyWayConfig;
import org.dpppt.backend.sdk.data.config.GaenDataServiceConfig;
import org.dpppt.backend.sdk.data.config.RedeemDataServiceConfig;
import org.dpppt.backend.sdk.data.config.StandaloneDataConfig;
import org.dpppt.backend.sdk.model.gaen.GaenKey;
import org.dpppt.backend.sdk.model.gaen.GaenKeyInternal;
import org.dpppt.backend.sdk.model.gaen.GaenUnit;
import org.dpppt.backend.sdk.utils.UTCInstant;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.support.AnnotationConfigContextLoader;
import org.springframework.transaction.annotation.Transactional;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(
    loader = AnnotationConfigContextLoader.class,
    classes = {
      StandaloneDataConfig.class,
      FlyWayConfig.class,
      GaenDataServiceConfig.class,
      RedeemDataServiceConfig.class
    })
@ActiveProfiles("hsqldb")
public class GaenDataServiceTest {

  private static final Duration BUCKET_LENGTH = Duration.ofHours(2);

  @Autowired private GAENDataService gaenDataService;

  @Test
  @Transactional
  public void upsert() throws Exception {
    var tmpKey = new GaenKeyInternal();
    tmpKey.setRollingStartNumber(
        (int) UTCInstant.today().minus(Duration.ofDays(1)).get10MinutesSince1970());
    tmpKey.setKeyData(Base64.getEncoder().encodeToString("testKey32Bytes01".getBytes("UTF-8")));
    tmpKey.setRollingPeriod(144);
    tmpKey.setFake(0);
    tmpKey.setTransmissionRiskLevel(0);
    tmpKey.setOrigin("CH");
    var tmpKey2 = new GaenKeyInternal();
    tmpKey2.setRollingStartNumber(
        (int) UTCInstant.today().minus(Duration.ofDays(1)).get10MinutesSince1970());
    tmpKey2.setKeyData(Base64.getEncoder().encodeToString("testKey32Bytes02".getBytes("UTF-8")));
    tmpKey2.setRollingPeriod(144);
    tmpKey2.setFake(0);
    tmpKey2.setTransmissionRiskLevel(0);
    tmpKey2.setOrigin("CH");
    List<GaenKeyInternal> keys = List.of(tmpKey, tmpKey2);
    var now = UTCInstant.now();
    gaenDataService.upsertExposees(keys, now);

    // calculate exposed until bucket, but get bucket in the future, as keys have
    // been inserted with timestamp now.
    UTCInstant publishedUntil = now.roundToNextBucket(BUCKET_LENGTH);

    var returnedKeys =
        gaenDataService.getSortedExposedForKeyDate(
            UTCInstant.today().minusDays(1), UTCInstant.midnight1970(), publishedUntil, now, false);

    assertEquals(keys.size(), returnedKeys.size());
    assertEquals(keys.get(1).getKeyData(), returnedKeys.get(0).getKeyData());
  }

  @Test
  @Transactional
  public void upsertAndGetSince() throws Exception {
    var tmpKey = new GaenKeyInternal();
    tmpKey.setRollingStartNumber(
        (int) UTCInstant.today().minus(Duration.ofDays(1)).get10MinutesSince1970());
    tmpKey.setKeyData(Base64.getEncoder().encodeToString("testKey32Bytes01".getBytes("UTF-8")));
    tmpKey.setRollingPeriod(144);
    tmpKey.setFake(0);
    tmpKey.setTransmissionRiskLevel(0);
    tmpKey.setOrigin("CH");
    var tmpKey2 = new GaenKeyInternal();
    tmpKey2.setRollingStartNumber(
        (int) UTCInstant.today().minus(Duration.ofDays(1)).get10MinutesSince1970());
    tmpKey2.setKeyData(Base64.getEncoder().encodeToString("testKey32Bytes02".getBytes("UTF-8")));
    tmpKey2.setRollingPeriod(144);
    tmpKey2.setFake(0);
    tmpKey2.setTransmissionRiskLevel(0);
    tmpKey2.setOrigin("CH");
    tmpKey2.setCountries(List.of("IE","DE"));
    List<GaenKeyInternal> keys = List.of(tmpKey, tmpKey2);
    var now = UTCInstant.now();
    gaenDataService.upsertExposees(keys, now);

    var returnedKeys =
        gaenDataService.getSortedExposedSince(now.minusDays(10), now.plusDays(1), false);

    assertEquals(keys.size(), returnedKeys.size());
    assertEquals(keys.get(1).getKeyData(), returnedKeys.get(0).getKeyData());
  }

  @Test
  @Transactional
  public void testNoEarlyReleaseSince() throws Exception {
    var outerNow = UTCInstant.now();
    Clock twoOClock =
        Clock.fixed(outerNow.atStartOfDay().plusHours(2).getInstant(), ZoneOffset.UTC);
    Clock elevenOClock =
        Clock.fixed(outerNow.atStartOfDay().plusHours(11).getInstant(), ZoneOffset.UTC);
    Clock fourteenOClock =
        Clock.fixed(outerNow.atStartOfDay().plusHours(14).getInstant(), ZoneOffset.UTC);

    try (var now = UTCInstant.setClock(twoOClock)) {
      var tmpKey = new GaenKeyInternal();
      tmpKey.setRollingStartNumber((int) now.atStartOfDay().get10MinutesSince1970());
      tmpKey.setKeyData(Base64.getEncoder().encodeToString("testKey32Bytes--".getBytes("UTF-8")));
      tmpKey.setRollingPeriod(
          (int) Duration.ofHours(10).dividedBy(GaenUnit.TenMinutes.getDuration()));
      tmpKey.setFake(0);
      tmpKey.setTransmissionRiskLevel(0);
      tmpKey.setOrigin("CH");

      gaenDataService.upsertExposees(List.of(tmpKey), now);
    }
    // key was inserted with a rolling period of 10 hours, which means the key is not allowed to be
    // released before 12, but since 12 already is in the 14 O'Clock bucket, it is not released
    // before 14:00

    // eleven O'clock no key
    try (var now = UTCInstant.setClock(elevenOClock)) {
      var returnedKeys = gaenDataService.getSortedExposedSince(now.minusDays(10), now, false);
      assertEquals(0, returnedKeys.size());
    }

    // 14 O'clock release the key
    try (var now = UTCInstant.setClock(fourteenOClock)) {
      var returnedKeys = gaenDataService.getSortedExposedSince(now.minusDays(10), now, false);
      assertEquals(1, returnedKeys.size());
    }
  }

  @Test
  @Transactional
  public void testNoEarlyRelease() throws Exception {
    var outerNow = UTCInstant.now();
    Clock twoOClock =
        Clock.fixed(outerNow.atStartOfDay().plusHours(2).getInstant(), ZoneOffset.UTC);
    Clock twelve01Clock =
        Clock.fixed(
            outerNow.atStartOfDay().plusHours(12).plusMinutes(1).getInstant(), ZoneOffset.UTC);
    Clock fourteenOClock =
        Clock.fixed(outerNow.atStartOfDay().plusHours(14).getInstant(), ZoneOffset.UTC);

    try (var now = UTCInstant.setClock(twoOClock)) {
      var tmpKey = new GaenKeyInternal();
      tmpKey.setRollingStartNumber((int) now.atStartOfDay().get10MinutesSince1970());
      tmpKey.setKeyData(Base64.getEncoder().encodeToString("testKey32Bytes--".getBytes("UTF-8")));
      tmpKey.setRollingPeriod(
          (int) Duration.ofHours(10).dividedBy(GaenUnit.TenMinutes.getDuration()));
      tmpKey.setFake(0);
      tmpKey.setTransmissionRiskLevel(0);
      tmpKey.setOrigin("CH");
      tmpKey.setCountries(List.of("CH"));

      gaenDataService.upsertExposees(List.of(tmpKey), now);
    }
    // key was inserted with a rolling period of 10 hours, which means the key is not allowed to be
    // released before 14

    // 12:01 no key
    try (var now = UTCInstant.setClock(twelve01Clock)) {
      UTCInstant publishedUntil = now.roundToBucketStart(BUCKET_LENGTH);
      var returnedKeysV1 =
          gaenDataService.getSortedExposedForKeyDate(
              now.atStartOfDay(), UTCInstant.midnight1970(), publishedUntil, now, false);
      assertEquals(0, returnedKeysV1.size());
      var returnedKeysV2 = gaenDataService.getSortedExposedSince(UTCInstant.midnight1970(), now, false);
      assertEquals(0, returnedKeysV2.size());
    }

    // 14:00 release the key
    try (var now = UTCInstant.setClock(fourteenOClock)) {
      UTCInstant publishedUntil = now.roundToBucketStart(BUCKET_LENGTH);
      var returnedKeysV1 =
          gaenDataService.getSortedExposedForKeyDate(
              now.atStartOfDay(), UTCInstant.midnight1970(), publishedUntil, now, false);
      assertEquals(1, returnedKeysV1.size());
      var returnedKeysV2 = gaenDataService.getSortedExposedSince(UTCInstant.midnight1970(), now, false);
      assertEquals(1, returnedKeysV2.size());
    }
  }

  @Test
  @Transactional
  public void upsertMultipleTimes() throws Exception {
    var tmpKey = new GaenKeyInternal();
    tmpKey.setRollingStartNumber(
        (int) UTCInstant.today().minus(Duration.ofDays(1)).get10MinutesSince1970());
    tmpKey.setKeyData(Base64.getEncoder().encodeToString("testKey32Bytes05".getBytes("UTF-8")));
    tmpKey.setRollingPeriod(144);
    tmpKey.setFake(0);
    tmpKey.setTransmissionRiskLevel(0);
    tmpKey.setOrigin("CH");
    var tmpKey2 = new GaenKeyInternal();
    tmpKey2.setRollingStartNumber(
        (int) UTCInstant.today().minus(Duration.ofDays(1)).get10MinutesSince1970());
    tmpKey2.setKeyData(Base64.getEncoder().encodeToString("testKey32Bytes05".getBytes("UTF-8")));
    tmpKey2.setRollingPeriod(144);
    tmpKey2.setFake(0);
    tmpKey2.setTransmissionRiskLevel(0);
    tmpKey2.setOrigin("CH");
    List<GaenKeyInternal> keys = List.of(tmpKey, tmpKey2);
    var now = UTCInstant.now();
    gaenDataService.upsertExposees(keys, now);

    // calculate exposed until bucket, but get bucket in the future, as keys have
    // been inserted with timestamp now.
    UTCInstant publishedUntil = now.roundToNextBucket(BUCKET_LENGTH);

    var returnedKeys =
        gaenDataService.getSortedExposedForKeyDate(
            UTCInstant.today().minusDays(1), UTCInstant.midnight1970(), publishedUntil, now, false);

    assertEquals(1, returnedKeys.size());
    assertEquals(keys.get(1).getKeyData(), returnedKeys.get(0).getKeyData());
  }
  

  @Test
  @Transactional
  public void upsertMultipleCountry() throws Exception {
    var tmpKey = new GaenKeyInternal();
    tmpKey.setRollingStartNumber(
        (int) UTCInstant.today().minus(Duration.ofDays(1)).get10MinutesSince1970());
    tmpKey.setKeyData(Base64.getEncoder().encodeToString("testKey32Bytes01".getBytes("UTF-8")));
    tmpKey.setRollingPeriod(144);
    tmpKey.setFake(0);
    tmpKey.setTransmissionRiskLevel(0);
    tmpKey.setOrigin("CH");
    var tmpKey2 = new GaenKeyInternal();
    tmpKey2.setRollingStartNumber(
        (int) UTCInstant.today().minus(Duration.ofDays(1)).get10MinutesSince1970());
    tmpKey2.setKeyData(Base64.getEncoder().encodeToString("testKey32Bytes02".getBytes("UTF-8")));
    tmpKey2.setRollingPeriod(144);
    tmpKey2.setFake(0);
    tmpKey2.setTransmissionRiskLevel(0);
    tmpKey2.setOrigin("CH");
    tmpKey2.setCountries(List.of("DE"));
    var tmpKey3 = new GaenKeyInternal();
    tmpKey3.setRollingStartNumber(
        (int) UTCInstant.today().minus(Duration.ofDays(1)).get10MinutesSince1970());
    tmpKey3.setKeyData(Base64.getEncoder().encodeToString("testKey32Bytes03".getBytes("UTF-8")));
    tmpKey3.setRollingPeriod(144);
    tmpKey3.setFake(0);
    tmpKey3.setTransmissionRiskLevel(0);
    tmpKey3.setOrigin("IT");
    tmpKey3.setCountries(List.of("DE","CH"));
    var tmpKey4 = new GaenKeyInternal();
    tmpKey4.setRollingStartNumber(
            (int) UTCInstant.today().minus(Duration.ofDays(1)).get10MinutesSince1970());
    tmpKey4.setKeyData(Base64.getEncoder().encodeToString("testKey32Bytes04".getBytes("UTF-8")));
    tmpKey4.setRollingPeriod(144);
    tmpKey4.setFake(0);
    tmpKey4.setTransmissionRiskLevel(0);
    tmpKey4.setOrigin("DE");
    var tmpKey5 = new GaenKeyInternal();
    tmpKey5.setRollingStartNumber(
            (int) UTCInstant.today().minus(Duration.ofDays(1)).get10MinutesSince1970());
    tmpKey5.setKeyData(Base64.getEncoder().encodeToString("testKey32Bytes04".getBytes("UTF-8")));
    tmpKey5.setRollingPeriod(144);
    tmpKey5.setFake(0);
    tmpKey5.setTransmissionRiskLevel(0);
    tmpKey5.setOrigin("IE");
    
    
    List<GaenKeyInternal> keys = List.of(tmpKey, tmpKey2, tmpKey3, tmpKey4, tmpKey5);
    var now = UTCInstant.now();
    gaenDataService.upsertExposees(keys, now);

    // calculate exposed until bucket, but get bucket in the future, as keys have
    // been inserted with timestamp now.
    UTCInstant publishedUntil = now.roundToNextBucket(BUCKET_LENGTH);

    var returnedKeys =
    		gaenDataService.getSortedExposedSince(UTCInstant.today().minusDays(1), publishedUntil, false);

    assertEquals(3, returnedKeys.size());
    
    returnedKeys =
    		gaenDataService.getSortedExposedSince(UTCInstant.today().minusDays(1), publishedUntil, true);

    assertEquals(4, returnedKeys.size());

    returnedKeys =
    		gaenDataService.getSortedExposedSince(UTCInstant.today().minusDays(1), publishedUntil, List.of("IT"));

    assertEquals(1, returnedKeys.size());

    returnedKeys =
    		gaenDataService.getSortedExposedSince(UTCInstant.today().minusDays(1), publishedUntil, List.of("DE"));

    assertEquals(3, returnedKeys.size());

    returnedKeys =
            gaenDataService.getSortedExposedForKeyDate(
                UTCInstant.today().minusDays(1), UTCInstant.midnight1970(), publishedUntil, now, false);
    
    assertEquals(3, returnedKeys.size());

    returnedKeys =
            gaenDataService.getSortedExposedForKeyDate(
                UTCInstant.today().minusDays(1), UTCInstant.midnight1970(), publishedUntil, now, true);
    
    assertEquals(4, returnedKeys.size());

  }
  
}
