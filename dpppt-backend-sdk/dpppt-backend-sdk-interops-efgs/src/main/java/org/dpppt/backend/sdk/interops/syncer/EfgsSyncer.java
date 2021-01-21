package org.dpppt.backend.sdk.interops.syncer;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import org.dpppt.backend.sdk.data.gaen.FakeKeyService;
import org.dpppt.backend.sdk.data.gaen.GAENDataService;
import org.dpppt.backend.sdk.interops.batchsigning.SignatureGenerator;
import org.dpppt.backend.sdk.interops.model.EfgsProto;
import org.dpppt.backend.sdk.interops.model.EfgsProto.ReportType;
import org.dpppt.backend.sdk.model.gaen.GaenKey;
import org.dpppt.backend.sdk.model.gaen.GaenKeyInternal;
import org.dpppt.backend.sdk.utils.UTCInstant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.protobuf.ByteString;

import lombok.AllArgsConstructor;

/**
 * Interops syncer for the irish hub:
 * https://github.com/HSEIreland/covid-green-interoperability-service
 *
 * @author alig
 */
public class EfgsSyncer {

  private static final Object MUTEX = new Object();
  
  private final String baseUrl;
  private final int retentionDays;
  private final int efgsMaxAgeDays;
  private final long efgsMaxDownloadKeys;
  private final LocalDate efgsDownloadNotBefore;
  private final long efgsMaxUploadKeys;
  private final long efgsMinUploadKeys;
  private final SecureRandom random;
  private final Integer fakeKeySize;
  private final boolean fakeKeysEnabled;
  private final String originCountry;
  private final GAENDataService gaenDataService;
  
  private final Duration releaseBucketDuration;

  private final SyncStateService syncStateService;

  private final String efgsCallbackId;
  private final String efgsCallbackUrl;

  
  // path for downloading keys. the %s must be replaced by day dates to retreive the keys for one
  // day, for example: 2020-09-15
  private static final String DOWNLOAD_PATH = "/diagnosiskeys/download/%s";

  private static final String UPLOAD_PATH = "/diagnosiskeys/upload";
  
  private static final String CALLBACK_PATH = "/diagnosiskeys/callback";
  
  private static final String CALLBACK_PATH_WITH_ID = "/diagnosiskeys/callback/%s";

  private final RestTemplate restTemplate;
  
  private final SignatureGenerator signatureGenerator;
  
  private static final Logger logger = LoggerFactory.getLogger(EfgsSyncer.class);

  public EfgsSyncer(
      String baseUrl,
      int retentionDays,
      int efgsMaxAgeDays,
      long efgsMaxDownloadKeys,
      LocalDate efgsDownloadNotBefore,
      long efgsMaxUploadKeys,
      boolean fakeKeysEnabled,
      long efgsMinUploadKeys,
      Integer fakeKeySize,
      String originCountry,
      Duration releaseBucketDuration,
      GAENDataService gaenDataService,
      RestTemplate restTemplate,
      SignatureGenerator signatureGenerator,
      SyncStateService syncStateService,
      String efgsCallbackId,
      String efgsCallbackUrl) {
    this.baseUrl = baseUrl;
    this.retentionDays = retentionDays;
    this.efgsMaxAgeDays = efgsMaxAgeDays;
    this.efgsMaxDownloadKeys = efgsMaxDownloadKeys;
    this.efgsDownloadNotBefore = efgsDownloadNotBefore;
    this.efgsMaxUploadKeys = efgsMaxUploadKeys;
    this.fakeKeysEnabled = fakeKeysEnabled;
    this.efgsMinUploadKeys = efgsMinUploadKeys;
    this.originCountry = originCountry;
    this.gaenDataService = gaenDataService;
    this.restTemplate = restTemplate;
    this.releaseBucketDuration = releaseBucketDuration;
    this.signatureGenerator = signatureGenerator;
    this.syncStateService = syncStateService;
    this.efgsCallbackId = efgsCallbackId;
    this.efgsCallbackUrl = efgsCallbackUrl;
    this.random = new SecureRandom();
    this.fakeKeySize = fakeKeySize;
  }
  
