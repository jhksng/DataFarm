# chat.py (최신 google-genai SDK 버전)
from google import genai
import os

API_KEY = os.getenv("GEMINI_API_KEY")
if not API_KEY:
    raise SystemExit("❌ 오류: GEMINI_API_KEY 환경변수가 없습니다. 먼저 export GEMINI_API_KEY=\"YOUR_KEY\"")

# 클라이언트 생성
client = genai.Client(api_key=API_KEY)

print("Gemini 챗봇에 오신 것을 환영합니다! (종료하려면 'exit' 또는 'quit' 입력)")

# 대화 루프 시작
while True:
    user_input = input("You: ")
    if user_input.lower() in ["exit", "quit"]:
        print("챗봇을 종료합니다.")
        break

    try:
        response = client.models.generate_content(
            model="gemini-1.5-flash",  # 최신 모델
            contents=user_input
        )
        print(f"Gemini: {response.text}")
    except Exception as e:
        print(f"⚠️ 오류 발생: {e}")
