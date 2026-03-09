package nl.fayece.paymentprocessing.util

import java.math.BigDecimal
import java.math.RoundingMode

fun BigDecimal.toMoney(): BigDecimal = setScale(2, RoundingMode.HALF_EVEN)
