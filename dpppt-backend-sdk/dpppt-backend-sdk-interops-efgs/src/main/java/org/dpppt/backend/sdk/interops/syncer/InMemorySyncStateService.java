package org.dpppt.backend.sdk.interops.syncer;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

public class InMemorySyncStateService implements SyncStateService {

	private Map<LocalDate, String> lastDownloadedBatchTagMap;
	private Long lastUploadKeyBundleTag = null;
	private CallbackSub callbackSub = null;

	public InMemorySyncStateService() {
		super();
		lastDownloadedBatchTagMap = new HashMap<>();
	}

	public String getLastDownloadedBatchTag(LocalDate dayDate) {
		return lastDownloadedBatchTagMap.get(dayDate);
	}

	public void setLastDownloadedBatchTag(LocalDate dayDate, String batchTag) {
		this.lastDownloadedBatchTagMap.put(dayDate, batchTag);
	}

	public Long getLastUploadKeyBundleTag() {
		return lastUploadKeyBundleTag;
	}

	public void setLastUploadKeyBundleTag(Long lastUploadKeyBundleTag) {
		this.lastUploadKeyBundleTag = lastUploadKeyBundleTag;
	}

	@Override
	public void saveCallback(String id, String url) {
		callbackSub = new CallbackSub(id, url);
	}

	@Override
	public CallbackSub getCallbackSub(String id) {
		return callbackSub;
	}

}
