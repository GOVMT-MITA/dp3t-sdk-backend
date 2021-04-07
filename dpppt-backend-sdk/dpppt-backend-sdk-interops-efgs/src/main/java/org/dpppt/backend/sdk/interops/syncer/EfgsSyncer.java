package org.dpppt.backend.sdk.interops.syncer;

import java.net.URI;
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

import org.dpppt.backend.sdk.data.gaen.GAENDataService;
import org.dpppt.backend.sdk.interops.batchsigning.SignatureGenerator;
import org.dpppt.backend.sdk.interops.model.EfgsProto;
import org.dpppt.backend.sdk.interops.model.EfgsProto.ReportType;
import org.dpppt.backend.sdk.model.gaen.GaenKeyInternal;
import org.dpppt.backend.sdk.utils.UTCInstant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.datetime.DateFormatter;
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
  private final List<String> otherCountries;
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
      List<String> otherCountries,
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
    this.otherCountries = otherCountries;
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
                  baseUrl + String.format(CALLBACK_PATH_WITH_ID, callbackId) + "?url=" + url); // URLEncoder.encode(url, StandardCharsets.UTF_8));
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
      if (!cb.getCallbackId().equals(efgsCallbackId)) { 
        deleteCallback(cb.getCallbackId());
      }});
      putCallback(efgsCallbackId, efgsCallbackUrl);
	  
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
	  UTCInstant till = now.roundToBucketStart(releaseBucketDuration);
	  do {		  
		  exposedKeys = gaenDataService.getSortedExposedSince(keysSince, till, originCountry).stream()
				  			.filter(k -> Strings.isNullOrEmpty(k.getEfgsUploadTag()))
				  			.filter(k -> !(k.getCountries().size() == 0 || (k.getCountries().size() == 1 && k.getCountries().get(0).equals(originCountry))))
				  			.collect(Collectors.toList());
		  
		  till = now.minus(releaseBucketDuration.multipliedBy(multiplier++));		  
	  } while (exposedKeys.size() > this.efgsMaxUploadKeys);
	  UTCInstant keyBundleTag = till;

	  if (exposedKeys.isEmpty()) {
		  logger.info("No keys to upload");
		  return;
	  } else {
		  logger.info("Uploading " + exposedKeys.size() + " keys.");
	  }

	  List<GaenKeyInternal> finalKeys = Lists.newArrayList();
	  finalKeys.addAll(exposedKeys);
  	  if (fakeKeysEnabled) {
  		finalKeys.addAll(fillupKeys(now, finalKeys));
  	  }
	  
	  List<EfgsProto.DiagnosisKey> diagnosisKeys = finalKeys.stream().map(ek -> {
		  
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
            
            ResponseEntity<?> response =
            		restTemplate.exchange(request, Object.class);

            if (response.getStatusCode().is2xxSuccessful()) {
            	if (response.getStatusCode().equals(HttpStatus.CREATED)) {
            		logger.info("Upload successful. BatchTag: " + response.getHeaders().getOrEmpty("batchTag"));
            		gaenDataService.markUploaded(exposedKeys, String.valueOf(keyBundleTag.get10MinutesSince1970()));
            	}
            	if (response.getStatusCodeValue() == 207) {
            		logger.warn("The upload was only partially successful. Response: " + response.getBody());
            	}
        		syncStateService.setLastUploadKeyBundleTag(keyBundleTag.getTimestamp());
            } else {
            	logger.error("Upload failed with code " + response.getStatusCodeValue() + " and message " + response.getBody());
            }
	  
  }

  private List<GaenKeyInternal> fillupKeys(UTCInstant now, List<GaenKeyInternal> exposedKeys) {
	var fakeKeys = new ArrayList<GaenKeyInternal>();
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
	    key.setCountries(this.otherCountries);
	    fakeKeys.add(key);
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

  public Callable<Void> startSync() {

	return new Callable<Void>() {

		@Override
		public Void call() throws Exception {
			sync();
			return null;
		}
	};
  }

  public long download(LocalDate dayDate, String startBatchTag) {
	  logger.info("Start download: " + dayDate + " with batchTag " + startBatchTag);
	  
	  boolean updateState = false;
	  Integer nextBatchNumForDay = syncStateService.getNextDownloadBatchNum(dayDate);
	  if (null != nextBatchNumForDay) {
		  int startBatchNum = getBatchNumber(startBatchTag);
		  updateState = nextBatchNumForDay == startBatchNum;
	  }
	  
	  DownloadResult res = doDownload(dayDate, startBatchTag, 0);
	  saveDiagnosisKeys(res.diagnosisKey, res.keysCount);
	  if (updateState) {
		  logger.info("Updating state for " + DateTimeFormatter.ISO_LOCAL_DATE.format(dayDate) + ". Setting next batch number to " + res.nextBatchNum);
		  this.syncStateService.setNextDownloadBatchNum(dayDate, res.nextBatchNum);
	  } else {
		  logger.info("Not updating state for " + DateTimeFormatter.ISO_LOCAL_DATE.format(dayDate) + ". Next batch number is still " + nextBatchNumForDay);
	  }
	  return res.keysCount;		
  }
  
  private void download() {
    LocalDate today = LocalDate.now();
	LocalDate dayDate = today.minusDays(efgsMaxAgeDays);
	if (dayDate.isBefore(efgsDownloadNotBefore)) {
		dayDate = efgsDownloadNotBefore.plusDays(0); // Make a copy
	}
    logger.info("Start download: " + dayDate + " - " + today);
    long receivedKeys = 0;
    while (dayDate.isBefore(today.plusDays(1))) {
      String nextBatchTagForDay = getBatchTag(dayDate, syncStateService.getNextDownloadBatchNum(dayDate));
      DownloadResult res = doDownload(dayDate, nextBatchTagForDay, receivedKeys);
      saveDiagnosisKeys(res.diagnosisKey, res.keysCount);
      receivedKeys += res.keysCount;
      this.syncStateService.setNextDownloadBatchNum(dayDate, res.nextBatchNum);
      dayDate = dayDate.plusDays(1);
      if (receivedKeys >= efgsMaxDownloadKeys) {
    	dayDate = today.plusDays(1);
      }
    }

  }

  @Transactional(readOnly = false)
  private long saveDiagnosisKeys(Map<String, List<EfgsProto.DiagnosisKey>> receivedKeys, long keysCount) {
	UTCInstant now = UTCInstant.now();
	
    logger.info("Received " + keysCount + " keys. Store ...");
    long storedCount = 0;
    for (Map.Entry<String, List<EfgsProto.DiagnosisKey>> diagKeys : receivedKeys.entrySet()) {
      /*long existKeyCount = gaenDataService.efgsBatchExists(diagKeys.getKey());
      if (existKeyCount > 0 && existKeyCount == diagKeys.getValue().size()) {
    	  logger.warn("Batch " + diagKeys.getKey() + " has already been downloaded before. Not storing.");
    	  continue;
      }*/
      for (EfgsProto.DiagnosisKey diagKey : diagKeys.getValue()) {
          
    	  if (!this.otherCountries.contains(diagKey.getOrigin())) continue;
      	
          GaenKeyInternal gaenKey = mapToGaenKey(diagKey);
          gaenKey.setEfgsBatchTag(diagKeys.getKey());
          gaenDataService.upsertExposee(gaenKey, now);
          storedCount++;
          if (storedCount % 1000 == 0) {
          	logger.info("Stored " + storedCount + " of " + keysCount + ".");
          }    	  
      }      
    }
    return storedCount;
  }

  @AllArgsConstructor
  private static class DownloadResult {
	  Map<String, List<EfgsProto.DiagnosisKey>> diagnosisKey;
	  int nextBatchNum;
	  long keysCount;
  }
  
  private DownloadResult doDownload(LocalDate dayDate, String startBatchTag, long runningKeyCount) {
	 logger.info(
          "Download keys for: "
              + dayDate
              + " from BatchTag: "
              + (startBatchTag != null ? startBatchTag : " none"));

	  Map<String, List<EfgsProto.DiagnosisKey>> receivedKeys = new HashMap<>();
      boolean done = false;

  	  String currentBatchTagForDay = startBatchTag; 
  	  int nextBatchNum = 0;
  	  long keysCount = 0;
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
        		logger.warn(e.getResponseBodyAsString());
        		nextBatchNum = getBatchNumber(startBatchTag);
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

                long existKeyCount = gaenDataService.efgsBatchExists(batchTag);
                if (existKeyCount == 0 || existKeyCount != downloadResponse.getKeysCount()) {
                  receivedKeys.put(batchTag, downloadResponse.getKeysList());
              	  keysCount += downloadResponse.getKeysCount();
                } else {
               	  logger.warn("Batch " + batchTag + " has already been downloaded before. Discarding.");                	
                }
            }            
            
            if ("null".equals(nextBatchTag)) {
            	nextBatchNum = getBatchNumber(batchTag) + 1;
                logger.info("Got empty nextBatchTag. Store next batch tag as " + nextBatchNum);
                // no more keys to load. store last batch tag for next sync
            	done = true;
            	currentBatchTagForDay = batchTag;
            	continue;
            } 
            if ((runningKeyCount + keysCount) >= efgsMaxDownloadKeys) {
                logger.info("Exceeded efgsMaxDownloadKeys. Stopping download and storing next batch tag as " + (getBatchNumber(batchTag) + 1));

                // no more keys to load. 
            	done = true;
            }
            currentBatchTagForDay = nextBatchTag;            	
        	nextBatchNum = getBatchNumber(nextBatchTag);
            
          }
        }
      }
	return new DownloadResult(receivedKeys, nextBatchNum, keysCount);
  }
  
  private int getBatchNumber(String batchTag) {
	  return Integer.valueOf(batchTag.split("-")[1]);
  }

  private String getBatchTag(LocalDate date, Integer batchNum) {
	  if (null == date || null == batchNum) return null;
	  return DateTimeFormatter.BASIC_ISO_DATE.format(date) + "-" + batchNum;
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
