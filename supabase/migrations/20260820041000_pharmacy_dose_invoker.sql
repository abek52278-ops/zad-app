-- The atomic function only touches rows already covered by user-owned RLS policies.
-- Run with the caller's privileges so authenticated clients never cross an ownership boundary.
alter function public.zad_log_pharmacy_dose_atomic(uuid, uuid, timestamptz, timestamptz)
  security invoker;
