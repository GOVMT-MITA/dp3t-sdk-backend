package org.dpppt.backend.sdk.interops.syncer;

import java.time.LocalDate;

public interface SyncStateService {

	Integer getNextDownloadBatchNum(LocalDate dayDate);

	void setNextDownloadBatchNum(LocalDate dayDate, Integer batchNum);

	Long getLastUploadKeyBundleTag();
	
	void setLastUploadKeyBundleTag(Long lastUploadKeyBundleTag);

}
