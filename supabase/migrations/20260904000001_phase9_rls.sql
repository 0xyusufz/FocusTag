-- Phase 9 Step 2B: RLS + Database Security Implementation
-- supabase/migrations/20260904000001_phase9_rls.sql
-- 1. Enable RLS on all new tables
ALTER TABLE public.institutions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.locations ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.nfc_tags ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.classes ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.enrollments ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.teacher_class_access ENABLE ROW LEVEL SECURITY;

-- 2. Helper functions to simplify policies and prevent recursion
-- Note: Stable functions are cached per-statement in Postgres.
CREATE OR REPLACE FUNCTION public.get_auth_role()
RETURNS TEXT AS $$
  SELECT role FROM public.profiles WHERE id = auth.uid();
$$ LANGUAGE sql STABLE SECURITY DEFINER;

CREATE OR REPLACE FUNCTION public.get_auth_institution()
RETURNS UUID AS $$
  SELECT institution_id FROM public.profiles WHERE id = auth.uid();
$$ LANGUAGE sql STABLE SECURITY DEFINER;

-- 3. Institutions Policies
CREATE POLICY "Institutions are visible to members"
ON public.institutions FOR SELECT
TO authenticated
USING (id = public.get_auth_institution());

CREATE POLICY "Institutions are manageable by admins"
ON public.institutions FOR ALL
TO authenticated
USING (public.get_auth_role() = 'admin' AND id = public.get_auth_institution());

-- 4. Locations Policies
CREATE POLICY "Locations are visible to members"
ON public.locations FOR SELECT
TO authenticated
USING (institution_id = public.get_auth_institution());

CREATE POLICY "Locations are manageable by admins"
ON public.locations FOR ALL
TO authenticated
USING (public.get_auth_role() = 'admin' AND institution_id = public.get_auth_institution());

-- 5. NFC Tags Policies
CREATE POLICY "NFC tags are visible to members"
ON public.nfc_tags FOR SELECT
TO authenticated
USING (
  EXISTS (
    SELECT 1 FROM public.locations l
    WHERE l.id = nfc_tags.location_id
    AND l.institution_id = public.get_auth_institution()
  )
);

CREATE POLICY "NFC tags are manageable by admins"
ON public.nfc_tags FOR ALL
TO authenticated
USING (
  public.get_auth_role() = 'admin' AND
  EXISTS (
    SELECT 1 FROM public.locations l
    WHERE l.id = nfc_tags.location_id
    AND l.institution_id = public.get_auth_institution()
  )
);

-- 6. Classes Policies
CREATE POLICY "Classes are visible to members"
ON public.classes FOR SELECT
TO authenticated
USING (institution_id = public.get_auth_institution());

CREATE POLICY "Classes are manageable by admins"
ON public.classes FOR ALL
TO authenticated
USING (public.get_auth_role() = 'admin' AND institution_id = public.get_auth_institution());

-- 7. Enrollments Policies
CREATE POLICY "Enrollments are visible to students and their teachers"
ON public.enrollments FOR SELECT
TO authenticated
USING (
  student_id = auth.uid() OR
  public.get_auth_role() = 'admin' AND EXISTS (SELECT 1 FROM public.classes c WHERE c.id = enrollments.class_id AND c.institution_id = public.get_auth_institution()) OR
  EXISTS (
    SELECT 1 FROM public.teacher_class_access tca
    WHERE tca.class_id = enrollments.class_id
    AND tca.teacher_id = auth.uid()
  )
);

CREATE POLICY "Enrollments are manageable by admins"
ON public.enrollments FOR ALL
TO authenticated
USING (
  public.get_auth_role() = 'admin' AND
  EXISTS (SELECT 1 FROM public.classes c WHERE c.id = enrollments.class_id AND c.institution_id = public.get_auth_institution())
);

-- 8. Teacher Class Access Policies
CREATE POLICY "Teacher access is visible to self and admins"
ON public.teacher_class_access FOR SELECT
TO authenticated
USING (
  teacher_id = auth.uid() OR
  public.get_auth_role() = 'admin' AND EXISTS (SELECT 1 FROM public.classes c WHERE c.id = teacher_class_access.class_id AND c.institution_id = public.get_auth_institution())
);

CREATE POLICY "Teacher access is manageable by admins"
ON public.teacher_class_access FOR ALL
TO authenticated
USING (
  public.get_auth_role() = 'admin' AND
  EXISTS (SELECT 1 FROM public.classes c WHERE c.id = teacher_class_access.class_id AND c.institution_id = public.get_auth_institution())
);

-- 9. Profiles Hardening (Existing Table)
DROP POLICY IF EXISTS "Users can read all profiles" ON public.profiles; -- Cleanup old loose policy if it exists
CREATE POLICY "Profiles are visible to self, authorized teachers, and admins"
ON public.profiles FOR SELECT
TO authenticated
USING (
  id = auth.uid() OR
  (public.get_auth_role() = 'admin' AND institution_id = public.get_auth_institution()) OR
  (public.get_auth_role() = 'teacher' AND EXISTS (
      SELECT 1 FROM public.teacher_class_access tca
      JOIN public.enrollments e ON tca.class_id = e.class_id
      WHERE tca.teacher_id = auth.uid() AND e.student_id = public.profiles.id
  ))
);

-- Protect role and institution_id from client updates
CREATE POLICY "Users can update their own name only"
ON public.profiles FOR UPDATE
TO authenticated
USING (id = auth.uid())
WITH CHECK (
  id = auth.uid() AND
  role = (SELECT role FROM public.profiles WHERE id = auth.uid()) AND
  institution_id IS NOT DISTINCT FROM (SELECT institution_id FROM public.profiles WHERE id = auth.uid())
);

-- 10. Focus Sessions Teacher Access (Existing Table)
-- Ensure students still have their original own-data access
DROP POLICY IF EXISTS "Users can read own sessions" ON public.focus_sessions;
CREATE POLICY "Sessions are visible to owner and authorized teachers"
ON public.focus_sessions FOR SELECT
TO authenticated
USING (
  user_id = auth.uid() OR
  (public.get_auth_role() = 'teacher' AND EXISTS (
    -- Teacher Session Access Logic:
    -- student is in teacher's class AND session tag matches class location
    SELECT 1 FROM public.teacher_class_access tca
    JOIN public.enrollments e ON tca.class_id = e.class_id
    JOIN public.classes c ON tca.class_id = c.id
    JOIN public.nfc_tags nt ON c.location_id = nt.location_id
    WHERE tca.teacher_id = auth.uid()
    AND e.student_id = focus_sessions.user_id
    AND nt.uid = focus_sessions.tag_id
  ))
);

-- Interception events follow the same owner/teacher pattern
DROP POLICY IF EXISTS "Users can read own events" ON public.interception_events;
CREATE POLICY "Events are visible to owner and authorized teachers"
ON public.interception_events FOR SELECT
TO authenticated
USING (
  user_id = auth.uid() OR
  (public.get_auth_role() = 'teacher' AND EXISTS (
    SELECT 1 FROM public.focus_sessions s
    WHERE s.id = interception_events.session_id
    AND EXISTS (
      SELECT 1 FROM public.teacher_class_access tca
      JOIN public.enrollments e ON tca.class_id = e.class_id
      JOIN public.classes c ON tca.class_id = c.id
      JOIN public.nfc_tags nt ON c.location_id = nt.location_id
      WHERE tca.teacher_id = auth.uid()
      AND e.student_id = s.user_id
      AND nt.uid = s.tag_id
    )
  ))
);
