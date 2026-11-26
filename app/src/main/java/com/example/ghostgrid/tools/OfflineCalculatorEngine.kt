package com.example.ghostgrid.tools

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Pure offline recursive-descent arithmetic expression evaluator for the GhostGrid
 * Survival Calculator. Supports +, -, ×, ÷, %, parentheses, decimals, and unary minus.
 */
object OfflineCalculatorEngine {

    fun evaluate(rawExpression: String): String {
        val normalized = rawExpression
            .replace("×", "*")
            .replace("÷", "/")
            .replace("−", "-")
            .replace(" ", "")
        if (normalized.isEmpty()) return "0"

        return runCatching {
            val parser = Parser(normalized)
            val result = parser.parseExpression()
            if (!parser.isAtEnd()) throw IllegalArgumentException("Unexpected token")
            formatResult(result)
        }.getOrElse { "Error" }
    }

    private fun formatResult(value: Double): String {
        if (value.isNaN() || value.isInfinite()) return "Error"
        val bd = BigDecimal.valueOf(value)
            .setScale(8, RoundingMode.HALF_UP)
            .stripTrailingZeros()
        return bd.toPlainString()
    }

    private class Parser(private val expr: String) {
        private var pos = 0

        fun isAtEnd(): Boolean = pos >= expr.length

        private fun peek(): Char? = if (pos < expr.length) expr[pos] else null

        fun parseExpression(): Double {
            var value = parseTerm()
            while (true) {
                when (peek()) {
                    '+' -> {
                        pos++
                        value += parseTerm()
                    }
                    '-' -> {
                        pos++
                        value -= parseTerm()
                    }
                    else -> return value
                }
            }
        }

        private fun parseTerm(): Double {
            var value = parseFactor()
            while (true) {
                when (peek()) {
                    '*' -> {
                        pos++
                        value *= parseFactor()
                    }
                    '/' -> {
                        pos++
                        val denom = parseFactor()
                        if (denom == 0.0) throw ArithmeticException("Division by zero")
                        value /= denom
                    }
                    '%' -> {
                        pos++
                        value /= 100.0
                    }
                    else -> return value
                }
            }
        }

        private fun parseFactor(): Double {
            return when (peek()) {
                '+' -> {
                    pos++
                    parseFactor()
                }
                '-' -> {
                    pos++
                    -parseFactor()
                }
                '(' -> {
                    pos++
                    val inner = parseExpression()
                    if (peek() == ')') pos++
                    inner
                }
                else -> parseNumber()
            }
        }

        private fun parseNumber(): Double {
            val start = pos
            while (peek()?.let { it.isDigit() || it == '.' } == true) {
                pos++
            }
            if (start == pos) throw IllegalArgumentException("Expected number")
            return expr.substring(start, pos).toDouble()
        }
    }
}
