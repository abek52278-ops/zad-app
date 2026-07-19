-- تحديثات قاعدة البيانات لذكاء زاد

-- 0. جداول العائلة الأساسية (إذا لم تكن موجودة)
CREATE TABLE IF NOT EXISTS family_groups (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    invite_code TEXT UNIQUE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now())
);

ALTER TABLE family_groups ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Allow authenticated users to read family groups" ON family_groups FOR SELECT USING (auth.role() = 'authenticated');
CREATE POLICY "Allow authenticated users to create family groups" ON family_groups FOR INSERT WITH CHECK (auth.role() = 'authenticated');

CREATE TABLE IF NOT EXISTS family_members (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    family_id UUID NOT NULL REFERENCES family_groups(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    zad_id TEXT,
    role TEXT NOT NULL DEFAULT 'member',
    alias TEXT NOT NULL DEFAULT '',
    balance NUMERIC NOT NULL DEFAULT 0,
    savings_goal NUMERIC NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now())
);

ALTER TABLE family_members ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Allow members to read their family" ON family_members FOR SELECT USING (family_id IN (SELECT family_id FROM family_members WHERE user_id = auth.uid()));
CREATE POLICY "Allow authenticated users to join families" ON family_members FOR INSERT WITH CHECK (auth.role() = 'authenticated');
CREATE POLICY "Allow admins to update members" ON family_members FOR UPDATE USING (family_id IN (SELECT family_id FROM family_members WHERE user_id = auth.uid() AND role = 'admin'));
CREATE POLICY "Allow admins to delete members" ON family_members FOR DELETE USING (family_id IN (SELECT family_id FROM family_members WHERE user_id = auth.uid() AND role = 'admin'));

-- 1. إضافة جدول المهام (Chores)
CREATE TABLE IF NOT EXISTS family_chores (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    family_id UUID NOT NULL REFERENCES family_groups(id) ON DELETE CASCADE,
    assigned_to UUID NOT NULL,
    title TEXT NOT NULL,
    due_date TEXT,
    reward_amount NUMERIC NOT NULL DEFAULT 0,
    is_completed BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now())
);

-- سياسات الأمان (RLS) لجدول المهام
ALTER TABLE family_chores ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Allow members to read chores for their family"
    ON family_chores FOR SELECT
    USING (
        family_id IN (
            SELECT family_id FROM family_members WHERE user_id = auth.uid()
        )
    );

CREATE POLICY "Allow admins to insert chores"
    ON family_chores FOR INSERT
    WITH CHECK (
        family_id IN (
            SELECT family_id FROM family_members WHERE user_id = auth.uid() AND role = 'admin'
        )
    );

CREATE POLICY "Allow members to update chore status"
    ON family_chores FOR UPDATE
    USING (
        family_id IN (
            SELECT family_id FROM family_members WHERE user_id = auth.uid()
        )
    );

CREATE POLICY "Allow admins to delete chores"
    ON family_chores FOR DELETE
    USING (
        family_id IN (
            SELECT family_id FROM family_members WHERE user_id = auth.uid() AND role = 'admin'
        )
    );

-- Phase D: Family Garden Features

-- 2. جدول شجرة التسبيح (family_tasbiha)
CREATE TABLE IF NOT EXISTS family_tasbiha (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    family_id UUID NOT NULL REFERENCES family_groups(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    tree_name TEXT NOT NULL DEFAULT 'بذرة',
    level INTEGER NOT NULL DEFAULT 1,
    score INTEGER NOT NULL DEFAULT 0,
    total_clicks INTEGER NOT NULL DEFAULT 0,
    last_tasbih_at TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now())
);

ALTER TABLE family_tasbiha ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Allow members to view family tasbiha" ON family_tasbiha FOR SELECT USING (family_id IN (SELECT family_id FROM family_members WHERE user_id = auth.uid()));
CREATE POLICY "Allow members to update family tasbiha" ON family_tasbiha FOR UPDATE USING (family_id IN (SELECT family_id FROM family_members WHERE user_id = auth.uid()));
CREATE POLICY "Allow members to insert tasbiha" ON family_tasbiha FOR INSERT WITH CHECK (family_id IN (SELECT family_id FROM family_members WHERE user_id = auth.uid()));

