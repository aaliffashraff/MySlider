package com.example.myslider

fun ClosedFloatingPointRange<Float>.toRatio(value: Float): Float{
    var ratio = (value-start)/(endInclusive-start)
    return ratio.coerceIn(0f,1f)
}

fun ClosedFloatingPointRange<Float>.fromRatio(ratio: Float): Float{
    var value = ratio.coerceIn(0f,1f)
    return value * (endInclusive-start)+start
}
