SET TIME ZONE 'UTC';

CREATE TABLE users (
                       id UUID PRIMARY KEY,
                       email VARCHAR(255) NOT NULL UNIQUE,
                       full_name VARCHAR(255) NOT NULL,
                       status VARCHAR(20) NOT NULL,

                       created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT (CURRENT_TIMESTAMP AT TIME ZONE 'UTC'),
                       updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT (CURRENT_TIMESTAMP AT TIME ZONE 'UTC')
);

CREATE TABLE accounts (
                          id UUID PRIMARY KEY,
                          owner_id UUID NOT NULL,
                          balance_cents BIGINT NOT NULL,
                          currency VARCHAR(3) NOT NULL,
                          status VARCHAR(10) NOT NULL,
                          version BIGINT,

                          created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT (CURRENT_TIMESTAMP AT TIME ZONE 'UTC'),
                          updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT (CURRENT_TIMESTAMP AT TIME ZONE 'UTC'),

                          CONSTRAINT fk_account_owner FOREIGN KEY (owner_id) REFERENCES users (id)
);

CREATE TABLE integration_failures (
                                      id UUID PRIMARY KEY,
                                      context VARCHAR(100) NOT NULL,
                                      entity_name VARCHAR(100),
                                      related_id UUID,
                                      message VARCHAR(1000) NOT NULL,
                                      payload TEXT,
                                      retry_count INT NOT NULL DEFAULT 0,
                                      occurred_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT (CURRENT_TIMESTAMP AT TIME ZONE 'UTC')
);

CREATE TABLE payment_orders (
                                id UUID PRIMARY KEY,
                                idempotency_key VARCHAR(255) NOT NULL UNIQUE,
                                initiated_by_user_id UUID NOT NULL,
                                source_account_id UUID NOT NULL,
                                total_amount_cents BIGINT NOT NULL,
                                currency VARCHAR(3) NOT NULL,
                                status VARCHAR(20) NOT NULL,

                                created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT (CURRENT_TIMESTAMP AT TIME ZONE 'UTC'),
                                updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT (CURRENT_TIMESTAMP AT TIME ZONE 'UTC')
);

CREATE TABLE payment_order_items (
                                     id UUID PRIMARY KEY,
                                     payment_order_id UUID NOT NULL,
                                     destination_account_id UUID NOT NULL,
                                     amount_cents BIGINT NOT NULL,
                                     status VARCHAR(20) NOT NULL,
                                     order_key VARCHAR(255) NOT NULL UNIQUE,
                                     failure_reason VARCHAR(500),

                                     created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT (CURRENT_TIMESTAMP AT TIME ZONE 'UTC'),
                                     updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT (CURRENT_TIMESTAMP AT TIME ZONE 'UTC'),

                                     CONSTRAINT fk_paymentitem_order FOREIGN KEY (payment_order_id) REFERENCES payment_orders (id)
);

CREATE TABLE transactions (
                              id UUID PRIMARY KEY,
                              source_account_id UUID NOT NULL,
                              destination_account_id UUID NOT NULL,
                              amount_cents BIGINT NOT NULL,
                              currency VARCHAR(3) NOT NULL,
                              payment_order_id UUID NOT NULL,
                              payment_order_item_id UUID NOT NULL,
                              idempotency_key VARCHAR(255) NOT NULL,

                              created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT (CURRENT_TIMESTAMP AT TIME ZONE 'UTC'),
                              updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT (CURRENT_TIMESTAMP AT TIME ZONE 'UTC')
);

CREATE INDEX idx_transaction_source_acc ON transactions (source_account_id);
CREATE INDEX idx_transaction_dest_acc ON transactions (destination_account_id);
CREATE INDEX idx_transaction_order_key ON transactions (idempotency_key);