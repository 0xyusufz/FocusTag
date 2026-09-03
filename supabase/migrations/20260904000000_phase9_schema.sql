-- Phase 9 Step 2A: Database Schema Implementation

-- 1. Create institutions table
CREATE TABLE IF NOT EXISTS public.institutions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name TEXT NOT NULL,
    created_at TIMESTAMPTZ DEFAULT now() NOT NULL
);

-- 2. Modify profiles table (Additive)
-- Assuming profiles table already exists with id, name, role
ALTER TABLE public.profiles
ADD COLUMN IF NOT EXISTS institution_id UUID REFERENCES public.institutions(id);

-- Add index for profiles(institution_id)
CREATE INDEX IF NOT EXISTS idx_profiles_institution_id ON public.profiles(institution_id);

-- 3. Create locations table
CREATE TABLE IF NOT EXISTS public.locations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    institution_id UUID NOT NULL REFERENCES public.institutions(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    created_at TIMESTAMPTZ DEFAULT now() NOT NULL
);

-- Add index for locations(institution_id)
CREATE INDEX IF NOT EXISTS idx_locations_institution_id ON public.locations(institution_id);

-- 4. Create nfc_tags table
CREATE TABLE IF NOT EXISTS public.nfc_tags (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    uid TEXT UNIQUE NOT NULL, -- Format: 1D:FF:7C:1C:1A:10:80
    location_id UUID NOT NULL REFERENCES public.locations(id) ON DELETE CASCADE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ DEFAULT now() NOT NULL
);

-- Add index for nfc_tags(location_id)
CREATE INDEX IF NOT EXISTS idx_nfc_tags_location_id ON public.nfc_tags(location_id);

-- 5. Create classes table
CREATE TABLE IF NOT EXISTS public.classes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    institution_id UUID NOT NULL REFERENCES public.institutions(id) ON DELETE CASCADE,
    location_id UUID NOT NULL UNIQUE REFERENCES public.locations(id), -- ONE CLASS PER LOCATION
    name TEXT NOT NULL,
    created_at TIMESTAMPTZ DEFAULT now() NOT NULL
);

-- Add index for classes(institution_id)
CREATE INDEX IF NOT EXISTS idx_classes_institution_id ON public.classes(institution_id);

-- 6. Create enrollments table
CREATE TABLE IF NOT EXISTS public.enrollments (
    student_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    class_id UUID NOT NULL REFERENCES public.classes(id) ON DELETE CASCADE,
    PRIMARY KEY (student_id, class_id),
    created_at TIMESTAMPTZ DEFAULT now() NOT NULL
);

-- Add index for enrollments(class_id)
CREATE INDEX IF NOT EXISTS idx_enrollments_class_id ON public.enrollments(class_id);

-- 7. Create teacher_class_access table
CREATE TABLE IF NOT EXISTS public.teacher_class_access (
    teacher_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    class_id UUID NOT NULL REFERENCES public.classes(id) ON DELETE CASCADE,
    PRIMARY KEY (teacher_id, class_id),
    created_at TIMESTAMPTZ DEFAULT now() NOT NULL
);

-- Add index for teacher_class_access(class_id)
CREATE INDEX IF NOT EXISTS idx_teacher_class_access_class_id ON public.teacher_class_access(class_id);
