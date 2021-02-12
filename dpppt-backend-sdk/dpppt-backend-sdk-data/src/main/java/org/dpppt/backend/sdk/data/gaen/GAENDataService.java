/*
 * Copyright (c) 2020 Ubique Innovation AG <https://www.ubique.ch>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

package org.dpppt.backend.sdk.data.gaen;

import java.time.Duration;
import java.util.List;
import org.dpppt.backend.sdk.model.gaen.GaenKey;
import org.dpppt.backend.sdk.model.gaen.GaenKeyInternal;
import org.dpppt.backend.sdk.utils.UTCInstant;

public interface GAENDataService {

  /**
   * Upserts (Update or Inserts) the given key received from interops synchronization.
   *
   * @param key the diagnosis key to upsert
   * @param now time of the sync
   * @param origin the origin or the key
   */
  void upsertExposee(
      GaenKeyInternal key, UTCInstant now);

  /**
   * Upserts (Update or Inserts) the given list of exposed keys
   *
   * @param keys the list of exposed keys to upsert
   * @param now time of the request
   */
  void upsertExposees(List<GaenKeyInternal> keys, UTCInstant now);

  /**
   * Upserts (Update or Inserts) the given list of exposed keys, with delayed release of same day
   * TEKs
   *
   * @param keys the list of exposed keys to upsert
   * @param delayedReceivedAt the timestamp to use for the delayed release (if null use now rounded
   *     to next bucket)
   * @param now time of the request
   */
  void upsertExposeesDelayed(
      List<GaenKeyInternal> keys, UTCInstant delayedReceivedAt, UTCInstant now);

  /**
   * Returns all exposeed keys for the given batch, where a batch is parametrized with keyDate (for
   * which day was the key used) publishedAfter/publishedUntil (when was the key published) and now
   * (has the key expired or not, based on rollingStartNumber and rollingPeriod).
   *
   * @param keyDate must be midnight UTC
   * @param publishedAfter when publication should start
   * @param publishedUntil last publication
   * @param now the start of the query
   * @param international return keys from all countries of origin
   * @return all exposeed keys for the given batch
   */
  List<GaenKeyInternal> getSortedExposedForKeyDate(
      UTCInstant keyDate, UTCInstant publishedAfter, UTCInstant publishedUntil, UTCInstant now, boolean international);

  /**
   * deletes entries older than retentionperiod
   *
   * @param retentionPeriod in milliseconds
   */
  void cleanDB(Duration retentionPeriod);

  /**
   * Returns all exposed keys since keySince. It will always include the keys of the origin country.
   *
   * @param keysSince
   * @param now
   * @param countries List of countries to retrieve. Otherwise only keys from the origin country.
   * @return
   */
  List<GaenKeyInternal> getSortedExposedSince(
      UTCInstant keysSince, UTCInstant now, List<String> countries);

  /**
   * Returns all exposed keys since keySince. It will always include the keys of the origin country.
   *
   * @param keysSince
   * @param now
   * @param international
   * @return
   */
  List<GaenKeyInternal> getSortedExposedSince(
      UTCInstant keysSince, UTCInstant now, boolean international);

  /**
   * Returns all exposed keys since keySince for the given origin country only.
   *
   * @param keysSince
   * @param now
   * @param origin Origin of keys to retrieve.
   * @return
   */
  List<GaenKeyInternal> getSortedExposedSince(
      UTCInstant keysSince, UTCInstant now, String origin);

  /**
   * Returns all exposed keys since keySince for the given origin countries only.
   *
   * @param keysSince
   * @param now
   * @param origin Origin of keys to retrieve.
   * @return
   */
  List<GaenKeyInternal> getSortedExposedSinceForOrigins(
      UTCInstant keysSince, UTCInstant now, List<String> origins);
  
  
  /**
   * Marks the given exposed keys as uploaded by setting their upload batch tag.
   *
   * @param gaenKeys Exposed keys to mark
   * @param batchTag Upload batch tag
   * @return
   */
  void markUploaded(List<GaenKeyInternal> gaenKeys, String batchTag);

  /**
   * Returns true if the given batch tag exists, otherwise return false
   *
   * @param batchTag batch tag
   * @return
   */
  long efgsBatchExists(String batchTag);
  
}