  public void init() {
    try {
    	if (!Strings.isNullOrEmpty(this.efgsCallbackId)) {    		
    		setupCallback();
    	}
      } catch (Exception e) {
        logger.error("Exception while setting up callback subscription:", e);
      }
	  
  }

  public void sync() {
    long start = System.currentTimeMillis();
    logger.info("Start sync from: " + baseUrl);
    LocalDate today = LocalDate.now();
    try {
      download();
      upload();
    } catch (Exception e) {
      logger.error("Exception downloading keys:", e);
    }

    long end = System.currentTimeMillis();
    logger.info("Sync done in: " + (end - start) + " [ms]");
  }

  private void deleteCallback(String callbackId) {
	  
      UriComponentsBuilder builder =
              UriComponentsBuilder.fromHttpUrl(
                  baseUrl + String.format(CALLBACK_PATH_WITH_ID, callbackId));
      URI uri = builder.build().toUri();
      logger.info("DELETE request to: " + uri.toString());
      RequestEntity<Void> request =
              RequestEntity.delete(builder.build().toUri())
                  .build();
      ResponseEntity<Void> response = restTemplate.exchange(request, Void.class);
      

  }
  
  private void putCallback(String callbackId, String url) {
	  
      UriComponentsBuilder builder =
              UriComponentsBuilder.fromHttpUrl(
                  baseUrl + String.format(CALLBACK_PATH_WITH_ID, callbackId) + "?url=" + URLEncoder.encode(url, StandardCharsets.UTF_8));
      URI uri = builder.build().toUri();
      logger.info("PUT request to: " + uri.toString());
      RequestEntity<Void> request =
              RequestEntity.put(builder.build().toUri())
                  .build();
      ResponseEntity<Void> response = restTemplate.exchange(request, Void.class);
      

  }

  private void setupCallback() {
	  
    UriComponentsBuilder builder =
    		UriComponentsBuilder.fromHttpUrl(baseUrl + CALLBACK_PATH);
      
    URI uri = builder.build().toUri();
    logger.info("GET request to: " + uri.toString());
    RequestEntity<Void> request = 
    		RequestEntity.get(builder.build().toUri())
            .accept(MediaType.APPLICATION_JSON)
            .build();
    ResponseEntity<Callback[]> response = restTemplate.exchange(request, Callback[].class);
    List<Callback> callbacks = List.of(response.getBody());
    callbacks.stream().forEach(cb -> {
      if (cb.getCallbackId().equals(efgsCallbackId) && 
    		  !cb.getUrl().equals(efgsCallbackUrl)) {
        putCallback(efgsCallbackId, efgsCallbackUrl);
      } else {
         deleteCallback(cb.getCallbackId());
      }});
      if (callbacks.size() == 0) {
        putCallback(efgsCallbackId, efgsCallbackUrl);
      }	  
	  
  }
  