-- 3. جدول الرسائل اللحظية (chat_messages)
CREATE TABLE IF NOT EXISTS chat_messages (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    family_id UUID NOT NULL REFERENCES family_groups(id) ON DELETE CASCADE,
    sender_id TEXT NOT NULL,
    message TEXT NOT NULL,
    message_type TEXT NOT NULL DEFAULT 'TEXT',
    metadata TEXT,
    is_pinned BOOLEAN DEFAULT FALSE,
    reactions TEXT,
    voice_url TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now())
);

ALTER TABLE chat_messages ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Allow members to view chat" ON chat_messages FOR SELECT USING (family_id IN (SELECT family_id FROM family_members WHERE user_id = auth.uid()));
CREATE POLICY "Allow members to insert chat" ON chat_messages FOR INSERT WITH CHECK (family_id IN (SELECT family_id FROM family_members WHERE user_id = auth.uid()));
CREATE POLICY "Allow members to update chat" ON chat_messages FOR UPDATE USING (family_id IN (SELECT family_id FROM family_members WHERE user_id = auth.uid()));

-- تفعيل Realtime لجدول الرسائل اللحظية
ALTER PUBLICATION supabase_realtime ADD TABLE chat_messages;

-- 4. جدول الأهداف العائلية (family_goals)
CREATE TABLE IF NOT EXISTS family_goals (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    family_id UUID NOT NULL REFERENCES family_groups(id) ON DELETE CASCADE,
    target_amount NUMERIC NOT NULL DEFAULT 0,
    current_amount NUMERIC NOT NULL DEFAULT 0,
    month_year TEXT NOT NULL,
    reward_suggestion TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now())
);

ALTER TABLE family_goals ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Allow members to view goals" ON family_goals FOR SELECT USING (family_id IN (SELECT family_id FROM family_members WHERE user_id = auth.uid()));
CREATE POLICY "Allow admins to modify goals" ON family_goals FOR ALL USING (family_id IN (SELECT family_id FROM family_members WHERE user_id = auth.uid() AND role = 'admin'));

-- 5. جدول قائمة التسوق المشتركة
CREATE TABLE IF NOT EXISTS shared_grocery_list (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    family_id UUID NOT NULL REFERENCES family_groups(id) ON DELETE CASCADE,
    added_by UUID NOT NULL,
    item_name TEXT NOT NULL,
    category TEXT,
    is_purchased BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now())
);

ALTER TABLE shared_grocery_list ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Allow members to view grocery list" ON shared_grocery_list FOR SELECT USING (family_id IN (SELECT family_id FROM family_members WHERE user_id = auth.uid()));
CREATE POLICY "Allow members to insert grocery items" ON shared_grocery_list FOR INSERT WITH CHECK (family_id IN (SELECT family_id FROM family_members WHERE user_id = auth.uid()));
CREATE POLICY "Allow members to update grocery items" ON shared_grocery_list FOR UPDATE USING (family_id IN (SELECT family_id FROM family_members WHERE user_id = auth.uid()));
CREATE POLICY "Allow members to delete grocery items" ON shared_grocery_list FOR DELETE USING (family_id IN (SELECT family_id FROM family_members WHERE user_id = auth.uid()));

-- 6. جدول الإشعارات
CREATE TABLE IF NOT EXISTS app_notifications (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    title TEXT NOT NULL,
    message TEXT NOT NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now())
);

ALTER TABLE app_notifications ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Allow users to read their notifications" ON app_notifications FOR SELECT USING (user_id = auth.uid());
CREATE POLICY "Allow authenticated users to insert notifications" ON app_notifications FOR INSERT WITH CHECK (auth.role() = 'authenticated');
CREATE POLICY "Allow users to mark their notifications as read" ON app_notifications FOR UPDATE USING (user_id = auth.uid());
