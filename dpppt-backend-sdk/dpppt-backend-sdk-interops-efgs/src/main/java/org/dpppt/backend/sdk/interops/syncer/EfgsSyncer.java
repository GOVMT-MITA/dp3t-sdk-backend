package org.dpppt.backend.sdk.interops.syncer;

import java.net.URI;
import java.net.URISyntaxException;
import java.security.cert.Certificate;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import java.security.cert.X509Certificate;

import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.cms.CMSTypedData;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.bouncycastle.util.Store;
import org.dpppt.backend.sdk.data.gaen.GAENDataService;
import org.dpppt.backend.sdk.interops.batchsigning.SignatureGenerator;
import org.dpppt.backend.sdk.interops.model.EfgsProto;
import org.dpppt.backend.sdk.interops.model.IrishHubDownloadResponse;
import org.dpppt.backend.sdk.interops.model.IrishHubKey;
import org.dpppt.backend.sdk.model.gaen.GaenKey;
import org.dpppt.backend.sdk.utils.UTCInstant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.google.common.base.Strings;

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
  private final GAENDataService gaenDataService;
  
  private Long lastUploadKeyBundleTag = null;
  private final Duration releaseBucketDuration;

  // keep batch tags per day.
  private Map<LocalDate, String> lastBatchTag = new HashMap<>();

  // path for downloading keys. the %s must be replaced by day dates to retreive the keys for one
  // day, for example: 2020-09-15
  private static final String DOWNLOAD_PATH = "/diagnosiskeys/download/%s";

  private static final String UPLOAD_PATH = "/diagnosiskeys/upload";

  private final RestTemplate restTemplate;
  
  private final SignatureGenerator signatureGenerator;
  
  private static final Logger logger = LoggerFactory.getLogger(EfgsSyncer.class);

  public EfgsSyncer(
      String baseUrl,
      int retentionDays,
      int efgsMaxAgeDays,
      long efgsMaxDownloadKeys,
      Duration releaseBucketDuration,
      GAENDataService gaenDataService,
      RestTemplate restTemplate,
      SignatureGenerator signatureGenerator) {
    this.baseUrl = baseUrl;
    this.retentionDays = retentionDays;
    this.efgsMaxAgeDays = efgsMaxAgeDays;
    this.efgsMaxDownloadKeys = efgsMaxDownloadKeys;
    this.gaenDataService = gaenDataService;
    this.restTemplate = restTemplate;
    this.releaseBucketDuration = releaseBucketDuration;
    this.signatureGenerator = signatureGenerator;
  }

  public void sync() {
    long start = System.currentTimeMillis();
    logger.info("Start sync from: " + baseUrl);
    LocalDate today = LocalDate.now();
    try {
      download(today);
      //upload(lastUploadKeyBundleTag);
    } catch (Exception e) {
      logger.error("Exception downloading keys:", e);
    }

    long end = System.currentTimeMillis();
    logger.info("Sync done in: " + (end - start) + " [ms]");
  }

  private void upload(Long lastKeyBundleTag) throws URISyntaxException {

	  var now = UTCInstant.now();
	  
	  if (lastKeyBundleTag == null) {
	    // if no lastKeyBundleTag is set yet, go back to the start of the retention period and
	    // select next bucket.
	    lastKeyBundleTag =
	        now.minusDays(retentionDays).roundToNextBucket(releaseBucketDuration).getTimestamp();
	  }
	  var keysSince = UTCInstant.ofEpochMillis(lastKeyBundleTag);
	
	  UTCInstant keyBundleTag = now.roundToBucketStart(releaseBucketDuration);
	
	  List<GaenKey> exposedKeys =
			  gaenDataService.getSortedExposedSince(keysSince, now, null);
	
	  if (exposedKeys.isEmpty()) {
		  
	  }
	  
	  lastUploadKeyBundleTag = keyBundleTag.getTimestamp();
	  
  }
  
  private void download(LocalDate today) throws URISyntaxException {
    LocalDate endDate = today.minusDays(efgsMaxAgeDays);
    LocalDate dayDate = endDate;
    logger.info("Start download: " + endDate + " - " + today);
    List<EfgsProto.DiagnosisKey> receivedKeys = new ArrayList<>();
    while (dayDate.isBefore(today.plusDays(1))) {
      String lastBatchTagForDay = lastBatchTag.get(dayDate);
      logger.info(
          "Download keys for: "
              + dayDate
              + " from BatchTag: "
              + (lastBatchTagForDay != null ? lastBatchTagForDay : " none"));

      boolean done = false;

  	String currentBatchTagForDay = lastBatchTagForDay; 
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
        ResponseEntity<EfgsProto.DiagnosisKeyBatch> response =
        		restTemplate.exchange(request, EfgsProto.DiagnosisKeyBatch.class);

        if (response.getStatusCode().is2xxSuccessful()) {
          if (response.getStatusCode().equals(HttpStatus.OK)) {
        	EfgsProto.DiagnosisKeyBatch downloadResponse = response.getBody();
        	String batchTag = response.getHeaders().getValuesAsList("batchTag").get(0);
        	String nextBatchTag = response.getHeaders().getValuesAsList("nextBatchTag").get(0);
            logger.info(
                "Got 200. BatchTag: "
                    + batchTag
                    + " NextBatchTag: "
                    + nextBatchTag 
                    + " Number of keys: "
                    + downloadResponse.getKeysCount());
            
            receivedKeys.addAll(downloadResponse.getKeysList());
            
            if ("null".equals(nextBatchTag)) {
                logger.info("Got empty nextBatchTag. Store last batch tag");
                // no more keys to load. store last batch tag for next sync
                this.lastBatchTag.put(dayDate, currentBatchTagForDay);
            	done = true;
            } else if (receivedKeys.size() >= efgsMaxDownloadKeys) {
                logger.info("Exceeded efgsMaxDownloadKeys. Stopping download");
                // no more keys to load. store last batch tag for next sync
                this.lastBatchTag.put(dayDate, "null".equals(nextBatchTag) ? currentBatchTagForDay : nextBatchTag);
            	done = true;            	
            	dayDate = today.plusDays(1);
            } else {
            	currentBatchTagForDay = nextBatchTag;            	
            }
            
          }
        }
      }
      dayDate = dayDate.plusDays(1);
    }

    UTCInstant now = UTCInstant.now();
    logger.info("Received " + receivedKeys.size() + " keys. Store ...");
    for (EfgsProto.DiagnosisKey diagKey : receivedKeys) {
      GaenKey gaenKey = mapToGaenKey(diagKey);
      if (diagKey.getOrigin() != null
          && !diagKey.getOrigin().isBlank()
          && !diagKey.getVisitedCountriesList().isEmpty()) {
        gaenDataService.upsertExposeeFromInterops(
            gaenKey, now, diagKey.getOrigin(), diagKey.getVisitedCountriesList());
      }
    }
  }

  private GaenKey mapToGaenKey(EfgsProto.DiagnosisKey diagKey) {
    GaenKey gaenKey = new GaenKey();
    gaenKey.setKeyData(diagKey.getKeyData().toStringUtf8());
    gaenKey.setRollingPeriod(diagKey.getRollingPeriod());
    gaenKey.setRollingStartNumber(diagKey.getRollingStartIntervalNumber());
    return gaenKey;
  }

  private HttpHeaders createDownloadHeaders(String batchTag) {
    HttpHeaders headers = new HttpHeaders();
    if (null != batchTag) {
        headers.add("batchTag", batchTag);    	
    }
    return headers;
  }
}
