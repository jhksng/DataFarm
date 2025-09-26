import RPi.GPIO as GPIO
import time

# 사용할 GPIO 핀 번호 리스트
RELAY_PINS = [5, 6, 13, 19, 26]

# GPIO 핀 모드 설정 (BCM 모드)
GPIO.setmode(GPIO.BCM)

# 릴레이 핀들을 출력으로 설정
for pin in RELAY_PINS:
    GPIO.setup(pin, GPIO.OUT)

def set_pin_state(pin, state):
    """
    주어진 핀 번호와 상태에 따라 릴레이를 제어합니다.
    (대부분의 릴레이 모듈은 Low 신호에서 ON 되기 때문에 HIGH/LOW를 반대로 적용합니다.)
    """
    if state == 1: # 켜기
        GPIO.output(pin, GPIO.LOW)
        print(f"✅ GPIO {pin} 핀을 켰습니다.")
    elif state == 0: # 끄기
        GPIO.output(pin, GPIO.HIGH)
        print(f"❌ GPIO {pin} 핀을 껐습니다.")

def main():
    """
    사용자의 입력을 받아 핀 상태를 제어하는 메인 함수
    """
    try:
        # 모든 핀을 초기 상태(꺼짐)로 설정
        print("모든 릴레이 핀을 초기화합니다.")
        for pin in RELAY_PINS:
            GPIO.output(pin, GPIO.HIGH)
        
        while True:
            # 현재 상태 출력
            print("\n--- 현재 릴레이 상태 ---")
            for pin in RELAY_PINS:
                # 릴레이가 Low일 때 켜진 상태로 표시
                current_state = "켜짐" if GPIO.input(pin) == GPIO.LOW else "꺼짐"
                print(f"GPIO {pin} 핀: {current_state}")
            print("------------------------")

            # 사용자 입력 받기
            print("\n어떤 핀의 상태를 변경하시겠습니까? (5, 6, 13, 19, 26) 'q'를 입력하면 종료합니다.")
            pin_input = input("핀 번호 입력: ")

            if pin_input.lower() == 'q':
                break

            try:
                pin_to_control = int(pin_input)
                if pin_to_control not in RELAY_PINS:
                    print("⚠️ 유효하지 않은 핀 번호입니다. 다시 입력해 주세요.")
                    continue

                print(f"GPIO {pin_to_control} 핀을 어떻게 할까요? (1: 켜기, 0: 끄기)")
                state_input = input("상태 입력 (1 또는 0): ")

                state_to_set = int(state_input)
                if state_to_set not in [0, 1]:
                    print("⚠️ 유효하지 않은 상태 값입니다. 1 또는 0을 입력해 주세요.")
                    continue

                set_pin_state(pin_to_control, state_to_set)

            except ValueError:
                print("⚠️ 잘못된 입력입니다. 숫자를 입력해 주세요.")
                
    except KeyboardInterrupt:
        print("\n프로그램을 종료합니다.")
    finally:
        # 프로그램 종료 시 GPIO 초기화
        print("모든 릴레이 핀을 끄고 GPIO를 초기화합니다.")
        GPIO.cleanup()

if __name__ == "__main__":
    main()
