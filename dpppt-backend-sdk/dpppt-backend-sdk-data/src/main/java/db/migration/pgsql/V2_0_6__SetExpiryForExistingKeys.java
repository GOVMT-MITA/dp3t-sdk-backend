/*
 * Copyright (c) 2020 Ubique Innovation AG <https://www.ubique.ch>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

package db.migration.pgsql;

import java.sql.PreparedStatement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public class V2_0_6__SetExpiryForExistingKeys extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {

    // Update all existing keys with no origin to the given origin country
    try (PreparedStatement update =
        context
            .getConnection()
            .prepareStatement("update t_gaen_exposed set expires_at = TO_TIMESTAMP((rolling_start_number + rolling_period) * 10 * 60 + ?)")) {
      update.setInt(1, 7200);
      update.execute();
    }
  }
}
