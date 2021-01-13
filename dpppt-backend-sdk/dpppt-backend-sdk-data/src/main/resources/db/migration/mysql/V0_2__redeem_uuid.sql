/*
 * Created by Malta Information Technology Agency
 * https://mita.gov.mt
 * Copyright (c) 2021. All rights reserved.
 */

CREATE TABLE t_redeem_uuid(
 pk_redeem_uuid_id Serial,
 uuid VARCHAR(50) NOT NULL,
 received_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Add keys for table t_redeem_uuid

ALTER TABLE t_redeem_uuid ADD CONSTRAINT PK_t_redeem_uuid PRIMARY KEY (pk_redeem_uuid_id);

ALTER TABLE t_redeem_uuid ADD CONSTRAINT uuid UNIQUE (uuid);