  private void upload() throws Exception {

	  var now = UTCInstant.now();
	  
	  if (syncStateService.getLastUploadKeyBundleTag() == null) {
	    // if no lastKeyBundleTag is set yet, go back to the start of the retention period and
	    // select next bucket.
		  syncStateService.setLastUploadKeyBundleTag(
	        now.minusDays(retentionDays).roundToNextBucket(releaseBucketDuration).getTimestamp());
	  }
	  var keysSince = UTCInstant.ofEpochMillis(syncStateService.getLastUploadKeyBundleTag());
	
	  List<GaenKeyInternal> exposedKeys = Lists.newArrayList();
	  int multiplier = 2;
	  UTCInstant till = now;
	  do {		  
		  exposedKeys = gaenDataService.getSortedExposedSince(keysSince, till, originCountry);	
		  till = now.minus(releaseBucketDuration.multipliedBy(multiplier++));		  
	  } while (exposedKeys.size() > this.efgsMaxUploadKeys);
	  UTCInstant keyBundleTag = till.roundToBucketStart(releaseBucketDuration);

	  exposedKeys.addAll(fillupKeys(now, exposedKeys));
	  
	  if (exposedKeys.isEmpty()) {
		  logger.info("No keys to upload");
		  return;
	  } else {
		  logger.info("Uploading " + exposedKeys.size() + " keys.");
	  }
	  
	  List<EfgsProto.DiagnosisKey> diagnosisKeys = exposedKeys.stream().map(ek -> {
		  
		  return EfgsProto.DiagnosisKey.newBuilder()
		  	.setKeyData(ByteString.copyFrom(java.util.Base64.getDecoder().decode(ek.getKeyData())))
		  	.setRollingStartIntervalNumber(ek.getRollingStartNumber())
		  	.setRollingPeriod(ek.getRollingPeriod())
		  	.setTransmissionRiskLevel(ek.getTransmissionRiskLevel())
		  	.addAllVisitedCountries(ek.getCountries())
		  	.setOrigin(this.originCountry)
		  	.setReportType(ReportType.CONFIRMED_TEST)
		  	.setDaysSinceOnsetOfSymptoms(ek.getDaysSinceOnsetOfSymptoms())
		  	.build();			  	
		  
	  }).collect(Collectors.toList());   
	  
	  EfgsProto.DiagnosisKeyBatch batch = EfgsProto.DiagnosisKeyBatch.newBuilder()
	  	.addAllKeys(diagnosisKeys)
	  	.build();
	  
	  byte[] batchBytes = BatchSignatureUtils.generateBytes(batch);
	  String signature = signatureGenerator.sign(batchBytes);
	  
        UriComponentsBuilder builder =
                UriComponentsBuilder.fromHttpUrl(baseUrl + UPLOAD_PATH);
            URI uri = builder.build().toUri();
            logger.info("Uploading Diagnosis Key Batch with to: " + uri.toString() + ". BatchTag: " + String.valueOf(keyBundleTag.get10MinutesSince1970()));
            
            RequestEntity<byte[]> request =
                RequestEntity.post(builder.build().toUri())
                	.contentType(MediaType.parseMediaType("application/protobuf; version=1.0"))
                    .headers(createUploadHeaders(keyBundleTag, signature))
                    .body(batch.toByteArray());
            
            ResponseEntity<String> response =
            		restTemplate.exchange(request, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
            	if (response.getStatusCode().equals(HttpStatus.CREATED)) {
            		syncStateService.setLastUploadKeyBundleTag(keyBundleTag.getTimestamp());
            		logger.info("Upload successful. BatchTag: " + response.getHeaders().getOrEmpty("batchTag"));
            	}
            	if (response.getStatusCodeValue() == 207) {
            		logger.warn("The upload was only partially successful. Response: " + response.getBody());
            	}
            } else {
            	logger.error("Upload failed with code " + response.getStatusCodeValue() + " and message " + response.getBody());
            }
	  
  }

  private List<GaenKeyInternal> fillupKeys(UTCInstant now, List<GaenKeyInternal> exposedKeys) {
	var fakeKeys = new ArrayList<GaenKeyInternal>();
	if (fakeKeysEnabled) {
	  for (int i = exposedKeys.size(); i < efgsMinUploadKeys; i++) {
	    byte[] keyData = new byte[fakeKeySize];
	    random.nextBytes(keyData);
	    var keyGAENTime = (int) now.atStartOfDay().get10MinutesSince1970();
	    var key = new GaenKeyInternal();
	    key.setKeyData(Base64.getEncoder().encodeToString(keyData));
	    key.setRollingStartNumber(keyGAENTime);
	    key.setRollingPeriod(144);
	    key.setOrigin(originCountry);
	    key.setReportType("CONFIRMED_TEST");
	    key.setDaysSinceOnsetOfSymptoms(14);
	    fakeKeys.add(key);
	  }	      
    }
	return fakeKeys;
  }
  
