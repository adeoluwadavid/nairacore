CREATE TABLE accounts.accounts (
                                   id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                   user_id UUID NOT NULL,
                                   account_number VARCHAR(10) NOT NULL UNIQUE,
                                   account_name VARCHAR(200) NOT NULL,
                                   account_type VARCHAR(20) NOT NULL,
                                   status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
                                   balance NUMERIC(19, 4) NOT NULL DEFAULT 0.0000,
                                   currency VARCHAR(3) NOT NULL DEFAULT 'NGN',
                                   created_at TIMESTAMP NOT NULL DEFAULT NOW(),
                                   updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE accounts.kyc_details (
                                      id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                      user_id UUID NOT NULL UNIQUE,
                                      bvn VARCHAR(11) NOT NULL,
                                      nin VARCHAR(11),
                                      date_of_birth DATE,
                                      address TEXT,
                                      city VARCHAR(100),
                                      state VARCHAR(100),
                                      id_type VARCHAR(20),
                                      id_number VARCHAR(50),
                                      id_expiry_date DATE,
                                      is_verified BOOLEAN NOT NULL DEFAULT FALSE,
                                      created_at TIMESTAMP NOT NULL DEFAULT NOW(),
                                      updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_accounts_user_id ON accounts.accounts(user_id);
CREATE INDEX idx_accounts_account_number ON accounts.accounts(account_number);
CREATE INDEX idx_kyc_user_id ON accounts.kyc_details(user_id);