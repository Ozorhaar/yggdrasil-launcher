package com.ozorhaar.yggdrasil.launcher.search

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * A small calculator for the search bar: + - * / ^ %, brackets, and "x" or "×" for times.
 * Hand-written parser (no script engine), so typing in search can never run code.
 */
object Calc {
    /** The answer, or null if [text] isn't a sum (or is just a number). */
    fun evaluate(text: String): String? {
        val src = text.trim().replace('×', '*').replace('÷', '/').replace(Regex("(?<=\\d)\\s*[xX]\\s*(?=[\\d(])"), "*")
        if (src.isEmpty() || !src.any { it in "+-*/^%" } || !src.all { it.isDigit() || it in "+-*/^%.() ," }) return null
        return runCatching {
            val p = Parser(src.replace(",", "").replace(" ", ""))
            val v = p.expr()
            if (p.pos != p.s.length) return null
            v.round(MathContext(12, RoundingMode.HALF_EVEN)).stripTrailingZeros().let {
                if (it.scale() < 0) it.setScale(0) else it
            }.toPlainString()
        }.getOrNull()
    }

    private class Parser(val s: String) {
        var pos = 0
        private val mc = MathContext(20, RoundingMode.HALF_EVEN)

        fun expr(): BigDecimal {
            var v = term()
            while (pos < s.length && s[pos] in "+-") {
                val op = s[pos++]
                val r = term()
                v = if (op == '+') v.add(r, mc) else v.subtract(r, mc)
            }
            return v
        }

        private fun term(): BigDecimal {
            var v = power()
            while (pos < s.length && s[pos] in "*/%") {
                val op = s[pos++]
                if (op == '%' && (pos >= s.length || s[pos] !in "0123456789.(")) { v = v.divide(BigDecimal(100), mc); continue }
                val r = power()
                v = when (op) {
                    '*' -> v.multiply(r, mc)
                    '/' -> v.divide(r, mc)
                    else -> v.remainder(r, mc)
                }
            }
            return v
        }

        private fun power(): BigDecimal {
            val b = unary()
            if (pos < s.length && s[pos] == '^') {
                pos++
                val e = power()
                val n = e.intValueExact()
                require(n in -999..999)
                return if (n >= 0) b.pow(n, mc) else BigDecimal.ONE.divide(b.pow(-n, mc), mc)
            }
            return b
        }

        private fun unary(): BigDecimal {
            if (pos < s.length && s[pos] == '-') { pos++; return unary().negate() }
            if (pos < s.length && s[pos] == '+') { pos++; return unary() }
            return atom()
        }

        private fun atom(): BigDecimal {
            if (pos < s.length && s[pos] == '(') {
                pos++
                val v = expr()
                require(pos < s.length && s[pos] == ')')
                pos++
                return v
            }
            val start = pos
            while (pos < s.length && (s[pos].isDigit() || s[pos] == '.')) pos++
            require(pos > start)
            return BigDecimal(s.substring(start, pos))
        }
    }
}