  public Callable<Long> startDownload(LocalDate dayDate, String startBatchTag) {

	return new Callable<Long>() {

		@Override
		public Long call() throws Exception {
			return download(dayDate, startBatchTag);
		}
	};
  }

  public long download(LocalDate dayDate, String startBatchTag) {
	synchronized (MUTEX) {
	  logger.info("Start download: " + dayDate + " with batchTag " + startBatchTag);
      List<EfgsProto.DiagnosisKey> receivedKeys = new ArrayList<>();
      receivedKeys.addAll(doDownload(dayDate, startBatchTag, false).diagnosisKey);
      saveDiagnosisKeys(receivedKeys);
      return receivedKeys.size();		
	}
  }
  
  private void download() {
	synchronized (MUTEX) {
	    LocalDate today = LocalDate.now();
		LocalDate dayDate = today.minusDays(efgsMaxAgeDays);
		if (dayDate.isBefore(efgsDownloadNotBefore)) {
			dayDate = efgsDownloadNotBefore.plusDays(0); // Make a copy
		}
	    logger.info("Start download: " + dayDate + " - " + today);
	    List<EfgsProto.DiagnosisKey> receivedKeys = new ArrayList<>();
	    while (dayDate.isBefore(today.plusDays(1))) {
	      String lastBatchTagForDay = syncStateService.getLastDownloadedBatchTag(dayDate);
	      DownloadResult res = doDownload(dayDate, lastBatchTagForDay, lastBatchTagForDay != null);
	      receivedKeys.addAll(res.diagnosisKey);
	      this.syncStateService.setLastDownloadedBatchTag(dayDate, res.lastDownloadedBatchTag);
	      dayDate = dayDate.plusDays(1);
	      if (receivedKeys.size() >= efgsMaxDownloadKeys) {
	    	dayDate = today.plusDays(1);
	      }
	    }
	
	    saveDiagnosisKeys(receivedKeys);
	}
  }

  @Transactional(readOnly = false)
  private void saveDiagnosisKeys(List<EfgsProto.DiagnosisKey> receivedKeys) {
	UTCInstant now = UTCInstant.now();
    logger.info("Received " + receivedKeys.size() + " keys. Store ...");
    long count = 0;
    for (EfgsProto.DiagnosisKey diagKey : receivedKeys) {
      GaenKeyInternal gaenKey = mapToGaenKey(diagKey);
      if (diagKey.getOrigin() != null
          && !diagKey.getOrigin().isBlank()
          && !diagKey.getVisitedCountriesList().isEmpty()) {
        gaenDataService.upsertExposee(gaenKey, now);
        count++;
        if (count % 1000 == 0) {
        	logger.info("Stored " + count + " of " + receivedKeys.size() + ".");
        }
      }
    }
  }

  @AllArgsConstructor
  private static class DownloadResult {
	  List<EfgsProto.DiagnosisKey> diagnosisKey;
	  String lastDownloadedBatchTag;
  }
  
