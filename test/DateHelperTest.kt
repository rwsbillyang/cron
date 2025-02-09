/*
 * ```
 * Copyright © 2025 rwsbillyang@qq.com.  All Rights Reserved.
 *
 * Written by rwsbillyang@qq.com at Beijing Time: 2025-02-08 20:34
 *
 * NOTICE:
 * This software is protected by China and U.S. Copyright Law and International Treaties.
 * Unauthorized use, duplication, reverse engineering, any form of redistribution,
 * or use in part or in whole other than by prior, express, printed and signed license
 * for use is subject to civil and criminal prosecution. If you have received this file in error,
 * please notify copyright holder and destroy this and any other copies as instructed.
 * ```
 */

package com.github.rwsbillyang.yinyang.test

import com.github.rwsbillyang.cron.diffDate
import org.junit.Assert
import org.junit.Test
import java.time.LocalDate


class DateHelperTest{
    @Test
    fun test_diffDate1() {
        Assert.assertTrue(diffDate(LocalDate.of(2025,1,31), LocalDate.of(2025,2,1)) == 1)
    }
    @Test
    fun test_diffDate2() {
        Assert.assertTrue(diffDate(LocalDate.of(2025,1,31), LocalDate.of(2025,2,28)) == 28)
    }
    @Test
    fun test_diffDate3() {
        Assert.assertTrue(diffDate(LocalDate.of(2025,1,31), LocalDate.of(2025,1,1)) == -30)
    }

    @Test
    fun test_diffDate4() {
        Assert.assertTrue(diffDate(LocalDate.of(2025,1,31), LocalDate.of(2026,2,1)) == 366)
    }

    @Test
    fun test_diffDate5() {
        Assert.assertTrue(diffDate(LocalDate.of(2025,1,31), LocalDate.of(2025,3,31)) == 59)
    }

    @Test
    fun test_negative_mod() {
        val d = diffDate(LocalDate.of(2025,3,31), LocalDate.of(2025,3,1))
        Assert.assertTrue(d == -30)
        Assert.assertTrue(d % 5 == 0)
        Assert.assertTrue(d % 7 == -2)
        Assert.assertTrue(d % 8 == -6)
        Assert.assertTrue(d % 9 == -3)
    }
}