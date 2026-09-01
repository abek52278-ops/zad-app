import sys
import os
import threading
from openai import OpenAI
from google import genai

def get_groq_response(prompt, results):
    try:
        client = OpenAI(
            base_url="https://api.groq.com/openai/v1",
            api_key=os.environ.get("GROQ_API_KEY")
        )
        response = client.chat.completions.create(
            
            model="openai/gpt-oss-120b",
            messages=[{"role": "user", "content": prompt}]
        )
        results['groq'] = response.choices[0].message.content
    except Exception as e:
        results['groq'] = f"Groq Error: {e}"

def get_gemini_response(prompt, results):
    try:
        client = genai.Client(api_key=os.environ.get("GEMINI_API_KEY"))
        response = client.models.generate_content(
            model='gemini-3.5-flash',
            contents=prompt,
        )
        results['gemini'] = response.text
    except Exception as e:
        results['gemini'] = f"Gemini Error: {e}"

def main():
    if len(sys.argv) < 2:
        print("Please provide a prompt.")
        sys.exit(1)

    prompt = sys.argv[1]
    results = {}

    # تشغيل النموذجين في نفس الوقت لتوفير الوقت
    t1 = threading.Thread(target=get_groq_response, args=(prompt, results))
    t2 = threading.Thread(target=get_gemini_response, args=(prompt, results))

    t1.start()
    t2.start()

    t1.join()
    t2.join()

    # طباعة النتيجة بصيغة يفهمها كلود
    print("---")
    print("LLM Council Review: Perspectives Gathered\n")
    print("### Groq (openai/gpt-oss-120b) Perspective ###")
    print(results.get('groq', 'No response'))
    print("\n### Gemini Perspective ###")
    print(results.get('gemini', 'No response'))
    print("---")

if __name__ == "__main__":
    main()