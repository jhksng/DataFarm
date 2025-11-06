package com.smartfarm.smartfarm_server.repository;

import com.smartfarm.smartfarm_server.model.SensorData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SensorDataRepository extends JpaRepository<SensorData, Long>, JpaSpecificationExecutor<SensorData> {

    Optional<SensorData> findTopByOrderByTimestampDesc();
    List<SensorData> findTop60ByOrderByTimestampDesc();

    // ✅ ① 1분 단위 평균 (최근 60분)
    @Query(value = """
        SELECT DATE_FORMAT(timestamp, '%Y-%m-%d %H:%i:00') AS grouped_time,
               ROUND(AVG(temperature), 2),
               ROUND(AVG(humidity), 2),
               ROUND(AVG(soil_moisture), 2),
               ROUND(AVG(water_level), 2)
        FROM sensor_data
        WHERE timestamp >= (SELECT MAX(timestamp) FROM sensor_data) - INTERVAL 60 MINUTE
        GROUP BY grouped_time
        ORDER BY grouped_time ASC
    """, nativeQuery = true)
    List<Object[]> findMinuteAvgData();

    // ✅ ② 1시간 단위 평균 (최근 24시간)
    @Query(value = """
        SELECT DATE_FORMAT(timestamp, '%Y-%m-%d %H:00:00') AS grouped_time,
               ROUND(AVG(temperature), 2),
               ROUND(AVG(humidity), 2),
               ROUND(AVG(soil_moisture), 2),
               ROUND(AVG(water_level), 2)
        FROM sensor_data
        WHERE timestamp >= (SELECT MAX(timestamp) FROM sensor_data) - INTERVAL 24 HOUR
        GROUP BY grouped_time
        ORDER BY grouped_time ASC
    """, nativeQuery = true)
    List<Object[]> findHourlyAvgData();

    // ✅ ③ 1일 단위 평균 (최근 7일)
    @Query(value = """
        SELECT DATE_FORMAT(timestamp, '%Y-%m-%d') AS grouped_time,
               ROUND(AVG(temperature), 2),
               ROUND(AVG(humidity), 2),
               ROUND(AVG(soil_moisture), 2),
               ROUND(AVG(water_level), 2)
        FROM sensor_data
        WHERE timestamp >= (SELECT MAX(timestamp) FROM sensor_data) - INTERVAL 7 DAY
        GROUP BY grouped_time
        ORDER BY grouped_time ASC
    """, nativeQuery = true)
    List<Object[]> findDailyAvgData();

    // ✅ ④ 1주 단위 평균 (최근 4주)
    @Query(value = """
        SELECT CONCAT(
               YEAR(timestamp), '-',
               LPAD(MONTH(timestamp), 2, '0'),
               '-w',
               CASE 
                   WHEN (
                       WEEK(timestamp, 1)
                       - WEEK(DATE_SUB(timestamp, INTERVAL DAYOFMONTH(timestamp) - 1 DAY), 1)
                       + 1
                   ) > 4 THEN 4
                   ELSE (
                       WEEK(timestamp, 1)
                       - WEEK(DATE_SUB(timestamp, INTERVAL DAYOFMONTH(timestamp) - 1 DAY), 1)
                       + 1
                   )
               END
           ) AS grouped_time,
           ROUND(AVG(temperature), 2),
           ROUND(AVG(humidity), 2),
           ROUND(AVG(soil_moisture), 2),
           ROUND(AVG(water_level), 2)
        FROM sensor_data
        WHERE timestamp >= (SELECT MAX(timestamp) FROM sensor_data) - INTERVAL 4 WEEK
        GROUP BY grouped_time
        ORDER BY grouped_time ASC
    """, nativeQuery = true)
    List<Object[]> findWeeklyAvgData();

    // ✅ ⑤ 1달 단위 평균 (최근 12개월)
    @Query(value = """
        SELECT DATE_FORMAT(timestamp, '%Y-%m') AS grouped_time,
               ROUND(AVG(temperature), 2),
               ROUND(AVG(humidity), 2),
               ROUND(AVG(soil_moisture), 2),
               ROUND(AVG(water_level), 2)
        FROM sensor_data
        WHERE timestamp >= (SELECT MAX(timestamp) FROM sensor_data) - INTERVAL 12 MONTH
        GROUP BY grouped_time
        ORDER BY grouped_time ASC
    """, nativeQuery = true)
    List<Object[]> findMonthlyAvgData();
}
