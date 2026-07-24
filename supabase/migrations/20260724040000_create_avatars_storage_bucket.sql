-- Real avatar photo upload (replaces the 3 preset-icon fake avatar picker).
-- Public read so AsyncImage can render it anywhere in the app; writes restricted
-- to the user's own folder (avatars/{user_id}/...) via auth.uid().

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('avatars', 'avatars', true, 5242880, array['image/jpeg','image/png','image/webp'])
on conflict (id) do nothing;

create policy "avatars_public_read"
on storage.objects for select
to public
using (bucket_id = 'avatars');

create policy "avatars_owner_insert"
on storage.objects for insert
to authenticated
with check (bucket_id = 'avatars' and (storage.foldername(name))[1] = auth.uid()::text);

create policy "avatars_owner_update"
on storage.objects for update
to authenticated
using (bucket_id = 'avatars' and (storage.foldername(name))[1] = auth.uid()::text);

create policy "avatars_owner_delete"
on storage.objects for delete
to authenticated
using (bucket_id = 'avatars' and (storage.foldername(name))[1] = auth.uid()::text);
