/*
 * ```
 * Copyright © 2025 rwsbillyang@qq.com.  All Rights Reserved.
 *
 * Written by rwsbillyang@qq.com at Beijing Time: 2025-02-07 16:10
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

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Year




/**
 * The nearest coming datetime and distance since a datetime
 * @param year [Year.MIN_VALUE, Year.MAX_VALUE]
 * @param month [1,12]
 * @param day [1,31]
 * @param hour [0,23]
 * @param minute [0,59]
 * @param distanceMinutes unit: minutes
 * */
class Result(
    var year: Int,
    var month: Int,//[1,12] //与 LocalDate/LocalDateTime保持一致
    var day: Int,//[1,31]//与 LocalDate/LocalDateTime保持一致
    var hour: Int,//[0,23]
    var minute: Int,//[0,59]

    var distanceMinutes: Long
){
    override fun equals(other: Any?) = other != null && other is Result && this.year == other.year && this.month == other.month && this.day == other.day && this.hour == other.hour && this.minute == other.minute && this.distanceMinutes == other.distanceMinutes
    override fun hashCode(): Int {
        var result = year
        result = 31 * result + month
        result = 31 * result + day
        result = 31 * result + hour
        result = 31 * result + minute
        result = 31 * result + distanceMinutes.hashCode()
        return result
    }
}

/**
 * @param bits the cron bits, Cron.AnyValue means *，the bits in bit[0~30] means days of a period
 * @param period if 7, it is a week
 * @param firstYear the year of LocalDate
 * @param firstMonth the month of LocalDate, range [1,12]
 * @param firstDay the month day of LocalDate, range [1,31]
 * */
class CustomPeriod(
    val bits: Int, //Int.MAX_VALUE(0x0FFFFFFF)表示*
    val period: Int,//自定义周期,需<=31，否则bits不够用
    val firstYear: Int, //周期的第一天是哪一年
    val firstMonth: Int,//周期的第一天是哪一天 值范围[1,12]
    val firstDay: Int,//周期的第一天是哪一天 值范围[1,31]
)

/**
 * @param year the value Cron.AnyYear(zero) means *, else it is the specified year
 * @param month Cron.AnyValue(Int.MAX_VALUE(0x7FFFFFFF)) means *，the bits in bit[0~11] means months
 * @param mday Cron.AnyValue means *，the bits in bit[0~30] means days of month
 * @param wday Cron.AnyValue means *，the bits in bit[0~6] means days of week
 * @param hour Cron.AnyValue means *，the bits in bit[0~23] means hour of time
 * @param minute Cron.AnyValue means *，else: the value is the minute of time
 * @param customPeriod the customized period, week is a customPeriod which period=7
 *
 * Note: should set value for one ore more of mday/wday/customPeriod
 * */
