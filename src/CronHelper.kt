/*
 * ```
 * Copyright © 2025 rwsbillyang@qq.com.  All Rights Reserved.
 *
 * Written by rwsbillyang@qq.com at Beijing Time: 2025-02-04 21:06
 *
 * NOTICE:
 * This software is protected by China and U.S. Copyright Law and International Treaties.
 * Unauthorized use, duplication, reverse engineering, any form of redistribution,
 * or use in part or in whole other than by prior, express, printed and signed license
 * for use is subject to civil and criminal prosecution. If you have received this file in error,
 * please notify copyright holder and destroy this and any other copies as instructed.
 * ```
 */

package com.github.rwsbillyang.cron

import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.abs

val month_days = arrayOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
val month_days_leap_year = arrayOf(31, 29, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)

const val EnableLog = true
inline fun log(msg: String) {
    if (EnableLog) println(msg)
}

fun Int.toBinary(len: Int=32) = String.format(
    "%" + len + "s",
    Integer.toBinaryString(this)
).replace(" ".toRegex(), "0")
fun Int.toHex() = "0x"+this.toString(16).uppercase()

/**
 * 世界通用的格里历（Gregorian Calendar）平常年一年是365天，闰年一年是366天
 * 1、如果年份是 4 的倍数，且不是 100 的倍数，则是闰年；
 * 2、如果年份是 400 的倍数，则是闰年；
 * 3、不满足 1、2 条件的就是平常年。
 * 总结成一句话就是：四年一闰，百年不闰，四百年再闰。
 */
fun isLeap(year: Int) = year % 400 == 0 || (year % 4 == 0 && year % 100 != 0)
inline fun isBitSet(bits: Int, bit: Int):Boolean {
    val m = 1 shl bit
    return bits and m == m
}

/**
 * @param year: should > 0
 * @param month: [0,11]
 */
fun daysInMonth(year: Int, month: Int): Int {
    var y = year
    if (year <= 0) {
        System.err.println("Not yet support year=${year}, set it 1")
        y = 1
    }
    var m = month
    if (month < 0) {
        System.err.println("invalid month=${month}, set 0")
        m = 0
    } else if (month > 11) {
        System.err.println("invalid month={month}, set 11")
        m = 11
    }

    return if (isLeap(y)) month_days_leap_year[m] else month_days[m]
}

/**
 *
 * @returns minutes = (startDateTime，endDateTime]
 * if the minutes(startDateTime > endDateTime) is negative,
 */
fun diffDateTime(startDateTime: LocalDateTime, endDateTime: LocalDateTime) = (abs(diffDate(startDateTime.toLocalDate(), endDateTime.toLocalDate())) * 24L + endDateTime.hour - startDateTime.hour) * 60 + (endDateTime.minute - startDateTime.minute)

/**
 *
 * @returns days = (startDate，endDate]
 *
 * if days is negative, startDate > endDate
 */
fun diffDate(startDate: LocalDate, endDate: LocalDate): Int {
    var days = restDaysInYear(startDate);

    val y = endDate.year - startDate.year;
    if (y == 0) {
        return days - restDaysInYear(endDate)
    } else if (y >= 1) {
        days += daysAmongYears(startDate.year + 1, endDate.year) + passedDays(endDate)
    } else // y<0
    {
        return -diffDate(endDate, startDate)
    }

    return days
}

/**
 * 计算一年中过去的天数，包括指定的这一天
 * */
fun passedDays(d: LocalDate): Int {
    var days = 0
    for (i in 0 until d.month.ordinal) {
        days += daysInMonth(d.year, i)
    }
    days += d.dayOfMonth
    return days
}

/**
 * 计算一年中还剩下的天数，不包括指定的这一天
 *
 */
fun restDaysInYear(d: LocalDate): Int {
    var days = daysInMonth(d.year, d.month.ordinal) - d.dayOfMonth;
    for (i in d.month.ordinal+1 until 12) {
        days += daysInMonth(d.year, i);
    }
    return days;
}

/**
 * 计算[startYear, endYear)整年的天数
 * @param startYear 起始年份，包括该年
 * @param endYear 结束年份，不包括该年
 */
fun daysAmongYears(startYear: Int, endYear: Int): Int {
    if (startYear >= endYear) return 0
    var days = 0
    for (i in startYear until endYear) {
        days += if (isLeap(i)) 366 else 365
    }
    return days
}