package org.dpppt.backend.sdk.ws.controller;

import ch.ubique.openapi.docannotations.Documentation;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import javax.validation.Valid;
import org.dpppt.backend.sdk.data.gaen.FakeKeyService;
import org.dpppt.backend.sdk.data.gaen.GAENDataService;
import org.dpppt.backend.sdk.model.gaen.GaenKey;
import org.dpppt.backend.sdk.model.gaen.GaenKeyInternal;
import org.dpppt.backend.sdk.model.gaen.GaenV2UploadKeysRequest;
import org.dpppt.backend.sdk.utils.DurationExpiredException;
import org.dpppt.backend.sdk.utils.UTCInstant;
import org.dpppt.backend.sdk.ws.insertmanager.InsertException;
import org.dpppt.backend.sdk.ws.insertmanager.InsertManager;
import org.dpppt.backend.sdk.ws.insertmanager.insertionfilters.AssertKeyFormat.KeyFormatException;
import org.dpppt.backend.sdk.ws.security.ValidateRequest;
import org.dpppt.backend.sdk.ws.security.ValidateRequest.ClaimIsBeforeOnsetException;
import org.dpppt.backend.sdk.ws.security.ValidateRequest.InvalidDateException;
import org.dpppt.backend.sdk.ws.security.ValidateRequest.WrongScopeException;
import org.dpppt.backend.sdk.ws.security.signature.ProtoSignature;
import org.dpppt.backend.sdk.ws.security.signature.ProtoSignature.ProtoSignatureWrapper;
import org.dpppt.backend.sdk.ws.util.ValidationUtils;
import org.dpppt.backend.sdk.ws.util.ValidationUtils.BadBatchReleaseTimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

/** This is a new controller to simplify the sending and receiving of keys using ENv1.5/ENv2. */
@Controller
@RequestMapping("/v2/gaen")
@Documentation(description = "The GAEN V2 endpoint for the mobile clients supporting ENv1.5/ENv2")
public class GaenV2Controller {

  private static final Logger logger = LoggerFactory.getLogger(GaenV2Controller.class);

  private final InsertManager insertManager;

  private final ValidateRequest validateRequest;

  private final ValidationUtils validationUtils;
  private final FakeKeyService fakeKeyService;
  private final ProtoSignature gaenSigner;
  private final GAENDataService dataService;
  private final Duration releaseBucketDuration;
  private final Duration requestTime;
  private final Duration exposedListCacheControl;
  private final Duration retentionPeriod;
  private final String originCountry;
  private final boolean interopEnabled;
  
  private static final String HEADER_X_KEY_BUNDLE_TAG = "x-key-bundle-tag";

  public GaenV2Controller(
      InsertManager insertManager,
      ValidateRequest validateRequest,
      ValidationUtils validationUtils,
      FakeKeyService fakeKeyService,
      ProtoSignature gaenSigner,
      GAENDataService dataService,
      Duration releaseBucketDuration,
      Duration requestTime,
      Duration exposedListCacheControl,
      Duration retentionPeriod,
      String originCountry,
      boolean interopEnabled) {
    this.insertManager = insertManager;
    this.validateRequest = validateRequest;
    this.validationUtils = validationUtils;
    this.fakeKeyService = fakeKeyService;
    this.gaenSigner = gaenSigner;
    this.dataService = dataService;
    this.releaseBucketDuration = releaseBucketDuration;
    this.requestTime = requestTime;
    this.exposedListCacheControl = exposedListCacheControl;
    this.retentionPeriod = retentionPeriod;
    this.originCountry = originCountry;
    this.interopEnabled = interopEnabled;
  }

  @GetMapping(value = "")
  @Documentation(
      description = "Hello return",
      responses = {"200=>server live"})
  public @ResponseBody ResponseEntity<String> hello() {
    return ResponseEntity.ok().header("X-HELLO", "dp3t").body("Hello from DP3T WS GAEN V2");
  }

