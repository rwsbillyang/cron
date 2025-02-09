/*
 * Copyright © 2022 rwsbillyang@qq.com
 *
 * Written by rwsbillyang@qq.com at Beijing Time: 2025-02-07 16:45
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.rwsbillyang.yinyang.test


import com.github.rwsbillyang.cron.Cron
import com.github.rwsbillyang.cron.CustomPeriod
import com.github.rwsbillyang.cron.Result
import org.junit.Assert
import org.junit.Test
import java.time.LocalDateTime

class CronTest {

    @Test
    fun test_hour_minute() {
        //6:30, 12:30, 18:30, 23:30
        val cron = Cron(mday=Cron.AnyValue, hour = (1 shl 6) or (1 shl 12) or (1 shl 18) or (1 shl 23), minute = 30)

        cron.getNext(LocalDateTime.of(2025, 1, 5,20,20,0)){
            Assert.assertTrue(it == Result(2025, 1, 5, 23,30, 190L))
        }

        //minutes causes day+1
        cron.getNext(LocalDateTime.of(2025, 1, 5,23,40,0)){
            Assert.assertTrue(it == Result(2025, 1, 6, 6,30, 410L))
        }

        //minutes causes  month+1
        cron.getNext(LocalDateTime.of(2025, 1, 31,23,40,0)){
            Assert.assertTrue(it == Result(2025, 2, 1, 6,30, 410L))
        }

        //minutes causes  year+1
        cron.getNext(LocalDateTime.of(2024, 12, 31,23,40,0)){
            Assert.assertTrue(it == Result(2025, 1, 1, 6,30, 410L))
        }
    }

    @Test
    fun test_wday_hour_minute() {
        //Monday Wednesday Friday: 6:30, 12:30, 18:30, 23:30
        val cron = Cron( wday = 1 or (1 shl 2) or (1 shl 4), hour = (1 shl 6) or (1 shl 12) or (1 shl 18) or (1 shl 23), minute = 30)

        //2025.1.5 Sunday,
        cron.getNext(LocalDateTime.of(2025, 1, 5,20,20,0)){
            Assert.assertTrue(it == Result(2025, 1, 6, 6,30, 610L))
        }

        //2025.1.5 Sunday, wday causes day+1
        cron.getNext(LocalDateTime.of(2025, 1, 5,23,40,0)){
            Assert.assertTrue(it == Result(2025, 1, 6, 6,30, 410L))
        }

        //2025.1.31 Friday,
        cron.getNext(LocalDateTime.of(2025, 1, 31,23,40,0)){
            Assert.assertTrue(it == Result(2025, 2, 3, 6,30, 3290L))
        }

        //2024.12.31: Tuesday, minutes causes  year+1
        cron.getNext(LocalDateTime.of(2024, 12, 31,23,40,0)){
            Assert.assertTrue(it == Result(2025, 1, 1, 6,30, 410L))
        }

        //2025.2.12 Wednesday
        cron.getNext(LocalDateTime.of(2025, 2, 12,10,0,0)){
            Assert.assertTrue(it == Result(2025, 2, 12, 12,30, 150L))
        }
    }


    @Test
    fun test_customday_hour_minute() {
        //2025.2.7 first day, period=8 ,first and second day are set 1
        val customPeriod = CustomPeriod(3, 8,2025,2,7)

        val cron = Cron(customPeriod = customPeriod, hour = (1 shl 6) or (1 shl 16), minute = 30)

        cron.getNext(LocalDateTime.of(2025, 2, 6,6,20,0)){
            Assert.assertTrue(it == Result(2025, 2, 7, 6,30, 1450L))
        }

        cron.getNext(LocalDateTime.of(2025, 2, 7,5,40,0)){
            Assert.assertTrue(it == Result(2025, 2, 7, 6,30, 50L))
        }


        cron.getNext(LocalDateTime.of(2025, 2, 8,5,40,0)){
            Assert.assertTrue(it == Result(2025, 2, 8, 6,30, 50L))
        }

        cron.getNext(LocalDateTime.of(2025, 2, 9,5,40,0)){
            Assert.assertTrue(it == Result(2025, 2, 15, 6,30, 6*24*60+50L))
        }

        cron.getNext(LocalDateTime.of(2025, 2, 28,6,0,0)){
            Assert.assertTrue(it == Result(2025, 3, 3, 6,30, 24*60*3+30L))
        }

        //2024,12.29 firstday
        cron.getNext(LocalDateTime.of(2024, 12, 29,6,20,0)){
            Assert.assertTrue(it == Result(2024, 12, 29, 6,30, 10L))
        }
        //2024,12.29 firstday
        cron.getNext(LocalDateTime.of(2024, 12, 29,23,40,0)){
            Assert.assertTrue(it == Result(2024, 12, 30, 6,30, 410L))
        }

        //2024,12.29 firstday
        cron.getNext(LocalDateTime.of(2024, 12, 31,23,40,0)){
            Assert.assertTrue(it == Result(2025, 1, 6, 6,30, 24*60*5+410L))
        }
    }

    @Test
    fun test_mday_hour_minute() {
        //29,30,31 6:30, 12:30, 18:30, 23:30
        val cron = Cron(mday = (1 shl 28) or (1 shl 29) or (1 shl 30), hour = (1 shl 6) or (1 shl 12) or (1 shl 18) or (1 shl 23), minute = 30)
        cron.getNext(LocalDateTime.of(2025, 1, 5,23,40,0)){
            Assert.assertTrue(it == Result(2025, 1, 29, 6,30, 24*60*23+410L))
        }

        cron.getNext(LocalDateTime.of(2025, 2, 28,23,40,0)){
            Assert.assertTrue(it == Result(2025, 3, 29, 6,30, 24*60*28+410L))
        }
        cron.getNext(LocalDateTime.of(2024, 2, 28,23,40,0)){
            Assert.assertTrue(it == Result(2024, 2, 29, 6,30, 410L))
        }

        cron.getNext(LocalDateTime.of(2025, 3, 31,23,40,0)){
            Assert.assertTrue(it == Result(2025, 4, 29, 6,30, 24*60*28+410L))
        }

        //days in April is 30
        cron.getNext(LocalDateTime.of(2024, 4, 30,23,40,0)){
            Assert.assertTrue(it == Result(2024, 5, 29, 6,30, 24*60*28+410L))
        }
    }

    @Test
    fun test_wday_mday_hour_minute() {
        //mday: 29,30,31 6:30,Monday Wednesday Friday, 12:30, 18:30, 23:30
        val cron = Cron(mday = (1 shl 28) or (1 shl 29) or (1 shl 30),  wday = 1 or (1 shl 2) or (1 shl 4), hour = (1 shl 6) or (1 shl 12) or (1 shl 18) or (1 shl 23), minute = 30)
        cron.getNext(LocalDateTime.of(2025, 1, 5,23,40,0)){
            Assert.assertTrue(it == Result(2025, 1, 6, 6,30, 410L)) //Monday
        }

        cron.getNext(LocalDateTime.of(2025, 2, 28,23,40,0)){//Friday
            Assert.assertTrue(it == Result(2025, 3, 3, 6,30, 24*60*2+410L))
        }
        cron.getNext(LocalDateTime.of(2024, 2, 28,23,40,0)){//Wednesday
            Assert.assertTrue(it == Result(2024, 2, 29, 6,30, 410L))//Friday
        }

        cron.getNext(LocalDateTime.of(2025, 3, 31,23,40,0)){//Monday
            Assert.assertTrue(it == Result(2025, 4, 2, 6,30, 24*60+410L))//Wednesday
        }

        //days in April is 30
        cron.getNext(LocalDateTime.of(2024, 4, 30,23,40,0)){//Tuesday
            Assert.assertTrue(it == Result(2024, 5, 1, 6,30, 410L))//Wednesday
        }

        cron.getNext(LocalDateTime.of(2024, 12, 31,11,40,0)){//Tuesday
            Assert.assertTrue(it == Result(2024, 12, 31, 12,30, 50L))
        }
        cron.getNext(LocalDateTime.of(2024, 12, 31,23,40,0)){//Tuesday
            Assert.assertTrue(it == Result(2025, 1, 1, 6,30, 410L))//Wednesday
        }
    }

    @Test
    fun test_month_mday_hour_minute() {
        //Jan,Feb, 29,30,31 6:30, 12:30, 18:30, 23:30
        val cron = Cron(
            month= 1 or (1 shl 1),
            mday =  (1 shl 28) or (1 shl 29) or (1 shl 30),
            hour = (1 shl 6) or (1 shl 12) or (1 shl 18) or (1 shl 23),
            minute = 30)
        cron.getNext(LocalDateTime.of(2025, 1, 28,23,40,0)){
            Assert.assertTrue(it == Result(2025, 1, 29, 6,30, 410L))
        }
        cron.getNext(LocalDateTime.of(2025, 2, 28,23,20,0)){
            Assert.assertTrue(it == Result(2026, 1, 29, 6,30, 24*60*334+430L))
        }
        cron.getNext(LocalDateTime.of(2025, 3, 31,23,40,0)){
            Assert.assertTrue(it == Result(2026, 1, 29, 6,30, 24*60*303L+410))
        }
    }


    @Test
    fun test_month_wday_hour_minute() {
        //Jan Feb, Monday Wednesday Friday, 6:30, 12:30, 18:30, 23:30
        val cron = Cron(
            month= 1 or (1 shl 1),
            wday = 1 or (1 shl 2) or (1 shl 4),
            mday = 0,
            hour = (1 shl 6) or (1 shl 12) or (1 shl 18) or (1 shl 23),
            minute = 30)
        cron.getNext(LocalDateTime.of(2025, 1, 5,23,40,0)){//Sunday
            Assert.assertTrue(it == Result(2025, 1, 6, 6,30, 410L))
        }

        cron.getNext(LocalDateTime.of(2025, 2, 28,23,20,0)) {//Friday
            Assert.assertTrue(it == Result(2025, 2, 28, 23, 30, 10L))
        }
        cron.getNext(LocalDateTime.of(2025, 2, 28,23,40,0)){//Friday
            Assert.assertTrue(it == Result(2026, 1, 2, 6,30, 24*60*307L+410))//Friday
        }

        cron.getNext(LocalDateTime.of(2025, 3, 31,23,40,0)){
            Assert.assertTrue(it == Result(2026, 1, 2, 6,30, 24*60*276L+410L))//Friday
        }
    }

    @Test
    fun test_month_customday_hour_minute() {
        //2025.2.7 first day, period=8 ,first and second day are set 1
        val customPeriod = CustomPeriod(3, 8,2025,2,7)
        val cron = Cron(year = 2025, customPeriod = customPeriod, hour = (1 shl 6) or (1 shl 16), minute = 30)

        cron.getNext(LocalDateTime.of(2025, 2, 6,6,20,0)){
            Assert.assertTrue(it == Result(2025, 2, 7, 6,30, 1450L))
        }

        cron.getNext(LocalDateTime.of(2025, 2, 7,5,40,0)){
            Assert.assertTrue(it == Result(2025, 2, 7, 6,30, 50L))
        }


        cron.getNext(LocalDateTime.of(2025, 2, 8,5,40,0)){
            Assert.assertTrue(it == Result(2025, 2, 8, 6,30, 50L))
        }

        cron.getNext(LocalDateTime.of(2025, 2, 9,5,40,0)){
            Assert.assertTrue(it == Result(2025, 2, 15, 6,30, 6*24*60+50L))
        }

        cron.getNext(LocalDateTime.of(2025, 2, 28,6,0,0)){
            Assert.assertTrue(it == Result(2025, 3, 3, 6,30, 24*60*3+30L))
        }


        //2024,12.29 firstday
        cron.getNext(LocalDateTime.of(2024, 12, 29,23,40,0)){
            Assert.assertTrue(it == Result(2025, 1, 6, 6,30, 24*60*7+410L))
        }

        //2024,12.29 firstday
        cron.getNext(LocalDateTime.of(2024, 12, 31,23,40,0)){
            Assert.assertTrue(it == Result(2025, 1, 6, 6,30, 24*60*5+410L))
        }

        cron.getNext(LocalDateTime.of(2026, 1, 2,6,20,0)){
            //System.err.println("after 2026-1-2 6:20, result.year=${it?.year}")
            Assert.assertNull(it)
        }
    }

    @Test
    fun test_year_month_wday_hour_minute() {
        //Jan Feb, Monday Wednesday Friday, 6:30, 12:30, 18:30, 23:30
        val cron = Cron(
            year = 2025,
            month= 1 or (1 shl 1),
            wday = 1 or (1 shl 2) or (1 shl 4),
            mday = 0,
            hour = (1 shl 6) or (1 shl 12) or (1 shl 18) or (1 shl 23),
            minute = 30)
        cron.getNext(LocalDateTime.of(2025, 1, 5,23,40,0)){//Sunday
            Assert.assertTrue(it == Result(2025, 1, 6, 6,30, 410L))
        }

        cron.getNext(LocalDateTime.of(2025, 2, 28,23,20,0)) {//Friday
            Assert.assertTrue(it == Result(2025, 2, 28, 23, 30, 10L))
        }
        cron.getNext(LocalDateTime.of(2025, 2, 28,23,40,0)){//Friday
            //System.err.println("after 2025-1-28 23:40, result.year=${it?.year}")
            Assert.assertNull(it)
        }

        cron.getNext(LocalDateTime.of(2025, 3, 31,23,40,0)){
            Assert.assertNull(it)
        }
    }

    @Test
    fun test_year_month_mday_hour_minute() {
        //Jan,Feb, 29,30,31 6:30, 12:30, 18:30, 23:30
        val cron = Cron(
            year = 2025,
            month= 1 or (1 shl 1),
            mday =  (1 shl 28) or (1 shl 29) or (1 shl 30),
            hour = (1 shl 6) or (1 shl 12) or (1 shl 18) or (1 shl 23),
            minute = 30)
        cron.getNext(LocalDateTime.of(2025, 1, 28,23,40,0)){
            Assert.assertTrue(it == Result(2025, 1, 29, 6,30, 410L))
        }
        cron.getNext(LocalDateTime.of(2025, 2, 28,23,20,0)){
            Assert.assertTrue(it == null)
        }
        cron.getNext(LocalDateTime.of(2025, 3, 31,23,40,0)){
            Assert.assertTrue(it == null)
        }
    }
}