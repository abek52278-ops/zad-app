-- بند 33.2 — zad-voice-live بقى caller حقيقي لـ agent_execute/agent_confirm (مش نظري)،
-- فـ'voice' لازم تنضم لقيم agent_actions.source المسموحة، نفس تعليق الميجريشن الأصلية:
-- "مش قنوات نظرية لسه ملهاش استدعاء فعلي في الكود". من غيرها كل نداء من الجلسة الصوتية
-- كان هيفشل بـCHECK violation صامت (نفس نمط 42703 اللي الجلسة دي كلها بتحاول تتجنبه).
alter table public.agent_actions drop constraint if exists agent_actions_source_check;
alter table public.agent_actions
  add constraint agent_actions_source_check
  check (source in ('app_chat', 'telegram', 'confirm', 'daily', 'event', 'voice'));
