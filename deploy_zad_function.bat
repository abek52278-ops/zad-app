@echo off
chcp 65001 >nul
echo ====================================
echo  نشر Edge Function: zad-ai-proxy
echo ====================================
echo.
echo مشروع: auuftqncrjsnyylolhbu
echo.

:: سجل الدخول (حاول مرة وحدة)
npx supabase login

:: اربط المشروع و انشر
npx supabase link --project-ref auuftqncrjsnyylolhbu
npx supabase functions deploy zad-ai-proxy --project-ref auuftqncrjsnyylolhbu

:: ضع مفتاح Gemini API
echo.
echo حط مفتاح Gemini API (موجود في supabase\.env):
set /p KEY="مفتاح Gemini API: "
npx supabase secrets set GEMINI_API_KEY="%KEY%" --project-ref auuftqncrjsnyylolhbu

echo.
echo ====================================
echo  تم! جرب الاختبار:
echo ====================================
echo curl -X POST "https://auuftqncrjsnyylolhbu.supabase.co/functions/v1/zad-ai-proxy" ^
echo   -H "Content-Type: application/json" ^
echo   -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImF1dWZ0cW5jcmpzbnl5bG9saGJ1Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODMxMTU5MDgsImV4cCI6MjA5ODY5MTkwOH0.0W3q4f4aWjoJsKo1DG_x4KoroBd1WfFFzG8xy2J_DJc" ^
echo   -d "{\"request_type\":\"chat\",\"payload\":{\"message\":\"مرحبا\"}}"
echo.
pause
