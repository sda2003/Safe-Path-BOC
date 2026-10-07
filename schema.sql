-- Supabase Schema for SafePath
-- Run this in your Supabase SQL Editor (supabase.com → SQL Editor)

-- Enable UUID generation
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ═══════════════════════════════════════
-- 1. USERS TABLE (profile data)
-- ═══════════════════════════════════════
CREATE TABLE IF NOT EXISTS public.users (
    id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    email TEXT,
    district TEXT
);

ALTER TABLE public.users ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Users can view everyone." ON public.users FOR SELECT USING (true);
CREATE POLICY "Users can insert their own profile." ON public.users FOR INSERT WITH CHECK (auth.uid() = id);
CREATE POLICY "Users can update own profile." ON public.users FOR UPDATE USING (auth.uid() = id);

-- Phone verification (Feature 1)
ALTER TABLE public.users ADD COLUMN IF NOT EXISTS phone TEXT;
ALTER TABLE public.users ADD COLUMN IF NOT EXISTS verified BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE public.users ADD COLUMN IF NOT EXISTS verified_at TIMESTAMPTZ;
ALTER TABLE public.users ADD COLUMN IF NOT EXISTS tier TEXT NOT NULL DEFAULT 'free';

-- ═══════════════════════════════════════
-- 2. ALERTS TABLE (matches Alert.kt model)
-- ═══════════════════════════════════════
CREATE TABLE IF NOT EXISTS public.alerts (
    id TEXT PRIMARY KEY DEFAULT uuid_generate_v4()::text,
    user_id TEXT NOT NULL DEFAULT 'anon',
    type TEXT NOT NULL,
    severity TEXT NOT NULL DEFAULT 'MODERATE',
    description TEXT DEFAULT '',
    latitude DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    longitude DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    district TEXT NOT NULL DEFAULT '',
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE public.alerts ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Anyone can view alerts." ON public.alerts FOR SELECT USING (true);
CREATE POLICY "Anyone can insert alerts." ON public.alerts FOR INSERT WITH CHECK (true);

-- ═══════════════════════════════════════
-- 3. CHAT MESSAGES TABLE (matches ChatMessage.kt)
-- ═══════════════════════════════════════
CREATE TABLE IF NOT EXISTS public.chat_messages (
    id UUID DEFAULT uuid_generate_v4() PRIMARY KEY,
    room_id TEXT NOT NULL,
    user_id TEXT NOT NULL DEFAULT 'anon',
    sender_name TEXT NOT NULL DEFAULT 'Anonymous',
    is_anonymous BOOLEAN NOT NULL DEFAULT false,
    message TEXT NOT NULL,
    image_url TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE public.chat_messages ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Anyone can view chat_messages." ON public.chat_messages FOR SELECT USING (true);
CREATE POLICY "Anyone can insert chat_messages." ON public.chat_messages FOR INSERT WITH CHECK (true);

-- ═══════════════════════════════════════
-- 4. STORAGE BUCKET for chat media
-- ═══════════════════════════════════════
INSERT INTO storage.buckets (id, name, public) VALUES ('chat_media', 'chat_media', true) ON CONFLICT DO NOTHING;
CREATE POLICY "Public Access" ON storage.objects FOR SELECT USING ( bucket_id = 'chat_media' );
CREATE POLICY "Auth Insert" ON storage.objects FOR INSERT WITH CHECK ( bucket_id = 'chat_media' );

-- ═══════════════════════════════════════
-- 5. REALTIME PUBLICATION (needed for live updates)
-- ═══════════════════════════════════════
ALTER PUBLICATION supabase_realtime ADD TABLE public.alerts;
ALTER PUBLICATION supabase_realtime ADD TABLE public.chat_messages;