  @PostMapping(value = "/exposed")
  @Documentation(
      description = "Endpoint used to upload exposure keys to the backend",
      responses = {
        "200=>The exposed keys have been stored in the database",
        "400=> "
            + "- Invalid base64 encoding in GaenRequest"
            + "- negative rolling period"
            + "- fake claim with non-fake keys",
        "403=>Authentication failed"
      })
  public @ResponseBody Callable<ResponseEntity<String>> addExposed(
      @Documentation(description = "JSON Object containing all keys.") @Valid @RequestBody
          GaenV2UploadKeysRequest gaenV2Request,
      @RequestHeader(value = "User-Agent")
          @Documentation(
              description =
                  "App Identifier (PackageName/BundleIdentifier) + App-Version +"
                      + " OS (Android/iOS) + OS-Version",
              example = "ch.ubique.android.dp3t;1.0;iOS;13.3")
          String userAgent,
      @AuthenticationPrincipal
          @Documentation(description = "JWT token that can be verified by the backend server")
          Object principal)
      throws WrongScopeException, InsertException {
    var now = UTCInstant.now();

    this.validateRequest.isValid(principal);

    // Filter out non valid keys and insert them into the database (c.f.
    // InsertManager and
    // configured Filters in the WSBaseConfig)
    insertManager.insertIntoDatabase(
        gaenV2Request.getGaenKeys(),
        userAgent,
        principal,
        now,
        gaenV2Request.getCountries());
    var responseBuilder = ResponseEntity.ok();
    Callable<ResponseEntity<String>> cb =
        () -> {
          try {
            now.normalizeDuration(requestTime);
          } catch (DurationExpiredException e) {
            logger.error("Total time spent in endpoint is longer than requestTime");
          }
          return responseBuilder.body("OK");
        };
    return cb;
  }