class Cron(
    val year: Int = Cron.AnyYear,// 0表示*,否则为具体哪一年
    val month: Int = Cron.AnyValue, //Int.MAX_VALUE(0x7FFFFFFF)表示*，bit[0~11] 表示1~12月
    val mday: Int = 0, //Int.MAX_VALUE(0x7FFFFFFF)表示*, bit[0~30] 表示1~31日,每月1,3,5号响铃，则0x15
    val wday: Int = 0, //Int.MAX_VALUE(0x7FFFFFFF)表示*，bit[0~6]  表示周一到周日,如周一到周五每天响铃，则0x1F
    val hour: Int = Cron.AnyValue, //Int.MAX_VALUE(0x7FFFFFFF)表示*，bit[0~23] 表示哪些小时
    val minute: Int = Cron.AnyValue, // Int.MAX_VALUE(0x7FFFFFFF)表示*,否则为具体的分钟数

    val customPeriod: CustomPeriod? = null
) {
    companion object {
        const val AnyYear = 0
        const val AnyValue = Int.MAX_VALUE
    }

    /**
     * 当前正计计划计算哪个时间刻度
     */
    private enum class UpdateWhich { Year, Month, Day, Hour, Minute, Finished}
    private data class UpdateMsg(val which: UpdateWhich, val since: Int, val isFromZeroBit: Boolean?)

    var since: LocalDateTime = LocalDateTime.now()
    //下面存放的是检索的结果
    val result = Result(0,0,0, 0, 0, 0L)
    var isFinished = false //是否结束
    var err: String? = null //结果错误信息,若不空表示出错了

    /**
     * @param sinceDateTime any local datetime, often it is now
     * @param onFinished the callback notifys the result of the nearest datetime and distance. null if not found.
     * */
    fun getNext(sinceDateTime: LocalDateTime, onFinished: (r: Result?)->Unit ) = runBlocking{
        if(!checkValid())
        {
            //System.err.println("not valid")
            throw Exception("cron is invalid, please check")
            //return@runBlocking
        }
        //reset result
        resetResult()

        since = sinceDateTime
        onFinishedCb = onFinished

        launch{
            sendUpdateMsg(UpdateWhich.Year, since.year, null)
        }

        launch{
            handleUpdateMsgLoop()
        }
        //joinAll(job1, job2)

        //log("exit getNext")
    }

    /**
     * 检查cron设置的有效性，是否设置错误
     * */
    private fun checkValid(): Boolean {
        if (year < Year.MIN_VALUE || year > Year.MAX_VALUE) {
            System.err.println("invalid year=${year}")
            return false
        }
        if (month != AnyValue && (month < 0 || month >= (2 shl 12)) ) {
            System.err.println("invalid month=${month.toBinary()}, should set 1 at bit: [0,11]")
            return false
        }

        if (mday == 0 && wday == 0 && (customPeriod == null || customPeriod.bits == 0)) {
            System.err.println("invalid mday=${mday.toBinary()} or wday=${wday.toBinary()} or customDay=${customPeriod?.bits?.toBinary()}, should not set 1 at least one bit")
            return false
        }

        if(mday != AnyValue && mday < 0)
        {
            System.err.println("invalid mday=${mday.toBinary()}, should not set 1 at bit: [0,30]")
            return false
        }

        if(wday != AnyValue && (wday < 0 || wday >= (2 shl 7)))
        {
            System.err.println("invalid wday=${wday.toBinary()}, should set 1 at bit: [0,6]")
            return false
        }

        if(hour != AnyValue  && (hour < 0 || hour >= (1 shl 24)))
        {
            System.err.println("invalid hour=${hour.toBinary()}, should set 1 at bit: [0,23]")
            return false
        }

        if(minute != AnyValue && (minute < 0 || minute > 59))
        {
            System.err.println("invalid minute=${minute.toBinary()}, should between [0,59]")
            return false
        }

        if (customPeriod != null) {
            if (customPeriod.bits != AnyValue && (customPeriod.bits  >= (1 shl customPeriod.period) || customPeriod.bits  < 0)) {
                System.err.println("invalid customPeriod=${customPeriod.bits.toBinary()}")
                return false
            }
            if (customPeriod.firstYear < Year.MIN_VALUE || customPeriod.firstYear > Year.MAX_VALUE) {
                System.err.println("invalid firstYear=${customPeriod.firstYear}")
                return false
            }

            if (customPeriod.firstMonth < 1 || customPeriod.firstMonth > 12) {
                System.err.println("invalid firstMonth=${customPeriod.firstMonth}")
                return false
            }

            if (customPeriod.firstDay < 1 || customPeriod.firstDay > 31) {
                System.err.println("invalid firstDay=${customPeriod.firstDay}")
                return false
            }
        }

        return true
    }

    private var onFinishedCb: ((r: Result?)->Unit)? = null
    private val channel = Channel<UpdateMsg>(1)
    private fun resetResult()
    {
        result.year = 0
        result.month = 0
        result.day = 0
        result.hour = 0
        result.minute = 0
        result.distanceMinutes = 0L
    }


    private suspend fun handleUpdateMsgLoop() {
        while (true) {
            for (msg in channel) {
                //log("to handle msg=$msg")
                when (msg.which) {
                    UpdateWhich.Year -> updateYear(msg.since, msg.isFromZeroBit)
                    UpdateWhich.Month -> updateMonth(msg.since, msg.isFromZeroBit!!)
                    UpdateWhich.Day -> updateDay(msg.since, msg.isFromZeroBit!!)
                    UpdateWhich.Hour -> updateHour(msg.since, msg.isFromZeroBit!!)
                    UpdateWhich.Minute -> updateMinute(msg.since, msg.isFromZeroBit!!)
                    UpdateWhich.Finished -> {
                        log("call cb, and exit loop")
                        onFinishedCb?.invoke(if(result.year == 0) null else result)
                        return
                    }
                }
            }
        }
    }

    private fun sendUpdateMsg(which: UpdateWhich, since: Int, isFromZeroBit: Boolean?) = runBlocking {
        val msg = UpdateMsg(which,since, isFromZeroBit)
        //log("\nto send msg=$msg")
        log("\n")
        channel.send(msg)
        //log("send msg done!")
    }

    private fun finish(errMsg: String?) {
        isFinished = true
        err = errMsg

        if (errMsg == null){
            result.distanceMinutes = diffDateTime(since, LocalDateTime.of(result.year, result.month, result.day, result.hour, result.minute, 0))
            log("Done! got: ${result.year}-${result.month}-${result.day} ${result.hour}:${result.minute} ,distance=${result.distanceMinutes} minutes")
        }
        else{
            resetResult()
            System.err.println("Error: $errMsg")
        }

        sendUpdateMsg(UpdateWhich.Finished, 0, null)
    }

    /**
     * 检索决定year的值
     *
     * @param sinceYear year值，可能为AnyYear，或具体的年份值
     * @param isFromScratch  若为空，自行判断，否则使用forceFromScratch,
     * 首次自year->month->day->hour->minute 首次检索时由上一级决定，对于year，首次为null，
     * 若回溯时，则通常因进位而设置为true，比如中间环节，如因minute较小往回跳到下一日时重新检索时，将设置为true
     * */
    private fun updateYear(sinceYear: Int, isFromScratch: Boolean?) {
        log("updateYear, sinceYear=$sinceYear, isFromScratch=$isFromScratch")
        isFinished = false

        //任意年份，即年份未作要求, 与since同年，cron中后面的day/hour/minute取[since之后]的最小的
        if (year == AnyYear) {
            result.year = sinceYear
            log("cron.year is *, get y=$sinceYear, to update month...")
            sendUpdateMsg(UpdateWhich.Month, if(isFromScratch==true) 0 else since.month.ordinal, isFromScratch ?: false)
            return
        } else {//cron年份为具体的值
            if (year == sinceYear)//指定了具体的年份值为正好是since相同的一年，cron中后面的day/hour/minute取[since之后]的最小的
            {
                result.year = year
                log("cron.year=$year=sinceYear ,get y=$sinceYear, to update month isFromScratch=$isFromScratch...")
                sendUpdateMsg(UpdateWhich.Month, if(isFromScratch==true) 0 else since.month.ordinal, isFromScratch ?: false)
                return
            } else if (year > sinceYear)//cron指定的具体的年份year是since之后的年，cron中后面的day/hour/minute取[0之后]的最小的
            {
                result.year = year
                log("cron.year>$sinceYear , get y=$year, to update month from scratch...")
                sendUpdateMsg(UpdateWhich.Month, 0, true)
                return
            } else //cronYear< sinceYear//没有符合要求的时间日期
            {
                finish("not found because cron.year=${year} is before since(${sinceYear})")
                return
            }
        }
    }

    /**
     * 检索决定month的值
     *
     * @param sinceBit 月份索引 值范围[0,11] 自sinceBit位开始检索cron中哪一位置1了
     * @param isFromScratch  若为true，传递给下一级的全部为true；若为false,可自行决定
     * 首次自year->month->day->hour->minute 首次检索时由上一级决定，对于year，首次为null，
     * 若回溯时，则通常因进位而设置为true，比如中间环节，如因minute较小往回跳到下一日时重新检索时，将设置为true
     * */
    private fun updateMonth(sinceBit: Int, isFromScratch: Boolean) {
        log("updateMonth, isFromScratch=$isFromScratch")
        if(month == AnyValue)
        {
            result.month = sinceBit + 1
            log("cron.month is * , get result.month=${result.month} of now.month, to update day in same month ...")
            sendUpdateMsg(UpdateWhich.Day, if(isFromScratch) 0 else since.dayOfMonth-1, isFromScratch)
            return
        }

        if (isFromScratch) {//下一年或下几个月的情形
            //若sinceMonth为0，必有值，除非cron.month=0
            //若指定的是某个周期的第几天，或者是31天，又指定了某些月份，恰好这几个月份不存在这几天，就有可能找不到
            val p = getNext(month, sinceBit, 12, false)
            if (p == null) {
                if (sinceBit == 0){
                    finish( "invalid cron.month=${month.toBinary()}")
                    return
                }else{
                    log("not found month sinceBit=$sinceBit")

                    //上面的[sinceBit,12)中没有设置位，再从头开始找，若找到了，说明下一年中有设置
                    val p2 = getNext(month, 0, 12, false)
                    if(p2 == null){
                        finish( "invalid cron.month=${month.toBinary()}")
                        return
                    }else{
                        log("updateYear again from next year=${result.year + 1}, because monthIndex between [$sinceBit] is not set 1")
                        sendUpdateMsg(UpdateWhich.Year, result.year + 1, true)
                        return
                    }
                }
            } else {
                result.month = p.first + 1
                log("cron.month=${month.toBinary()} , got m=${result.month}, to update day from scratch ...")
                sendUpdateMsg(UpdateWhich.Day, 0, true)
            }
        } else {//本年情形
            //本应分别检查sinceMonth之前和之后的bit位，然后做出不同的处理
            //现改为一次检查所有month比特位，根据结果分别做处理
            val p = getNext(month, sinceBit, 12, true)
            if (p == null) {
                finish("invalid cron.month=${month.toBinary()}")
                return
            } else {
                if (p.first < sinceBit)//[since,11]没有置1，也就是本月及之后的月份没有设置，只能是下一年的since之前的月有置1
                {
                    //下一年，检查是否置1了，只有*才符合条件，否则可能最终无结果
                    log("updateYear again from next year=${result.year + 1}, because monthIndex=$${p.first} between [0,$sinceBit]")
                    sendUpdateMsg(UpdateWhich.Year, result.year + 1, true)
                    return
                } else {
                    result.month = p.first + 1
                    if (p.second == 0)//same as now 本年本月
                    {
                        log("cron.month=${month.toBinary()} , get result.month=${result.month} of now.month, to update day in same month ...")
                        sendUpdateMsg(UpdateWhich.Day, since.dayOfMonth - 1, false)
                    } else//[since.month.ordinal,11]
                    {
                        log("cron.month=${month.toBinary()} , get result.month=${result.month} after now.month, to update day from scratch ...")
                        sendUpdateMsg(UpdateWhich.Day, 0, true)
                    }
                }
            }
        }
    }

    /**
     * 从星期或自定义周期，或dayOnfMonth中，找到最近的一天
     * 若配置了wday,则计算 (y,m,0)这一日的wday
     * 若配置了customPeriod，则计算(y,m,0)这一日的customDay
     *
     * @param sinceBit dayOfMonth的索引，值范围[0,30]
     * @param isFromScratch  若为true，传递给下一级的全部为true；若为false,可自行决定
     * 首次自year->month->day->hour->minute 首次检索时由上一级决定，对于year，首次为null，
     * 若回溯时，则通常因进位而设置为true，比如中间环节，如因minute较小往回跳到下一日时重新检索时，将设置为true
     * */
    private fun updateDay(sinceBit: Int, isFromScratch: Boolean) {
        log("updateDay, isFromScratch=$isFromScratch, check ${result.year}-${result.month}-${sinceBit+1}")
        var w_d: Int = Int.MAX_VALUE
        var c_d: Int = Int.MAX_VALUE
        var d_d: Int = Int.MAX_VALUE

        //val sinceDay = if(isFromScratch) 0 else since.dayOfMonth - 1
        val newSince = LocalDate.of(result.year, result.month, sinceBit+1)
        //星期cron有设置
        if (wday > 0) {//星期有设置
            log("check wday=${wday.toBinary()}")
            if(wday == AnyValue){
                result.day = sinceBit + 1
                log("wday is *, got d=${result.day}, to update hour...")
                sendUpdateMsg(UpdateWhich.Hour, if(isFromScratch)0 else since.hour, isFromScratch)
                return
            }else{
                val sinceBit2 = newSince.dayOfWeek.ordinal
                val p = getNext(wday, sinceBit2, 7, true) //必有值，除非值设置有问题
                if (p == null) {
                    finish("invalid cron.wday=${wday.toBinary()}")
                    return
                } else {
                    w_d = p.second //从 y-m-1起开始的距离
                    log("get wday distance=$w_d, wday-bit=${p.first}")
                }
            }
        }

        //自定义周期有设置
        if (customPeriod != null) {
            log("check customDay=${customPeriod.bits.toBinary()}...")
            if(customPeriod.bits == AnyValue)
            {
                result.day = sinceBit + 1
                log("customDay is *, got d=${result.day}, to update hour...")
                sendUpdateMsg(UpdateWhich.Hour, if(isFromScratch)0 else since.hour, isFromScratch)
                return
            }else{
                val customFirstDay = LocalDate.of(customPeriod.firstYear, customPeriod.firstMonth, customPeriod.firstDay)
                var sinceBit2 = diffDate(customFirstDay, newSince) % customPeriod.period//since是自定义周期的第几日索引
                if(sinceBit2 < 0) sinceBit2 += customPeriod.period //注意：负数求余问题
                val p = getNext(customPeriod.bits, sinceBit2, customPeriod.period, true) //必有值，除非值设置有问题
                if (p == null) {
                    finish("invalid cron.customDay=$${customPeriod.bits.toBinary()}")
                    return
                } else {
                    c_d = p.second//距离
                    log("get customDay distance=$c_d, customDay=${p.first}, sinceBit=$sinceBit2")
                }
            }
        }

        if (mday > 0) {
            if(mday == AnyValue)
            {
                result.day = sinceBit + 1
                log("mday is *, got d=${result.day}, to update hour...")
                sendUpdateMsg(UpdateWhich.Hour, if(isFromScratch) 0 else since.hour, isFromScratch)
                return
            }
            val maxDaysOfMonth = daysInMonth(result.year, result.month - 1)
            val p = getNext(mday, sinceBit, maxDaysOfMonth, true)
            if (p == null)//未找到，
            {
                if (maxDaysOfMonth == 31) {//直至maxDaysOfMonth为31为止，因只有31才能检索了所有比特位
                    finish("invalid cron.day=${mday.toBinary()}")
                    return
                } else {//有可能是该月份不足31或30天，而cron中的设置却是31日或30日，此时需在下一个月中寻找
                    //go next month, 重新开始，月往下进一位，甚至引起年往前进一位
                    val sinceAfterPlusOneMonth = newSince.plusMonths(1)

                    if (result.year != sinceAfterPlusOneMonth.year)//年进位
                    {
                        println("not found day in month=${newSince.month.name}, go to check next year of ${result.year}...")
                        sendUpdateMsg(
                            UpdateWhich.Year,
                            sinceAfterPlusOneMonth.year,
                            true
                        )// re-start update year
                    } else {//月进位
                        println("not found day in month=${newSince.month.name} but in same year, go to check nextMonth=${sinceAfterPlusOneMonth.month.name}...")
                        sendUpdateMsg(
                            UpdateWhich.Month,
                            sinceAfterPlusOneMonth.month.ordinal,
                            true
                        )
                    }
                    return
                }
            } else {//找到了
                d_d = p.second
                if (p.first < sinceBit)//是下个月中的一天
                {
                    log("get mday distance=$d_d, but day=${p.first} in next month")
                } else//当月中的一天
                {
                    log("get mday distance=$d_d, day=${p.first} in same month")
                }
                //上面区分是本月还是下月后，不处理，留着与wday和customDay比较，
                // 谁才是最近的一天后，再处理年月进位问题
            }
        }

        //谁最近
        val min_d1 = if (w_d < c_d) w_d else c_d
        val min_d = if (min_d1 < d_d) min_d1 else d_d

        //都为默认值，意味着都未设置，cron设置出错了
        if (min_d == Int.MAX_VALUE) {
            finish("not set mday=${mday.toBinary()}, wday=${wday.toBinary()}, customDay=${customPeriod?.bits?.toBinary()}")
            return
        }

        //处理年月进位
        val newSince2 = newSince.plusDays(min_d.toLong())//distance， 加上sinceDate，即得到d
        
        log("get min_d distance=$min_d, get latest newSince ${newSince2.year}-${newSince2.month}-${newSince2.dayOfMonth}")

        if (newSince2.year > result.year)//年进位
        {
            log("to updateYear since newSince2.year=${newSince2.year}")
            sendUpdateMsg(UpdateWhich.Year, newSince2.year, true)
        } else if (newSince2.month.ordinal != result.month - 1)//只是月进位
        {
            log("to updateMonth again since y=${result.year},newSince2.month:${newSince2.month}")
            sendUpdateMsg(UpdateWhich.Month, newSince2.month.ordinal, true)
        } else {//均未进位，本年月日中找到了
            result.day = newSince2.dayOfMonth
            log("got day=${result.day} in same month: ${result.year}-${result.month}, to update hour...")
            val fromScratch = isFromScratch || (result.day != since.dayOfMonth) //result.day和since.dayOfMonth有可能相等，因为min_d可能为0
            sendUpdateMsg(UpdateWhich.Hour, if(fromScratch)0 else since.hour, fromScratch)
        }
    }

    /**
     * @param sinceBit hour，值范围[0,23]
     * @param isFromScratch  若为true，传递给下一级的全部为true；若为false,可自行决定
     * 首次自year->month->day->hour->minute 首次检索时由上一级决定，对于year，首次为null，
     * 若回溯时，则通常因进位而设置为true，比如中间环节，如因minute较小往回跳到下一日时重新检索时，将设置为true
     * */
    private fun updateHour(sinceBit: Int, isFromScratch: Boolean) {
        log("updateHour,sinceHour=$sinceBit, isFromScratch=$isFromScratch")
        if(hour == AnyValue){
            result.hour = sinceBit
            log("cron.hour is * , got h=${result.hour} of now.hour, to update minute in same hour ...")
            sendUpdateMsg(UpdateWhich.Minute, if(isFromScratch)0 else since.minute, isFromScratch)
            return
        }

        if (isFromScratch) {
            val p = getNext(hour, sinceBit, 24, false)
            if (p == null) {
                val err =
                    if (sinceBit == 0) "invalid cron.hour=${hour.toBinary()}" else "not found hour sinceHour=$sinceBit"
                finish(err)
                return
            } else {
                result.hour = p.first
                log("cron.hour=${hour.toBinary()} , get h=${result.hour}, to update minute from scratch ...")
                sendUpdateMsg(UpdateWhich.Minute, 0, true)
                return
            }
        } else {
            //本应分别检查since之前和之后的bit位，然后做出不同的处理
            //现改为一次检查所有比特位，根据结果分别做处理
            val p = getNext(hour, sinceBit, 24, true)
            if (p == null) {
                finish("invalid cron.hour=${hour.toBinary()}, sinceHour=$sinceBit")
                return
            } else {
                val foundHour = p.first
                if (foundHour < sinceBit)//[since,23]没有置1，也就是得在下一日中，从0时开始寻找
                {
                    //sinceHour之前的小时只能在下一天，day进位又肯能引起月进位，月又可能引起年进位
                    val newSince = LocalDate.of(result.year, result.month, result.day).plusDays(1)
                    if (newSince.year > result.year)//年进位
                    {
                        log("to updateYear because foundHour=$foundHour before sinceHour=$sinceBit cause day+1, then month+1, then year+1")
                        sendUpdateMsg(UpdateWhich.Year, newSince.year, true)
                    } else if (newSince.month.ordinal != result.month-1)//月进位
                    {
                        log("to updateMonth because foundHour=$foundHour before sinceHour=$sinceBit cause day+1, then month+1")
                        sendUpdateMsg(UpdateWhich.Month, newSince.month.ordinal, true)
                    } else {//日进位
                        log("to updateDay because foundHour=$foundHour before sinceHour=$sinceBit cause day+1")
                        sendUpdateMsg(UpdateWhich.Day, newSince.dayOfMonth - 1, true)
                    }

                    return
                } else if (foundHour == sinceBit) {
                    result.hour = p.first
                    log("cron.hour=${hour.toBinary()} , got h=${result.hour} of now.hour, to update minute in same hour ...")
                    sendUpdateMsg(UpdateWhich.Minute, since.minute, false)
                    return
                } else//foundHour > sinceHour: 在本小时后找到了hour时间，即[since.hour.ordinal,23]有设置
                {
                    result.hour = p.first
                    log("cron.hour=${hour.toBinary()} , got h=${result.hour} after now.hour, to update minute from scratch ...")
                    sendUpdateMsg(UpdateWhich.Minute, 0, true)
                    return
                }
            }
        }
    }

    /**
     * @param sinceMinute 要么为AnyValue，要么具体的值，值范围[0,59]
     * @param isFromScratch  若为true，传递给下一级的全部为true；若为false,可自行决定
     * 首次自year->month->day->hour->minute 首次检索时由上一级决定，对于year，首次为null，
     * 若回溯时，则通常因进位而设置为true，比如中间环节，如因minute较小往回跳到下一日时重新检索时，将设置为true
     * */
    private fun updateMinute(sinceMinute: Int, isFromScratch: Boolean)
    {
        log("updateMinute,sinceMinute=$sinceMinute, isFromScratch=$isFromScratch")
        //cron中设置为任意值，或从0开始的值均可
        if(minute == AnyValue){
            result.minute = sinceMinute
            log("cron.minute is *, got minute=${sinceMinute} ")
            finish(null)//successful
        }else{
            //正在寻找的minute在sinceMinute之后，符合条件
            if(minute >= sinceMinute){
                result.minute = minute
                log("cron.minute=$minute, got minute=$minute which is after sinceMinute=${sinceMinute} ")
                finish(null)//successful
            }else{
                if(isFromScratch)
                {
                    result.minute = minute
                    log("cron.minute=$minute, got minute=$minute which is before sinceMinute=${sinceMinute}, but isFromScratch=true")
                    finish(null)//successful
                }
                else{
                    //cron中指定的minute在sinceMinute之前，下一小时
                    val newSince = LocalDateTime.of(result.year, result.month, result.day, result.hour,0,0).plusHours(1)
                    val foundMinute= minute
                    if (newSince.year > result.year)//年进位
                    {
                        log("to updateYear because foundMinute=$foundMinute before sinceMinute=$sinceMinute cause hour+1->day+1->month+1->year+1")
                        sendUpdateMsg(UpdateWhich.Year, newSince.year, true)
                    } else if (newSince.month.ordinal != result.month-1)//月进位
                    {
                        log("to updateMonth because foundMinute=$foundMinute before sinceMinute=$sinceMinute cause hour+1->day+1->month+1")
                        sendUpdateMsg(UpdateWhich.Month, newSince.month.ordinal, true)
                    } else {//日进位
                        log("to updateDay because foundMinute=$foundMinute before sinceMinute=$sinceMinute cause hour+1->day+1")
                        sendUpdateMsg(UpdateWhich.Day, newSince.dayOfMonth - 1, true)
                    }
                }
            }
        }
    }


    /**
     * cronMonth必须至少有1bit被设置，或为*
     * @param cron cron中的bits设置  0x7FFFFF(*) means any, or any bit is set 1
     * @param since 从哪个bit开始检索 the index in a period: [0, period]
     * @param period bits一共几位，即一个周期，每个索引值对应一位 the bit count, the cycle, eg: week period=7, hour period=24, month period=12, day in month period= 31, cron is its bits
     * @param checkAllBits 从since搜索时，若checkAllBits == false， 则检查到最高位时结束，即检索[since,period)比特位；
     * 否则检索高位[since,period)结束后，又从0开始检索，即检索[0, since)是否置1了
     *
     * @return first: 置1的bit所在位置，second：与since的距离
     *
     * 对于固定周期来说，比如一周7天，cron整数正的低7比特位[0,6]，小时则为[0,23]比特位，哪一bit被设置为1，则表示该位被选中有值
     * 第1个参数cron表示cron表达式中设置的值，0xFFFF FFFF(*)表示不限制，即任何值，对应的cron比特位均为1即可
     * 第2个参数period，表示周期，也就是要使用的比特位数量
     * 第3个参数fromBit，从哪个比特位开始比较，判断是否被设置。
     * 若借位，从fromBit下一个bit位开始检查比特位是否被设置；无借位，从fromBit开始搜索，相当于从当前（月日时等）开始检查搜索。
     * 对于最近的日期时间，fromBit则是now对应的比特位开始搜索比较。若有借位，相当于从下一个（月日时等）开始检查搜索。
     *
     *  Note：对于月份中的第几日来说，每月的天数不相同，则返回结果有问题。
     *  比如，当设置mday为31日时，亦即cron中的bit[30]=1，而月份设置为2,4,6等月时，即月份的cron的bit[1,3,5]=1，若将周期period设为30则永远找不到结果，应该返回-1出错
     *  即使月份设置为1,3等月有31天时，即月份的cron的bit[0,2]=1，应该动态调整搜索period，并且返回值：到fromBit的距离，应该记录下所有循环过程
     * */
    private fun getNext(cron: Int, since: Int, period: Int, checkAllBits: Boolean): Pair<Int, Int>? {
        if (cron == AnyValue) {//* 任意值, 与since相同
            return Pair(since, 0)
        } else {
            if (checkAllBits) {
                for (i in 0 until period) {
                    val bit = (since + i) % period
                    if (isBitSet(cron, bit))
                        return Pair(bit, i)
                }
            } else {
                for (i in since until period) {
                    if (isBitSet(cron, i))
                        return Pair(i, i - since)
                }
            }
        }
        return null
    }
}


