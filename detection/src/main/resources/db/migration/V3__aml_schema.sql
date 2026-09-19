ALTER TABLE user_roles DROP CONSTRAINT user_roles_role_check;
ALTER TABLE user_roles ADD CONSTRAINT user_roles_role_check CHECK (role IN ('ROLE_USER','ROLE_ADMIN','ROLE_ANALYST','ROLE_INGESTOR'));
CREATE TABLE customers (
 id varchar(64) PRIMARY KEY, first_name varchar(100) NOT NULL, last_name varchar(100) NOT NULL,
 date_of_birth date, email varchar(254), phone_number varchar(32), city varchar(100), state varchar(100),
 country varchar(2) NOT NULL, postal_code varchar(20), occupation varchar(100), annual_income numeric(24,2),
 customer_since date NOT NULL, customer_segment varchar(40), kyc_status varchar(30) NOT NULL,
 risk_rating varchar(10) NOT NULL CHECK(risk_rating IN ('LOW','MEDIUM','HIGH')),
 politically_exposed boolean NOT NULL DEFAULT false
);
CREATE TABLE accounts (
 id varchar(64) PRIMARY KEY, customer_id varchar(64) NOT NULL REFERENCES customers(id),
 account_type varchar(30) NOT NULL, account_status varchar(20) NOT NULL CHECK(account_status IN ('ACTIVE','INACTIVE','DORMANT','FROZEN','CLOSED')),
 currency varchar(3) NOT NULL, open_date date NOT NULL, close_date date, branch_code varchar(30), branch_city varchar(100),
 current_balance numeric(24,2) NOT NULL DEFAULT 0, risk_rating varchar(10) NOT NULL CHECK(risk_rating IN ('LOW','MEDIUM','HIGH')),
 CHECK(close_date IS NULL OR close_date >= open_date)
);
CREATE INDEX accounts_customer_idx ON accounts(customer_id);
CREATE TABLE exchange_rates (
 currency varchar(3) PRIMARY KEY, inr_per_unit numeric(24,8) NOT NULL CHECK(inr_per_unit > 0), updated_at timestamptz NOT NULL DEFAULT now()
);
-- Demonstration rates only. Configure operational rates before loading transactions.
INSERT INTO exchange_rates(currency,inr_per_unit) VALUES ('INR',1),('USD',83),('EUR',90),('GBP',105);
CREATE TABLE transactions (
 id varchar(64) PRIMARY KEY, account_id varchar(64) NOT NULL REFERENCES accounts(id), amount numeric(24,2) NOT NULL CHECK(amount > 0),
 currency varchar(3) NOT NULL REFERENCES exchange_rates(currency), exchange_rate numeric(24,8) NOT NULL CHECK(exchange_rate > 0),
 base_amount numeric(24,2) NOT NULL CHECK(base_amount > 0), usd_amount numeric(24,2) NOT NULL CHECK(usd_amount > 0),
 direction varchar(3) NOT NULL CHECK(direction IN ('IN','OUT')), counterparty varchar(200) NOT NULL,
 channel varchar(30) NOT NULL, occurred_at timestamptz NOT NULL, jurisdiction varchar(2) NOT NULL, ingested_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX transactions_account_time_idx ON transactions(account_id,occurred_at);
CREATE TABLE detection_rules (
 code varchar(40) PRIMARY KEY, enabled boolean NOT NULL DEFAULT true, threshold numeric(24,4) NOT NULL CHECK(threshold > 0),
 secondary_threshold numeric(24,4) NOT NULL CHECK(secondary_threshold > 0), window_hours integer NOT NULL CHECK(window_hours BETWEEN 1 AND 2160),
 minimum_count integer NOT NULL CHECK(minimum_count >= 1), weight integer NOT NULL CHECK(weight BETWEEN 1 AND 100)
);
INSERT INTO detection_rules VALUES
 ('THRESHOLD',true,10000,1,24,1,40),
 ('STRUCTURING',true,9000,10000,24,3,35),
 ('RAPID_MOVEMENT',true,0.8,1,48,1,40),
 ('HIGH_RISK',true,1,1,24,1,60),
 ('BEHAVIORAL',true,3,1,2160,1,30),
 ('ROUND_NUMBER',true,1000,1,24,3,20);
CREATE TABLE risk_watchlist (
 kind varchar(20) NOT NULL CHECK(kind IN ('JURISDICTION','COUNTERPARTY')), value varchar(200) NOT NULL, enabled boolean NOT NULL DEFAULT true,
 PRIMARY KEY(kind,value)
);
CREATE TABLE alerts (
 id bigserial PRIMARY KEY, customer_id varchar(64) NOT NULL REFERENCES customers(id),
 status varchar(20) NOT NULL CHECK(status IN ('OPEN','IN_REVIEW','CLEARED','ESCALATED')),
 risk_score integer NOT NULL CHECK(risk_score BETWEEN 0 AND 100), explanation text NOT NULL,
 disposition_reason varchar(2000), analyst varchar(20) REFERENCES users(username), created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL
);
CREATE UNIQUE INDEX alerts_one_active_customer_idx ON alerts(customer_id) WHERE status IN ('OPEN','IN_REVIEW');
CREATE INDEX alerts_queue_idx ON alerts(status,risk_score DESC,created_at);
CREATE TABLE alert_rules (
 alert_id bigint NOT NULL REFERENCES alerts(id), rule_code varchar(40) NOT NULL REFERENCES detection_rules(code),
 weight integer NOT NULL, explanation text NOT NULL, configuration text NOT NULL, PRIMARY KEY(alert_id,rule_code)
);
CREATE TABLE alert_evidence (
 alert_id bigint NOT NULL REFERENCES alerts(id), transaction_id varchar(64) NOT NULL REFERENCES transactions(id),
 PRIMARY KEY(alert_id,transaction_id)
);
CREATE TABLE investigation_cases (
 id bigserial PRIMARY KEY, customer_id varchar(64) NOT NULL REFERENCES customers(id), title varchar(200) NOT NULL,
 status varchar(20) NOT NULL CHECK(status IN ('OPEN','IN_PROGRESS','CLOSED')), assigned_to varchar(20) REFERENCES users(username),
 disposition_reason varchar(2000), created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL
);
CREATE TABLE case_alerts (
 case_id bigint NOT NULL REFERENCES investigation_cases(id), alert_id bigint NOT NULL UNIQUE REFERENCES alerts(id), PRIMARY KEY(case_id,alert_id)
);
CREATE TABLE audit_events (
 id bigserial PRIMARY KEY, entity_type varchar(30) NOT NULL, entity_id varchar(64) NOT NULL,
 previous_state varchar(30), new_state varchar(30) NOT NULL, reason varchar(2000) NOT NULL,
 actor varchar(100) NOT NULL, occurred_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX audit_entity_idx ON audit_events(entity_type,entity_id,id);
CREATE FUNCTION forbid_audit_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'Audit records are immutable'; END; $$;
CREATE TRIGGER audit_immutable BEFORE UPDATE OR DELETE OR TRUNCATE ON audit_events FOR EACH STATEMENT EXECUTE FUNCTION forbid_audit_mutation();
CREATE FUNCTION forbid_alert_delete() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'Alerts must be retained'; END; $$;
CREATE TRIGGER alerts_retained BEFORE DELETE OR TRUNCATE ON alerts FOR EACH STATEMENT EXECUTE FUNCTION forbid_alert_delete();
