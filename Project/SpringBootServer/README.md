v0.0.1<a href="https://drive.google.com/file/d/1VZNdCNHOtx-OIkrr6g3m89TfFNrgPzSz/view?usp=sharing" style="padding: 10px 20px; background-color: #4285F4; color: white; text-decoration: none; border-radius: 5px;">
DOWNLOAD</a>

<details>
  <summary>변경 내역</summary>
  <ul>
    <li>카메라 기능</li> 
    <li>센서 데이터 조회 기능</li>
    <li>작물 정보 등록 기능</li>
    <li>작물 정보 조회 및 선택 기능</li> 
    <li>테이블 추가 (crop_info, photo, sensor_data, module_status)</li>
  </ul>
</details>

v0.0.2<a href="https://drive.google.com/file/d/1h02hCvxCivTj3eiani_UVQzI1xliGZ5L/view?usp=sharing" style="padding: 10px 20px; background-color: #4285F4; color: white; text-decoration: none; border-radius: 5px;">
DOWNLOAD</a>
<details>
  <summary>변경 내역_250929</summary>
  <ul>
    <li>센서 데이터 가장 최근 값 조회 및 페이지 구분</li> 
    <li>히터 2분 동작, 5분 쿨/ 워터 펌프 5초 동작, 24시간 쿨</li>
    <li>라즈베리파이 토양 습도 코드 수정 및 수위 감지 값 보정</li>
    <li>잘 못 입력 된 값 soil_percentage = int(max(0, min(100, ((1023 - soil_raw) / 1023) * 100)))</li>
    <li>db에 저장 된 수치 = int(max(0, min(100, ((1023 - soil_raw) / 1023) * 100))) 를 재계산 하여 db에 수정 작업</li>
    <li>
      <table>
        <tbody>
          <tr>
            <td>99</td>
            <td>3</td>
          </tr>
          <tr>
            <td>98</td>
            <td>13</td>
          </tr>
           <tr>
            <td>97</td>
            <td>23</td>
          </tr>
           <tr>
            <td>96</td>
            <td>33</td>
          </tr>
           <tr>
            <td>95</td>
            <td>44</td>
          </tr>
           <tr>
            <td>94</td>
            <td>54</td>
          </tr>
          <tr>
            <td>93</td>
            <td>64</td>
          </tr>
       </tbody>
      </table>
    </li>
  </ul>
</details>





