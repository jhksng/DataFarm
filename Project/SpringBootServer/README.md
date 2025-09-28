v0.0.1<a href="https://drive.google.com/file/d/1VZNdCNHOtx-OIkrr6g3m89TfFNrgPzSz/view?usp=sharing" style="padding: 10px 20px; background-color: #4285F4; color: white; text-decoration: none; border-radius: 5px;">
DOWNLOAD
</a>
<br>
<details>
  <summary>변경 내역</summary>
  <ul>
    <li>카메라 기능</li> 
    <li>센서 데이터 조회 기능</li>
    <li>작물 정보 등록 기능</li>
    <li>작물 정보 조회 및 선택 기능</li> 
    <li>테이블 추가 (crop_info, photo, sensor_data, module_status)</li>
    <br>
    <li>250929-00:25/ soil 부분 코드 수정 및 수위 감지 값 보정</li>
    <li>잘 못 입력 된 값 soil_percentage = int(max(0, min(100, ((1023 - soil_raw) / 1023) * 100)))</li>
    <li>db에 저장 된 수치 = int(max(0, min(100, ((1023 - soil_raw) / 1023) * 100))) 를 재계산 하여 db에 수정 작업</li>
  </ul>
</details>




