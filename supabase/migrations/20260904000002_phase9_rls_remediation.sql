-- Phase 9 Step 2B: RLS Security Remediation
-- supabase/migrations/20260904000002_phase9_rls_remediation.sql
-- Finding 1: Profiles Hardening & Secure Creation
ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;

-- Secure profile creation trigger to prevent privilege escalation at signup
CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS TRIGGER AS $$
BEGIN
  INSERT INTO public.profiles (id, name, role, institution_id)
  VALUES (
    NEW.id,
    NEW.raw_user_meta_data->>'name',
    'student', -- Default role is always student
    NULL       -- Institution is assigned by admin later to ensure verification
  );
  RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;

-- Explicitly drop and recreate trigger to ensure it is the only path for profile creation
DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;
CREATE TRIGGER on_auth_user_created
  AFTER INSERT ON auth.users
  FOR EACH ROW EXECUTE FUNCTION public.handle_new_user();

-- Ensure users cannot insert into profiles directly to prevent spoofing role or institution
DROP POLICY IF EXISTS "Users can insert own profile" ON public.profiles;
-- No INSERT policy for 'authenticated' role ensures only the trigger can create profiles.

-- Finding 4: SECURITY DEFINER Hardening
-- Adding search_path and restricting execution to authenticated users
ALTER FUNCTION public.get_auth_role() SET search_path = public;
ALTER FUNCTION public.get_auth_institution() SET search_path = public;

REVOKE EXECUTE ON FUNCTION public.get_auth_role() FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION public.get_auth_institution() FROM PUBLIC;

GRANT EXECUTE ON FUNCTION public.get_auth_role() TO authenticated;
GRANT EXECUTE ON FUNCTION public.get_auth_institution() TO authenticated;

-- Finding 2: Classes SELECT Policy Hardening
-- Restricting class visibility to enrolled students, authorized teachers, and admins
DROP POLICY IF EXISTS "Classes are visible to members" ON public.classes;
CREATE POLICY "Classes are visible to enrolled students, authorized teachers, and admins"
ON public.classes FOR SELECT
TO authenticated
USING (
  (public.get_auth_role() = 'admin' AND institution_id = public.get_auth_institution()) OR
  (public.get_auth_role() = 'teacher' AND EXISTS (
    SELECT 1 FROM public.teacher_class_access tca
    WHERE tca.class_id = public.classes.id AND tca.teacher_id = auth.uid()
  )) OR
  (public.get_auth_role() = 'student' AND EXISTS (
    SELECT 1 FROM public.enrollments e
    WHERE e.class_id = public.classes.id AND e.student_id = auth.uid()
  ))
);

-- Finding 3 & 5: Admin Write Validation with Explicit WITH CHECK
-- Ensuring admins can only manage relationships within their own institution

-- Enrollments admin policy
DROP POLICY IF EXISTS "Enrollments are manageable by admins" ON public.enrollments;
CREATE POLICY "Enrollments are manageable by admins"
ON public.enrollments FOR ALL
TO authenticated
USING (
  public.get_auth_role() = 'admin' AND
  EXISTS (SELECT 1 FROM public.classes c WHERE c.id = enrollments.class_id AND c.institution_id = public.get_auth_institution())
)
WITH CHECK (
  public.get_auth_role() = 'admin' AND
  EXISTS (SELECT 1 FROM public.classes c WHERE c.id = enrollments.class_id AND c.institution_id = public.get_auth_institution()) AND
  EXISTS (SELECT 1 FROM public.profiles p WHERE p.id = enrollments.student_id AND p.institution_id = public.get_auth_institution())
);

-- Teacher Class Access admin policy
DROP POLICY IF EXISTS "Teacher access is manageable by admins" ON public.teacher_class_access;
CREATE POLICY "Teacher access is manageable by admins"
ON public.teacher_class_access FOR ALL
TO authenticated
USING (
  public.get_auth_role() = 'admin' AND
  EXISTS (SELECT 1 FROM public.classes c WHERE c.id = teacher_class_access.class_id AND c.institution_id = public.get_auth_institution())
)
WITH CHECK (
  public.get_auth_role() = 'admin' AND
  EXISTS (SELECT 1 FROM public.classes c WHERE c.id = teacher_class_access.class_id AND c.institution_id = public.get_auth_institution()) AND
  EXISTS (SELECT 1 FROM public.profiles p WHERE p.id = teacher_class_access.teacher_id AND p.institution_id = public.get_auth_institution() AND p.role = 'teacher')
);

-- Institutions admin policy explicit WITH CHECK
DROP POLICY IF EXISTS "Institutions are manageable by admins" ON public.institutions;
CREATE POLICY "Institutions are manageable by admins"
ON public.institutions FOR ALL
TO authenticated
USING (public.get_auth_role() = 'admin' AND id = public.get_auth_institution())
WITH CHECK (public.get_auth_role() = 'admin' AND id = public.get_auth_institution());