fun main()
{
    test_month_customday_hour_minute()
}

fun test_hour_minute(){
    //every day 6:30, 12:30, 18:30, 23:30
    val cron = Cron(hour = (1 shl 6) or (1 shl 12) or (1 shl 18) or (1 shl 23), minute = 30)
    cron.getNext(LocalDateTime.of(2025, 1, 5,20,20,0)){
        log("ok=" + (it == Result(2025, 1, 5, 23,30, 190L)))
    }

    //minutes causes day+1
    cron.getNext(LocalDateTime.of(2025, 1, 5,23,40,0)){
        log("ok=" + (it == Result(2025, 1, 6, 6,30, 410L)))
    }

    //minutes causes  month+1
    cron.getNext(LocalDateTime.of(2025, 1, 31,23,40,0)){
        log("ok=" + (it == Result(2025, 2, 1, 6,30, 410L)))
    }

    //minutes causes  year+1
    cron.getNext(LocalDateTime.of(2024, 12, 31,23,40,0)){
        log("ok=" + (it == Result(2025, 1, 1, 6,30, 410L)))
    }
}

fun test_customday_hour_minute() {
    //2025.2.7 is first day, period=8 ,first and second day are set 1
    val customPeriod = CustomPeriod(3, 8,2025,2,7)
    val cron = Cron(mday = 0, customPeriod = customPeriod,
        hour = (1 shl 6) or (1 shl 16), minute = 30 //6:30, 16:30
    )

    //2024,12.29 first day
    cron.getNext(LocalDateTime.of(2024, 12, 30,5,40,0)){
        log("ok=" + (it == Result(2024, 12, 30, 6,30, 50L)))
    }

    cron.getNext(LocalDateTime.of(2024, 12, 31,23,40,0)){
        log("ok=" + (it == Result(2025, 1, 6, 6,30, 24*60*5+410L)))
    }

}

