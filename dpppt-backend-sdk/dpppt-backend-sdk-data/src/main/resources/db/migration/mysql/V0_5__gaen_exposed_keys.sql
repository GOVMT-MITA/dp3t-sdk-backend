/*
 * Created by Malta Information Technology Agency
 * https://mita.gov.mt
 * Copyright (c) 2021. All rights reserved.
 */

CREATE TABLE t_gaen_exposed(
 pk_exposed_id SERIAL,
 `key` VARCHAR(24) NOT NULL,
 rolling_start_number INT NOT NULL,
 rolling_period INT NOT NULL,
 transmission_risk_level INT NOT NULL,
 received_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Add keys for table t_gaen_exposed

ALTER TABLE t_gaen_exposed ADD CONSTRAINT PK_t_gaen_exposed PRIMARY KEY (pk_exposed_id);

ALTER TABLE t_gaen_exposed ADD CONSTRAINT gaen_exposed_key UNIQUE (`key`);