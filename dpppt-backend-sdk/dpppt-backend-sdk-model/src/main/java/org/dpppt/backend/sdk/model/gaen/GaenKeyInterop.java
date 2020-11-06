package org.dpppt.backend.sdk.model.gaen;

import java.util.ArrayList;
import java.util.List;

public class GaenKeyInterop extends GaenKey {
	
	private List<String> regions;
	private int daysSinceOnsetOfSymptoms;

	public int getDaysSinceOnsetOfSymptoms() {
		return daysSinceOnsetOfSymptoms;
	}

	public void setDaysSinceOnsetOfSymptoms(int daysSinceOnsetOfSymptoms) {
		this.daysSinceOnsetOfSymptoms = daysSinceOnsetOfSymptoms;
	}

	public List<String> getRegions() {
		return regions;
	}

	public void setRegions(List<String> regions) {
		this.regions = regions;
	}

	public GaenKeyInterop() {
		super();
		regions = new ArrayList<>();
	}

	public GaenKeyInterop(String keyData, Integer rollingStartNumber, Integer rollingPeriod, List<String> regions) {
		super(keyData, rollingStartNumber, rollingPeriod);
		this.regions = regions;
	}
	

	
}
