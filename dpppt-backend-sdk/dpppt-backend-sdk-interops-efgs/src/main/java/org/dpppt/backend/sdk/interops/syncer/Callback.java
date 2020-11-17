package org.dpppt.backend.sdk.interops.syncer;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class Callback {

  private String callbackId;

  private String url;

}