  private DownloadResult doDownload(LocalDate dayDate, String startBatchTag, boolean discardFirstBatch) {
	 logger.info(
          "Download keys for: "
              + dayDate
              + " from BatchTag: "
              + (startBatchTag != null ? startBatchTag : " none"));

      List<EfgsProto.DiagnosisKey> receivedKeys = new ArrayList<>();
      boolean done = false;

  	  String currentBatchTagForDay = startBatchTag; 
      while (!done) {
        UriComponentsBuilder builder =
            UriComponentsBuilder.fromHttpUrl(
                baseUrl
                    + String.format(
                        DOWNLOAD_PATH, dayDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))));
        URI uri = builder.build().toUri();
        logger.info("Request key for: " + uri.toString() + ". BatchTag: " + currentBatchTagForDay);
        RequestEntity<Void> request =
            RequestEntity.get(builder.build().toUri())
                .accept(MediaType.parseMediaType("application/protobuf; version=1.0"))
                .headers(createDownloadHeaders(currentBatchTagForDay))
                .build();
        ResponseEntity<EfgsProto.DiagnosisKeyBatch> response = null;
        try {
        	response =
        		restTemplate.exchange(request, EfgsProto.DiagnosisKeyBatch.class);

        } catch (HttpClientErrorException e) {
        	if (e.getStatusCode().equals(HttpStatus.NOT_FOUND)) {
        		done = true;
        		logger.info(e.getResponseBodyAsString());
        		continue;
        	} else {
        		throw e;
        	}
        }
        if (response.getStatusCode().is2xxSuccessful()) {
          if (response.getStatusCode().equals(HttpStatus.OK)) {
        	EfgsProto.DiagnosisKeyBatch downloadResponse = response.getBody();
        	String batchTag = response.getHeaders().getValuesAsList("batchTag").get(0);
        	String nextBatchTag = response.getHeaders().getValuesAsList("nextBatchTag").get(0);
            logger.info(
                "Got 200. BatchTag: "
                    + batchTag
                    + " NextBatchTag: "
                    + nextBatchTag);
            
            if (null == downloadResponse) {
            	logger.warn("Got a null body");
            } else {
                logger.info(" Number of keys: "
                        + downloadResponse.getKeysCount());            	
                if (!discardFirstBatch) {
                    receivedKeys.addAll(downloadResponse.getKeysList());
                } else {
                	logger.info("Discarding batch " + batchTag);
                }
            }            
            discardFirstBatch = false;
            
            if ("null".equals(nextBatchTag)) {
                logger.info("Got empty nextBatchTag. Store last batch tag");
                // no more keys to load. store last batch tag for next sync
            	done = true;
            	currentBatchTagForDay = batchTag;
            	continue;
            } 
            if (receivedKeys.size() >= efgsMaxDownloadKeys) {
                logger.info("Exceeded efgsMaxDownloadKeys. Stopping download");
                // no more keys to load. store last batch tag for next sync
            	done = true;
            	currentBatchTagForDay = batchTag;
            	continue;
            }
            currentBatchTagForDay = nextBatchTag;            	
            
          }
        }
      }
	return new DownloadResult(receivedKeys, currentBatchTagForDay);
}

  private GaenKeyInternal mapToGaenKey(EfgsProto.DiagnosisKey diagKey) {
    GaenKeyInternal gaenKey = new GaenKeyInternal();    
    gaenKey.setKeyData(Base64.getEncoder().encodeToString(diagKey.getKeyData().toByteArray()));
    gaenKey.setRollingPeriod(diagKey.getRollingPeriod());
    gaenKey.setRollingStartNumber(diagKey.getRollingStartIntervalNumber());
    gaenKey.setFake(0);
    gaenKey.setTransmissionRiskLevel(diagKey.getTransmissionRiskLevel());
    gaenKey.setDaysSinceOnsetOfSymptoms(diagKey.getDaysSinceOnsetOfSymptoms());
    gaenKey.setCountries(diagKey.getVisitedCountriesList());
    gaenKey.setOrigin(diagKey.getOrigin());
    return gaenKey;
  }

  private HttpHeaders createUploadHeaders(UTCInstant batchTag, String signature) {
    HttpHeaders headers = new HttpHeaders();
    headers.add("batchTag", String.valueOf(batchTag.get10MinutesSince1970()));    	
    headers.add("batchSignature", signature);
    return headers;
  }

  private HttpHeaders createDownloadHeaders(String batchTag) {
    HttpHeaders headers = new HttpHeaders();
    if (null != batchTag) {
        headers.add("batchTag", batchTag);    	
    }
    return headers;
  }
}
