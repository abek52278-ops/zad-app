-- =====================================================
-- CREATE: Amazon Affiliate tables (missing from schema — app code
-- queries these directly via postgrest but no migration defined them)
-- Run this in Supabase SQL Editor (Dashboard > SQL Editor)
-- =====================================================

-- 1. affiliate_products — الكتالوج (يديره الأدمن فقط، القراءة عامة للمستخدمين المسجلين)
CREATE TABLE IF NOT EXISTS affiliate_products (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    product_name_ar TEXT NOT NULL,
    product_name_search_keywords TEXT[] NOT NULL DEFAULT '{}',
    category TEXT,
    asin TEXT NOT NULL,
    image_url TEXT,
    average_price_sar DOUBLE PRECISION NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

ALTER TABLE affiliate_products ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "affiliate_products_select" ON affiliate_products;
CREATE POLICY "affiliate_products_select"
    ON affiliate_products FOR SELECT
    USING (auth.role() = 'authenticated');

-- الكتابة محصورة على service_role (Edge Functions / لوحة تحكم الأدمن) — لا توجد سياسة INSERT/UPDATE للمستخدمين العاديين.

-- 2. affiliate_clicks — تتبع ضغطات الشراء لكل مستخدم
CREATE TABLE IF NOT EXISTS affiliate_clicks (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    product_id UUID NOT NULL REFERENCES affiliate_products(id) ON DELETE CASCADE,
    user_id UUID REFERENCES auth.users(id) ON DELETE SET NULL,
    source_screen TEXT NOT NULL DEFAULT 'shopping',
    clicked_at TIMESTAMPTZ DEFAULT NOW()
);

ALTER TABLE affiliate_clicks ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "affiliate_clicks_select_own" ON affiliate_clicks;
CREATE POLICY "affiliate_clicks_select_own"
    ON affiliate_clicks FOR SELECT
    USING (auth.uid() = user_id);

DROP POLICY IF EXISTS "affiliate_clicks_insert_own" ON affiliate_clicks;
CREATE POLICY "affiliate_clicks_insert_own"
    ON affiliate_clicks FOR INSERT
    WITH CHECK (auth.uid() = user_id OR user_id IS NULL);

-- 3. affiliate_catalog_requests — طلبات منتجات مش موجودة في الكتالوج (بحث المستخدم رجع بدون نتيجة)
CREATE TABLE IF NOT EXISTS affiliate_catalog_requests (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    searched_term TEXT NOT NULL,
    user_id UUID REFERENCES auth.users(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

ALTER TABLE affiliate_catalog_requests ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "affiliate_catalog_requests_insert" ON affiliate_catalog_requests;
CREATE POLICY "affiliate_catalog_requests_insert"
    ON affiliate_catalog_requests FOR INSERT
    WITH CHECK (auth.uid() = user_id OR user_id IS NULL);

DROP POLICY IF EXISTS "affiliate_catalog_requests_select_own" ON affiliate_catalog_requests;
CREATE POLICY "affiliate_catalog_requests_select_own"
    ON affiliate_catalog_requests FOR SELECT
    USING (auth.uid() = user_id);

-- 4. Indexes مفيدة
CREATE INDEX IF NOT EXISTS idx_affiliate_products_active ON affiliate_products(is_active);
CREATE INDEX IF NOT EXISTS idx_affiliate_clicks_product ON affiliate_clicks(product_id);
CREATE INDEX IF NOT EXISTS idx_affiliate_clicks_user ON affiliate_clicks(user_id);

-- DONE! جداول أفيليت أمازون جاهزة بـ RLS.
-- ملاحظة: علشان تضيف منتجات للكتالوج، استخدم service_role key (مش anon key) من Supabase Dashboard
-- أو من Edge Function بصلاحيات أدمن — الـ RLS هنا يمنع أي مستخدم عادي من الكتابة في affiliate_products.
