package org.dpppt.backend.sdk.ws.insertmanager.insertionmodifier;

import static org.junit.Assert.assertEquals;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.dpppt.backend.sdk.model.gaen.GaenKeyInternal;
import org.dpppt.backend.sdk.semver.Version;
import org.dpppt.backend.sdk.utils.UTCInstant;
import org.dpppt.backend.sdk.ws.insertmanager.OSType;
import org.junit.Test;
import org.springframework.security.oauth2.jwt.Jwt;

public class InteropKeyModifierTest {

	@Test
	public void testDaysSinceOnset() throws Exception {
		InteropKeyModifier ikm = new InteropKeyModifier(14, false, null, "CH");
		
	    var tmpKey = new GaenKeyInternal();
	    
	    tmpKey.setRollingStartNumber(
	        (int) UTCInstant.today().get10MinutesSince1970());
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
	        (int) UTCInstant.today().minus(Duration.ofDays(2)).get10MinutesSince1970());
	    tmpKey3.setKeyData(Base64.getEncoder().encodeToString("testKey32Bytes03".getBytes("UTF-8")));
	    tmpKey3.setRollingPeriod(144);
	    tmpKey3.setFake(0);
	    tmpKey3.setTransmissionRiskLevel(0);
	    tmpKey3.setOrigin("IT");
	    tmpKey3.setCountries(List.of("DE","CH"));
	    
	    List<GaenKeyInternal> keys = List.of(tmpKey, tmpKey2, tmpKey3);
	    
	    List<GaenKeyInternal> retKeys = ikm.modify(UTCInstant.now(), keys, null, OSType.ANDROID, new Version(1), new Version(1), jwt(LocalDate.now().minusDays(5)));
	    
	    assertEquals(retKeys.size(), 3);
	    assertEquals(retKeys.get(0).getDaysSinceOnsetOfSymptoms(), 5);
	    assertEquals(retKeys.get(1).getDaysSinceOnsetOfSymptoms(), 4);
	    assertEquals(retKeys.get(2).getDaysSinceOnsetOfSymptoms(), 3);
	    
	}
	
	private Jwt jwt(LocalDate onset) {

		final String AUTH0_TOKEN = "token";
	    final String SUB = "sub";
		final String AUTH0ID = "12345678";
	    final String ONSET = "onset";

		// This is a place to add general and maybe custom claims which should be available after parsing token in the live system
	    Map<String, Object> claims = Map.of(
	        SUB, AUTH0ID,
	        ONSET, onset.toString()
	    );

	    //This is an object that represents contents of jwt token after parsing
	    return new Jwt(
	        AUTH0_TOKEN,
	        Instant.now(),
	        Instant.now().plusSeconds(30),
	        Map.of("alg", "none"),
	        claims
	    );
	  }
	
}
