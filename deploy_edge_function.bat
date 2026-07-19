@echo off
chcp 65001 >nul
echo ============================
echo نشر Edge Function - Zad AI Proxy
echo ============================

echo.
echo 1. تسجيل الدخول إلى Supabase
call npx supabase login

echo.
echo 2. ربط المشروع
echo (استخدم المشروع ref الموجود في .env: auuftqncrjsnyylolhbu)
call npx supabase link --project-ref auuftqncrjsnyylolhbu

echo.
echo 3. نشر الدالة
call npx supabase functions deploy zad-ai-proxy --project-ref auuftqncrjsnyylolhbu

echo.
echo 4. تعيين مفتاح Gemini API في Supabase Secrets
set /p KEY="ادخل مفتاح Gemini API: "
call npx supabase secrets set GEMINI_API_KEY="%KEY%" --project-ref auuftqncrjsnyylolhbu

echo.
echo ============================
echo تم النشر بنجاح!
echo ============================
echo.
echo للتحقق:
echo curl -X POST "https://auuftqncrjsnyylolhbu.functions.supabase.co/zad-ai-proxy" ^
echo   -H "Content-Type: application/json" ^
echo   -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImF1dWZ0cW5jcmpzbnl5bG9saGJ1Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODMxMTU5MDgsImV4cCI6MjA5ODY5MTkwOH0.0W3q4f4aWjoJsKo1DG_x4KoroBd1WfFFzG8xy2J_DJc" ^
echo   -d "{\"request_type\":\"chat\",\"payload\":{\"message\":\"مرحبا\"}}"
echo.
pause
