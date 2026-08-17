-- Migration: monthly_quota_and_billing_engine.sql
-- Tiered smart monthly quotas for Zad (Starter, Plus, Pro) + central enforcement RPCs

-- 1. Add Tier and Quota columns to zad_users and profiles
ALTER TABLE public.zad_users
ADD COLUMN IF NOT EXISTS tier TEXT NOT NULL DEFAULT 'free',
ADD COLUMN IF NOT EXISTS brain_queries_left INT NOT NULL DEFAULT 1,
ADD COLUMN IF NOT EXISTS receipt_scans_left INT NOT NULL DEFAULT 5,
ADD COLUMN IF NOT EXISTS billing_cycle_reset_at TIMESTAMPTZ NOT NULL DEFAULT (now() + interval '1 month'),
ADD COLUMN IF NOT EXISTS subscription_status TEXT NOT NULL DEFAULT 'none',
ADD COLUMN IF NOT EXISTS subscription_expires_at TIMESTAMPTZ;

DO $$ 
BEGIN
    IF EXISTS (SELECT FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'profiles') THEN
        ALTER TABLE public.profiles
        ADD COLUMN IF NOT EXISTS tier TEXT DEFAULT 'free',
        ADD COLUMN IF NOT EXISTS brain_queries_left INT DEFAULT 1,
        ADD COLUMN IF NOT EXISTS receipt_scans_left INT DEFAULT 5,
        ADD COLUMN IF NOT EXISTS billing_cycle_reset_at TIMESTAMPTZ DEFAULT (now() + interval '1 month'),
        ADD COLUMN IF NOT EXISTS subscription_status TEXT DEFAULT 'none',
        ADD COLUMN IF NOT EXISTS subscription_expires_at TIMESTAMPTZ;
    END IF;
END $$;

-- 2. Subscriptions management table
CREATE TABLE IF NOT EXISTS public.subscriptions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE,
    tier TEXT NOT NULL DEFAULT 'free', -- 'starter', 'plus', 'pro'
    provider TEXT NOT NULL DEFAULT 'google_play', -- 'google_play', 'stripe', 'paymob'
    provider_subscription_id TEXT,
    status TEXT NOT NULL DEFAULT 'active', -- 'active', 'canceled', 'expired', 'past_due'
    current_period_start TIMESTAMPTZ DEFAULT now(),
    current_period_end TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ DEFAULT now(),
    updated_at TIMESTAMPTZ DEFAULT now()
);

ALTER TABLE public.subscriptions ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Users can view their own subscriptions"
ON public.subscriptions FOR SELECT
TO authenticated
USING (auth.uid() = user_id);

-- 3. Monthly subscription quota reset function
CREATE OR REPLACE FUNCTION public.reset_monthly_subscription_quotas()
RETURNS VOID AS $$
BEGIN
    UPDATE public.zad_users
    SET 
        brain_queries_left = CASE 
            WHEN tier = 'starter' THEN 15
            WHEN tier = 'plus' THEN 50
            WHEN tier = 'pro' THEN 150
            ELSE 1
        END,
        receipt_scans_left = CASE 
            WHEN tier = 'starter' THEN 30
            WHEN tier = 'plus' THEN 100
            WHEN tier = 'pro' THEN 300
            ELSE 5
        END,
        billing_cycle_reset_at = now() + interval '1 month',
        updated_at = now()
    WHERE billing_cycle_reset_at <= now();

    -- Expire subscriptions that passed current_period_end
    UPDATE public.zad_users
    SET tier = 'free',
        subscription_status = 'expired'
    WHERE subscription_expires_at IS NOT NULL 
      AND subscription_expires_at < now()
      AND tier != 'free';
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- 4. Central Quota Consume RPC (App + Telegram)
CREATE OR REPLACE FUNCTION public.zad_quota_consume(
    p_user_id UUID,
    p_service TEXT -- 'brain' OR 'receipt' OR 'voice'
)
RETURNS JSONB AS $$
DECLARE
    v_tier TEXT;
    v_left INT;
    v_unlimited BOOLEAN := false;
BEGIN
    SELECT tier, 
           CASE 
             WHEN p_service = 'brain' THEN brain_queries_left 
             WHEN p_service = 'receipt' THEN receipt_scans_left
             ELSE 1
           END
    INTO v_tier, v_left
    FROM public.zad_users
    WHERE id = p_user_id;

    IF NOT FOUND THEN
        RETURN jsonb_build_object('allowed', false, 'reason', 'user_not_found');
    END IF;

    -- Pro tier has unlimited fast chats
    IF v_tier = 'pro' AND p_service = 'chat' THEN
        RETURN jsonb_build_object('allowed', true, 'remaining', 9999);
    END IF;

    IF v_left > 0 THEN
        IF p_service = 'brain' THEN
            UPDATE public.zad_users 
            SET brain_queries_left = GREATEST(0, brain_queries_left - 1) 
            WHERE id = p_user_id;
            v_left := v_left - 1;
        ELSIF p_service = 'receipt' THEN
            UPDATE public.zad_users 
            SET receipt_scans_left = GREATEST(0, receipt_scans_left - 1) 
            WHERE id = p_user_id;
            v_left := v_left - 1;
        END IF;

        RETURN jsonb_build_object('allowed', true, 'remaining', v_left, 'tier', v_tier);
    END IF;

    RETURN jsonb_build_object('allowed', false, 'reason', 'quota_exhausted', 'tier', v_tier);
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;
