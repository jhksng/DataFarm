# 스마트팜 기능 테스트

<h2>프로젝트 개요</h2>
<ul>
  <li>스마트팜</li>
</ul>
<h2>개발 목표</h2>
<ol>
  <li>파종에서 수확까지</li>
  <li>스마트팜 시스템 구축 전, 소규모 실증을 통해 데이터 기반을 마련.</li>
  <li>OpenCV를 활용한 이미지 분석을 통해 식물 잎의 크기를 정량적으로 측정</li>
</ol>

<h2>팀원</h2>

<table>
  <tr>
    <td>김정호</td>
    <td>김한빈</td>
  </tr>

  <tr>
    <td>하드웨어 설계 및 구현<br>클라우드 서버 구축</td>
     <td>프론트엔드 설계 및 구현<br>백엔드 설계 및 구현</td>
  </tr>

</table>


<h2>개발 환경</h2>
<b>

<ul>
        <li><b>Back-end Server</b>: Spring Boot (Java)
            <ul>
                <li>Server OS: Google Compute Engine (<b>Linux (Debian)</b>)</li>
            </ul>
        </li>
        <li><b>Database & Storage</b>:
            <ul>
                <li>Database: <b>Google Cloud SQL (MySQL)</b></li>
                <li>File Storage: Google Cloud Storage (사진, 로그)</li>
            </ul>
        </li>
        <li><b>IoT & Communication</b>:
            <ul>
                <li>IoT Device: Raspberry Pi</li>
                <li>IoT Device OS: <b>Raspberry Pi OS (Linux)</b></li>
                <li>Protocol: <b>MQTT</b></li>
            </ul>
        </li>
        <li><b>Front-end & Design</b>:
            <ul>
                <li>Front-end: HTML, CSS</li>
                <li>Design: Figma</li>
            </ul>
        </li>
        <li><b>Tools</b>:
            <ul>
                <li>Tools: Git, GitHub, Discord, Notion</li>
            </ul>
        </li>
    </ul>

<h2>하드웨어 구조</h2>

<ul>
    <li><b>메인 컨트롤러 및 센서 모듈</b>
        <ul>
            <li>Raspberry Pi 4 (4GB)
                <ul>
                    <li>온습도 센서(SHT31)</li>
                    <li>카메라 모듈(Pi Camera Module 3)</li>
                </ul>
            </li>
            <li>Arduino
                <ul>
                    <li>토양 습도 감지 센서</li>
                    <li>수위 감지 센서</li>
                </ul>
            </li>
        </ul>
    </li>
    <li><b>환경 제어 시스템</b>
        <ul>
            <li>워터 펌프 모듈 (5v)</li>
            <li>LED 식물 생장등 (5v)</li>
            <li>PTC 히터 (12v)</li>
            <li>쿨러x2 (12v)</li>
        </ul>
    </li>
    <li><b>전원 공급 장치</b>
        <ul>
            <li>SMPS (12v)</li>
            <li>DC/DC 변환 컨버터 (12V to 5V)</li>
        </ul>
    </li>
    <li><b>기타 하드웨어</b>
        <ul>
            <li>화분</li>
            <li>실리콘 관</li>
            <li>아크릴판</li>
        </ul>
    </li>
</ul>

<h2>기능</h2>
<ul>
  <li>씨앗을 심고, 물만 채워주면 알아서 수확 시기까지 작동</li>
  <li>환경 데이터 조회</li>
  <li></li>
</ul>


