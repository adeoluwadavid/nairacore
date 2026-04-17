CREATE TABLE notifications.notification_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    account_number VARCHAR(10),
    transaction_reference VARCHAR(20),
    type VARCHAR(30) NOT NULL,
    channel VARCHAR(10) NOT NULL DEFAULT 'EMAIL',
    recipient VARCHAR(150) NOT NULL,
    subject VARCHAR(255),
    message TEXT NOT NULL,
    status VARCHAR(10) NOT NULL DEFAULT 'PENDING',
    failure_reason VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_notifications_user_id
    ON notifications.notification_logs(user_id);

CREATE INDEX idx_notifications_transaction_ref
    ON notifications.notification_logs(transaction_reference);