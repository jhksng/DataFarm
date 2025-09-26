import RPi.GPIO as GPIO
import time

# 사용할 GPIO 핀 번호 리스트 [워터펌프, LED, 히터, 쿨러A, 쿨러B]
RELAY_PINS = [5, 6, 13, 19, 26]

# GPIO 핀 모드 설정 (BCM 모드)
GPIO.setmode(GPIO.BCM)

# 릴레이 핀들을 출력으로 설정
for pin in RELAY_PINS:
    GPIO.setup(pin, GPIO.OUT)

def set_pin_state(pin, state):
    if state == 1: # ON
        GPIO.output(pin, GPIO.HIGH)
        print(f"GPIO {pin} PIN ON")
    elif state == 0: # OFF
        GPIO.output(pin, GPIO.LOW)
        print(f"GPIO {pin} PIN OFF")

def main():
    try:
        # 핀 초기 상태 OFF
        print("모든 릴레이 핀을 초기화합니다.")
        for pin in RELAY_PINS:
            GPIO.output(pin, GPIO.LOW)
            
        while True:
           
            print("\n--- 현재 릴레이 상태 ---")
            for pin in RELAY_PINS:
                # 릴레이가 Low일 때 켜진 상태로 표시
                current_state = "켜짐" if GPIO.input(pin) == GPIO.HIGH else "꺼짐"
                print(f"GPIO {pin} 핀: {current_state}")
            print("------------------------")

            # 사용자 입력 받기
            print("\n INPUT PIN NUMBER (5, 6, 13, 19, 26) EXIT BUTTON 'e'")
            pin_input = input("핀 번호 입력: ")

            if pin_input.lower() == 'e':
                break

            try:
                pin_to_control = int(pin_input)
                if pin_to_control not in RELAY_PINS:
                    print("다시 입력해 주세요.")
                    continue

                print(f"GPIO {pin_to_control} 핀 (1: 켜기, 0: 끄기)")
                state_input = input("(1: 켜기, 0: 끄기): ")

                state_to_set = int(state_input)
                if state_to_set not in [0, 1]:
                    print("1 또는 0을 입력해 주세요.")
                    continue

                set_pin_state(pin_to_control, state_to_set)

            except ValueError:
                print("숫자를 입력해 주세요.")
                
    except KeyboardInterrupt:
        print("\n프로그램을 종료합니다.")
    finally:
        # 프로그램 종료 시 GPIO 초기화
        print("모든 릴레이 핀을 끄고 GPIO를 초기화합니다.")
        GPIO.cleanup()

if __name__ == "__main__":
    main()
