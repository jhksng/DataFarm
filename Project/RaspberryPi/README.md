<h2>pip install</h2>
<details>
  <summary>ddd</summary>
</details>
RPi.GPIO
paho-mqtt - 브로커
picamera2 -카메라
adafruit-blinka - board
adafruit-circuitpython-sht31d - 온습도



sudo apt install
libcamera-apps


pip install --trusted-host archive1.piwheels.org --trusted-host files.pythonhosted.org google-cloud-storage
export GOOGLE_APPLICATION_CREDENTIALS="/home/pi/gcs-key.json"



sudo apt-get install

libcap-dev

카메라 3개 설치 후 모듈 없으면 이걸로 재생성/ 카메라 모듈 3개 먼저 설치하고실행
python3 -m venv --system-site-packages project


===아두이노====
curl -fsSL https://raw.githubusercontent.com/arduino/arduino-cli/master/install.sh | sh

sudo mv arduino-cli /usr/local/bin/

arduino-cli core update-index
arduino-cli core install arduino:avr


sudo nano ~/.arduino15/arduino-cli.yaml

directories:
  data: /home/pi/.arduino15
  downloads: /home/pi/.arduino15/staging
  user: /home/pi/Arduino

library:
  enable_unsafe_install: false

daemon:
  port: "50051"


컴파일 및 업로드

arduino-cli compile -b arduino:avr:uno [project name, sensor.ino] -u -p [board name, /dev/ttyACM0]









nano ~/.bashrc 에

export 추가


[Unit]
Description=Data Farm Sensor Service
After=network.target

[Service]
ExecStart=/bin/bash -c "cd /home/pi/datafarm && source project/bin/activate && python start.py"
WorkingDirectory=/home/pi/datafarm
Environment="GOOGLE_APPLICATION_CREDENTIALS=/경로/서비스-계정-키.json"
Restart=always
User=pi

[Install]
WantedBy=multi-user.target

sudo systemctl daemon-reload
sudo systemctl restart datafarm.service
