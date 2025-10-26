import google.generativeai as genai
import os

# [중요] API 키 설정: 
# 더 안전한 방법은 환경 변수를 사용하는 것입니다.
# 터미널에서 'export GEMINI_API_KEY="여기에_키_입력"'을 실행한 후,
# 아래 코드를 사용하세요.
API_KEY = os.getenv("GEMINI_API_KEY")

# 만약 환경 변수 설정이 어렵다면, 임시로 다음과 같이 직접 입력할 수 있습니다.
# (경고: 이 방법은 보안에 취약하므로 외부에 코드를 공유하지 마세요.)
# API_KEY = "YOUR_API_KEY_HERE" 

if not API_KEY:
    print("오류: GEMINI_API_KEY 환경 변수가 설정되지 않았습니다.")
    exit()

try:
    genai.configure(api_key=API_KEY)

    # 사용할 모델 설정
    model = genai.GenerativeModel('gemini-1.5-flash') 

    # 대화 기록을 유지하는 챗 세션 시작
    chat = model.start_chat(history=[])

    print("Gemini 챗봇에 오신 것을 환영합니다! (종료하려면 'exit' 또는 'quit' 입력)")

    while True:
        # 사용자 입력 받기
        user_input = input("You: ")

        if user_input.lower() in ["exit", "quit"]:
            print("챗봇을 종료합니다.")
            break

        # Gemini API로 메시지 전송
        response = chat.send_message(user_input)

        # 봇의 응답 출력
        print(f"Gemini: {response.text}")

except Exception as e:
    print(f"오류가 발생했습니다: {e}")
