-- =========================================================================
--  V4 : Add previous_contract_id link to support salary/date changes.
--  When a contract needs mid-flight changes (salary bump, backdate fix),
--  the old contract is ended and a new one is created with previous_contract_id
--  pointing to the old row. This preserves the legal/audit chain.
-- =========================================================================

ALTER TABLE contracts
    ADD COLUMN previous_contract_id BIGINT;

ALTER TABLE contracts
    ADD CONSTRAINT fk_contract_previous
    FOREIGN KEY (previous_contract_id) REFERENCES contracts(id) ON DELETE RESTRICT;

-- Optional: a contract can only be superseded by one successor.
CREATE UNIQUE INDEX uk_contract_previous_unique
    ON contracts(previous_contract_id) WHERE previous_contract_id IS NOT NULL;

CREATE INDEX idx_contracts_previous ON contracts(previous_contract_id);