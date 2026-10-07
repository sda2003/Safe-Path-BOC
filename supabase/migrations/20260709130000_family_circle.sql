-- Family Circle contacts + I'm Safe broadcasts

CREATE TABLE IF NOT EXISTS public.family_contacts (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    owner_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    contact_name TEXT NOT NULL DEFAULT '',
    contact_phone TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (owner_id, contact_phone)
);

ALTER TABLE public.family_contacts ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Owners can view own contacts"
    ON public.family_contacts FOR SELECT
    USING (auth.uid() = owner_id);

CREATE POLICY "Owners can insert own contacts"
    ON public.family_contacts FOR INSERT
    WITH CHECK (auth.uid() = owner_id);

CREATE POLICY "Owners can delete own contacts"
    ON public.family_contacts FOR DELETE
    USING (auth.uid() = owner_id);

CREATE TABLE IF NOT EXISTS public.safe_broadcasts (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    sender_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    sender_phone TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'safe',
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    message TEXT NOT NULL DEFAULT 'I''m safe',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE public.safe_broadcasts ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Users can insert own broadcasts"
    ON public.safe_broadcasts FOR INSERT
    WITH CHECK (auth.uid() = sender_id);

CREATE POLICY "Users can view own broadcasts"
    ON public.safe_broadcasts FOR SELECT
    USING (auth.uid() = sender_id);