-- Locations admin policy explicit WITH CHECK
DROP POLICY IF EXISTS "Locations are manageable by admins" ON public.locations;
CREATE POLICY "Locations are manageable by admins"
ON public.locations FOR ALL
TO authenticated
USING (public.get_auth_role() = 'admin' AND institution_id = public.get_auth_institution())
WITH CHECK (public.get_auth_role() = 'admin' AND institution_id = public.get_auth_institution());

-- NFC Tags admin policy explicit WITH CHECK
DROP POLICY IF EXISTS "NFC tags are manageable by admins" ON public.nfc_tags;
CREATE POLICY "NFC tags are manageable by admins"
ON public.nfc_tags FOR ALL
TO authenticated
USING (
  public.get_auth_role() = 'admin' AND
  EXISTS (SELECT 1 FROM public.locations l WHERE l.id = nfc_tags.location_id AND l.institution_id = public.get_auth_institution())
)
WITH CHECK (
  public.get_auth_role() = 'admin' AND
  EXISTS (SELECT 1 FROM public.locations l WHERE l.id = nfc_tags.location_id AND l.institution_id = public.get_auth_institution())
);

-- Classes admin policy explicit WITH CHECK
DROP POLICY IF EXISTS "Classes are manageable by admins" ON public.classes;
CREATE POLICY "Classes are manageable by admins"
ON public.classes FOR ALL
TO authenticated
USING (public.get_auth_role() = 'admin' AND institution_id = public.get_auth_institution())
WITH CHECK (
  public.get_auth_role() = 'admin' AND
  institution_id = public.get_auth_institution() AND
  EXISTS (SELECT 1 FROM public.locations l WHERE l.id = location_id AND l.institution_id = public.get_auth_institution())
);

-- Finding 1 (Cont): Profiles Update policy hardening (ensuring role/institution are immutable for the user)
DROP POLICY IF EXISTS "Users can update their own name only" ON public.profiles;
CREATE POLICY "Users can update their own name only"
ON public.profiles FOR UPDATE
TO authenticated
USING (id = auth.uid())
WITH CHECK (
  id = auth.uid() AND
  role = (SELECT role FROM public.profiles WHERE id = auth.uid()) AND
  institution_id IS NOT DISTINCT FROM (SELECT institution_id FROM public.profiles WHERE id = auth.uid())
);

-- Profiles visibility fix (cleaning up subquery reference)
DROP POLICY IF EXISTS "Profiles are visible to self, authorized teachers, and admins" ON public.profiles;
CREATE POLICY "Profiles are visible to self, authorized teachers, and admins"
ON public.profiles FOR SELECT
TO authenticated
USING (
  id = auth.uid() OR
  (public.get_auth_role() = 'admin' AND institution_id = public.get_auth_institution()) OR
  (public.get_auth_role() = 'teacher' AND EXISTS (
      SELECT 1 FROM public.teacher_class_access tca
      JOIN public.enrollments e ON tca.class_id = e.class_id
      WHERE tca.teacher_id = auth.uid() AND e.student_id = id -- 'id' refers to the row being evaluated
  ))
);

-- Existing FocusTag Regression: Explicitly restore/ensure student write access if RLS is enabled
-- (These policies are often missing from additive migrations but required for background sync)
CREATE POLICY "Students can insert own sessions" ON public.focus_sessions FOR INSERT TO authenticated WITH CHECK (user_id = auth.uid());
CREATE POLICY "Students can update own sessions" ON public.focus_sessions FOR UPDATE TO authenticated USING (user_id = auth.uid()) WITH CHECK (user_id = auth.uid());
CREATE POLICY "Students can insert own interception events" ON public.interception_events FOR INSERT TO authenticated WITH CHECK (user_id = auth.uid());

-- Institutional Domain Enforcement (Server-side explicit check)
-- This ensures that only users from the designated institutional domain can sign up.
CREATE OR REPLACE FUNCTION public.enforce_institutional_domain()
RETURNS TRIGGER AS $$
BEGIN
  -- Replace 'college.edu' with the actual institutional domain before deployment.
  -- This example blocks any email not ending in '@college.edu'.
  IF NEW.email NOT LIKE '%@college.edu' THEN
    RAISE EXCEPTION 'Only institutional emails are allowed for this pilot.';
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;

-- Trigger for domain enforcement (Commented out until actual domain is confirmed)
-- DROP TRIGGER IF EXISTS on_auth_user_signup ON auth.users;
-- CREATE TRIGGER on_auth_user_signup
--   BEFORE INSERT ON auth.users
--   FOR EACH ROW EXECUTE FUNCTION public.enforce_institutional_domain();