  // GET for Key Download
  @GetMapping(value = "/exposed")
  @Documentation(
      description =
          "Requests keys published _after_ lastKeyBundleTag. The response includes also"
              + " international keys if includeAllInternationalKeys is set to true. (default is"
              + " false)",
      responses = {
        "200 => zipped export.bin and export.sig of all keys in that interval",
        "404 => Invalid _lastKeyBundleTag_"
      })
  public @ResponseBody ResponseEntity<byte[]> getExposedKeys(
		  @Documentation(
	              description =
	                  "List of countries of interest of requested keys. (iso-3166-1 alpha-2).",
	              example = "MT")
	          @RequestParam(required = false)
	          List<String> countries,
		  @Documentation(
              description =
                  "Only retrieve keys published after the specified key-bundle"
                      + " tag. Optional, if no tag set, all keys for the"
                      + " retention period are returned",
              example = "1593043200000")
          @RequestParam(required = false)
          Long lastKeyBundleTag)
      throws BadBatchReleaseTimeException, InvalidKeyException, SignatureException,
          NoSuchAlgorithmException, IOException {
    var now = UTCInstant.now();

    if (lastKeyBundleTag == null) {
      // if no lastKeyBundleTag given, go back to the start of the retention period and
      // select next bucket.
      lastKeyBundleTag =
          now.minus(retentionPeriod).roundToNextBucket(releaseBucketDuration).getTimestamp();
    }
    var keysSince = UTCInstant.ofEpochMillis(lastKeyBundleTag);

    //if (!validationUtils.isValidBatchReleaseTime(keysSince, now)) {
    //  return ResponseEntity.notFound().build();
    //}
    UTCInstant keyBundleTag = now.roundToBucketStart(releaseBucketDuration);
    
    // Make sure we're always interested in the origin country
    var effectiveCountries = enforceOriginCountry(countries);
    
    // When the user only requests the origin country, we return only keys with origin = origin country. Otherwise, we return
    // keys with origin IN given list of countries OR visited country IN given list of countries
    
    List<GaenKeyInternal> exposedKeysInternal = (effectiveCountries.size() == 1 && effectiveCountries.get(0).equals(originCountry)) ?
    		dataService.getSortedExposedSinceForOrigins(keysSince, now, effectiveCountries) :
    		dataService.getSortedExposedSince(keysSince, now, effectiveCountries);

    var exposedKeys = exposedKeysInternal.stream().map(ek -> ek.asGaenKey()).collect(Collectors.toList());
    
    // Log the download request
	logDownloadRequest(keysSince.getInstant(), effectiveCountries, exposedKeys.size());
        
    if (exposedKeys.isEmpty()) {
      return ResponseEntity.noContent()
          .cacheControl(CacheControl.maxAge(exposedListCacheControl))
          .header(HEADER_X_KEY_BUNDLE_TAG, Long.toString(keyBundleTag.getTimestamp()))
          .build();
    }
    ProtoSignatureWrapper payload = gaenSigner.getPayloadV2(exposedKeys);

    return ResponseEntity.ok()
        .cacheControl(CacheControl.maxAge(exposedListCacheControl))
        .header(HEADER_X_KEY_BUNDLE_TAG, Long.toString(keyBundleTag.getTimestamp()))
        .body(payload.getZip());
  }

private void logDownloadRequest(Instant keysSince, List<String> effectiveCountries, int numKeys) {
	StringBuilder sb = new StringBuilder();
    sb.append("User requested keys from ");
    sb.append(DateTimeFormatter.ISO_DATE_TIME.withZone(ZoneId.of("UTC")).format(keysSince));
    sb.append(" for countries ");
    effectiveCountries.forEach(c -> sb.append(c + ", "));
    sb.append("Returning " + numKeys + " keys.");
    logger.info(sb.toString());
}

  
  @GetMapping(value = "/exposed/raw")
  @Documentation(
      description =
          "Requests keys published _after_ lastKeyBundleTag. The response includes also"
              + " international keys if includeAllInternationalKeys is set to true. (default is"
              + " false)",
      responses = {
        "200 => List of all keys in that interval for the given countries of interest",
        "404 => Invalid _lastKeyBundleTag_"
      })
  public @ResponseBody ResponseEntity<List<GaenKeyInternalResponse>> getExposedKeysRaw(
		  @Documentation(
	              description =
	                  "List of countries of interest of requested keys. (iso-3166-1 alpha-2).",
	              example = "MT")
	          @RequestParam(required = false)
	          List<String> countries,
		  @Documentation(
              description =
                  "Only retrieve keys published after the specified key-bundle"
                      + " tag. Optional, if no tag set, all keys for the"
                      + " retention period are returned",
              example = "1593043200000")
          @RequestParam(required = false)
          Long lastKeyBundleTag)
      throws BadBatchReleaseTimeException, InvalidKeyException, SignatureException,
          NoSuchAlgorithmException, IOException {
    var now = UTCInstant.now();

    if (lastKeyBundleTag == null) {
      // if no lastKeyBundleTag given, go back to the start of the retention period and
      // select next bucket.
      lastKeyBundleTag =
          now.minus(retentionPeriod).roundToNextBucket(releaseBucketDuration).getTimestamp();
    }
    var keysSince = UTCInstant.ofEpochMillis(lastKeyBundleTag);

    //if (!validationUtils.isValidBatchReleaseTime(keysSince, now)) {
    //  return ResponseEntity.notFound().build();
    //}
    UTCInstant keyBundleTag = now.roundToBucketStart(releaseBucketDuration);
	
    // Make sure we're always interested in the origin country
    List<GaenKeyInternal> exposedKeysInternal =
        dataService.getSortedExposedSinceForOrigins(keysSince, now, defaultOriginCountry(countries));

    if (exposedKeysInternal.isEmpty()) {
      return ResponseEntity.noContent()
          .cacheControl(CacheControl.maxAge(exposedListCacheControl))
          .header(HEADER_X_KEY_BUNDLE_TAG, Long.toString(keyBundleTag.getTimestamp()))
          .build();
    }

    return ResponseEntity.ok()
        .cacheControl(CacheControl.maxAge(exposedListCacheControl))
        .header(HEADER_X_KEY_BUNDLE_TAG, Long.toString(keyBundleTag.getTimestamp()))
        .body(exposedKeysInternal.stream()
        		.map(k -> new GaenKeyInternalResponse(k))
        		.collect(Collectors.toList())
        		);
  }
  
  private List<String> defaultOriginCountry(List<String> countries) {
	if (null == countries || countries.size() == 0) {
    	countries = List.of(originCountry);
    }
	return countries;
  }

  private List<String> enforceOriginCountry(List<String> countries) {
	if (null == countries || countries.size() == 0) {
    	countries = List.of(originCountry);
    } else {
    	countries = countries.stream().map(c -> c.toUpperCase()).collect(Collectors.toList());
    	if (!countries.contains(originCountry)) {
    		countries.add(originCountry);
    	}
    }
	return countries;
  }

  @ExceptionHandler({
    IllegalArgumentException.class,
    InvalidDateException.class,
    JsonProcessingException.class,
    MethodArgumentNotValidException.class,
    BadBatchReleaseTimeException.class,
    DateTimeParseException.class,
    ClaimIsBeforeOnsetException.class,
    KeyFormatException.class
  })
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ResponseEntity<Object> invalidArguments() {
    return ResponseEntity.badRequest().build();
  }

  @ExceptionHandler({WrongScopeException.class})
  @ResponseStatus(HttpStatus.FORBIDDEN)
  public ResponseEntity<Object> forbidden() {
    return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
  }
}
