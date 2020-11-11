package org.dpppt.backend.sdk.model.gaen;

import java.util.ArrayList;
import java.util.List;

public class GaenKeyInterop extends GaenKey {
	
	private List<String> visitedCountries;
	private int daysSinceOnsetOfSymptoms;

	public int getDaysSinceOnsetOfSymptoms() {
		return daysSinceOnsetOfSymptoms;
	}

	public void setDaysSinceOnsetOfSymptoms(int daysSinceOnsetOfSymptoms) {
		this.daysSinceOnsetOfSymptoms = daysSinceOnsetOfSymptoms;
	}

	public GaenKeyInterop() {
		super();
		visitedCountries = new ArrayList<>();
	}

	public GaenKeyInterop(String keyData, Integer rollingStartNumber, Integer rollingPeriod, List<String> visitedCountries) {
		super(keyData, rollingStartNumber, rollingPeriod);
		this.visitedCountries = visitedCountries;
	}

	public List<String> getVisitedCountries() {
		return visitedCountries;
	}

	public void setVisitedCountries(List<String> visitedCountries) {
		this.visitedCountries = visitedCountries;
	}
	
	

	
}
