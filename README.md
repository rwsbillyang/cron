A variant of traditional cron implemented by Kotlin.

It aims to find the nearest datetime and distance since a specified datetime(such as now()).


## Add dependency

Step 1. Add the JitPack repository to your build file
Add it in your root build.gradle at the end of repositories:
```gradle
	allprojects {
		repositories {
			...
			maven { url 'https://jitpack.io' }
		}
	}
```

Step 2. Add the dependency
```gradle
	dependencies {
	        implementation 'com.github.rwsbillyang:cron:1.0.0'
	}
```

## Get Started


```kotlin
    //first step: create cron
    val cron = Cron(
        month= 1 or (1 shl 1),//only Jan Feb
        wday = 1 or (1 shl 2) or (1 shl 4), //Monday Wednesday Friday,
        mday = 0,
        hour = (1 shl 6) or (1 shl 12) or (1 shl 18) or (1 shl 23), minute = 30 //6:30, 12:30, 18:30, 23:30
    )

    //second step: get the nearest coming datetime since 2025-2-28 23:40
    //accroding to the cron above, the result is 2026-1-2 6:30
    cron.getNext(LocalDateTime.of(2025, 2, 28,23,40,0)){    //2025-2-28: Friday
        //2026-1-2 :Friday
        log("ok=" + (it == Result(2026, 1, 2, 6,30, 24*60*307L+410))) //ok=true
    }
```

## Detail

```kotlin
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
)


    /**
     * @param sinceDateTime any local datetime, often it is now
     * @param onFinished the callback notifys the result of the nearest datetime and distance. null if not found.
     * */
    fun getNext(sinceDateTime: LocalDateTime, onFinished: (r: Result?)->Unit )



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
)
```


### More examples

```kotlin
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
```