fun test_mday_hour_minute() {
    val cron = Cron(
        mday = (1 shl 28) or (1 shl 29) or (1 shl 30), //29,30,31
        hour = (1 shl 6) or (1 shl 12) or (1 shl 18) or (1 shl 23), minute = 30 // 6:30, 12:30, 18:30, 23:30
    )
    cron.getNext(LocalDateTime.of(2025, 2, 28,23,40,0)){
        log("ok=" + (it == Result(2025, 3, 29, 6,30, 24*60*28+410L)))
    }
}

fun test_month_mday_hour_minute() {
    val cron = Cron(
        month= 1 or (1 shl 1),//Jan Feb
        mday = (1 shl 28) or (1 shl 29) or (1 shl 30),//29,30,31
        hour = (1 shl 6) or (1 shl 12) or (1 shl 18) or (1 shl 23), minute = 30 //6:30, 12:30, 18:30, 23:30
    )

    cron.getNext(LocalDateTime.of(2025, 2, 28,23,20,0)){
        log("ok=" + (it == Result(2026, 1, 29, 6,30, 24*60*334+430L)))//ok=true
    }
}

fun test_month_wday_hour_minute() {
    val cron = Cron(
        month= 1 or (1 shl 1),//Jan Feb
        wday = 1 or (1 shl 2) or (1 shl 4), //Monday Wednesday Friday,
        mday = 0,
        hour = (1 shl 6) or (1 shl 12) or (1 shl 18) or (1 shl 23), minute = 30 //6:30, 12:30, 18:30, 23:30
    )

    cron.getNext(LocalDateTime.of(2025, 2, 28,23,40,0)){//Friday
        //2026-1-2 :Friday
        log("ok=" + (it == Result(2026, 1, 2, 6,30, 24*60*307L+410)))// //ok=true
    }
}

fun test_month_customday_hour_minute() {
    //2025.2.7 first day, period=8 ,first and second day are set 1
    val customPeriod = CustomPeriod(3, 8,2025,2,7)
    val cron = Cron(year = 2025, customPeriod = customPeriod, hour = (1 shl 6) or (1 shl 16), minute = 30)

    cron.getNext(LocalDateTime.of(2026, 1, 2,6,20,0)){
        log("ok=" + (it == null)) //ok=true
    }
}

fun test_year_month_wday_hour_minute() {
    //Jan Feb, Monday Wednesday Friday, 6:30, 12:30, 18:30, 23:30
    val cron = Cron(
        year = 2025,
        month = 1 or (1 shl 1),
        wday = 1 or (1 shl 2) or (1 shl 4),
        mday = 0,
        hour = (1 shl 6) or (1 shl 12) or (1 shl 18) or (1 shl 23),
        minute = 30
    )

    cron.getNext(LocalDateTime.of(2025, 2, 28,23,40,0)){//Friday
        log("ok=" + (it == null)) //ok=true
    }
}