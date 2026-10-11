CREATE TABLE push_subscriptions (id UUID PRIMARY KEY,company_id UUID NOT NULL,user_id UUID NOT NULL,endpoint VARCHAR(2048) NOT NULL UNIQUE,p256dh VARCHAR(512) NOT NULL,auth VARCHAR(256) NOT NULL);
CREATE INDEX idx_push_subscriptions_company ON push_subscriptions(company_id);
