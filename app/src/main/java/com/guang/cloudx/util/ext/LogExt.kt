package com.guang.cloudx.util.ext

import android.util.Log

fun Any.d(logTag: String = "CloudX.Log") {
    Log.d(logTag, this.toString())
}

fun Any.e(logTag: String = "CloudX.Log") {
    Log.e(logTag, this.toString())
}

fun Any.i(logTag: String = "CloudX.Log") {
    Log.i(logTag, this.toString())
}

fun Any.w(logTag: String = "CloudX.Log") {
    Log.w(logTag, this.toString())
}

fun Any.v(logTag: String = "CloudX.Log") {
    Log.v(logTag, this.toString())
}

fun Any.wtf(logTag: String = "CloudX.Log") {
    Log.wtf(logTag, this.toString())
}