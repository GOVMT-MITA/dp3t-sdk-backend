package org.dpppt.backend.sdk.ws.insertmanager.insertionmodifier;

import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.List;
import org.dpppt.backend.sdk.model.gaen.GaenKey;
import org.dpppt.backend.sdk.model.gaen.GaenKeyInternal;
import org.dpppt.backend.sdk.semver.Version;
import org.dpppt.backend.sdk.utils.UTCInstant;
import org.dpppt.backend.sdk.ws.insertmanager.OSType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.jwt.Jwt;

import com.google.common.collect.Lists;

/**
 * Some early builds of Google's Exposure Notification API returned TEKs with rolling period set to
 * '0'. According to the specification, this is invalid and will cause both Android and iOS to
 * drop/ignore the key. To mitigate ignoring TEKs from these builds altogether, the rolling period
 * is increased to '144' (one full day). This should not happen anymore and can be removed in the
 * near future. Until then we are going to log whenever this happens to be able to monitor this
 * problem.
 */
public class InteropKeyModifier implements KeyInsertionModifier {

  private static final Logger logger = LoggerFactory.getLogger(InteropKeyModifier.class);

  private final int retentionDays;
  private final boolean interopEnabled;
  private final List<String> otherCountries;
  private final String originCountry;
  

public InteropKeyModifier(int retentionDays, boolean interopEnabled, List<String> otherCountries, String originCountry) {
	super();
	this.retentionDays = retentionDays;
	this.interopEnabled = interopEnabled;
	this.otherCountries = otherCountries;
	this.originCountry = originCountry;
}


  @Override
  public List<GaenKeyInternal> modify(
      UTCInstant now,
      List<GaenKeyInternal> content,
      List<String> countries,
      OSType osType,
      Version osVersion,
      Version appVersion,
      Object principal) {
	
	var onsetDate = UTCInstant.now().minusDays(retentionDays);
	if (principal instanceof Jwt) {
	  Jwt token = (Jwt) principal;
	  onsetDate = UTCInstant.parseDate(token.getClaim("onset"));
    }

	for (GaenKeyInternal gaenKey : content) {		
		var keyDate = UTCInstant.of(gaenKey.getRollingStartNumber() * 10, ChronoUnit.MINUTES);
		long daysSinceOnsetOfSymptoms = ChronoUnit.DAYS.between(onsetDate.getInstant(), keyDate.getInstant());
		gaenKey.setDaysSinceOnsetOfSymptoms((int) daysSinceOnsetOfSymptoms);		
		if (null == countries) {
			countries = Lists.newArrayList();
		}
		if (countries.isEmpty() && interopEnabled) {
	    	countries.addAll(otherCountries);
		}		
		gaenKey.setCountries(countries);
		gaenKey.setOrigin(originCountry);
	}
    
	return content;
  }
}
