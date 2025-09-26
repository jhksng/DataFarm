<h2>RaspberryPi 4 Configuration</h2>
<details>
  <summary>Pip install</summary>
    RPi.GPIO
    paho-mqtt - 브로커
    picamera2 -카메라
    adafruit-blinka - board
    adafruit-circuitpython-sht31d - 온습도
</details>

<details>
  <summary>sudo apt install</summary>
    RPi.GPIO
    paho-mqtt - 브로커
    picamera2 -카메라
    adafruit-blinka - board
    adafruit-circuitpython-sht31d - 온습도
</details>

<details>
  <summary>Pip install</summary>
    libcamera-apps
</details>

<details>
  <summary>sudo apt-get install</summary>
    libcap-dev
</details>

<details>
  <summary>Google Cloud Storage(GCS) 연결</summary>
    pip install --trusted-host archive1.piwheels.org --trusted-host files.pythonhosted.org google-cloud-storage
    export GOOGLE_APPLICATION_CREDENTIALS="/dir/gcs-key.json"
</details>


카메라 관련 모듈 3개 설치를 해도 오류 발생 시 가상화 환경 재설정
python3 -m venv --system-site-packages project


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
