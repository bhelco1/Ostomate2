package com.ostomate.app.platform

/**
 * A QR code as a square grid of modules, including a quiet zone of [quietZone] light modules
 * on every side. Printers draw it as vector rectangles, so its printed size is exact.
 */
class QrModules(
    val size: Int,
    private val dark: BooleanArray,
    val quietZone: Int = 1,
) {
    init {
        require(size > 2 * quietZone) { "size $size leaves no symbol inside a quiet zone of $quietZone" }
        require(dark.size == size * size) { "expected ${size * size} modules, got ${dark.size}" }
    }

    /** Modules across the symbol itself, quiet zone excluded. */
    val symbolSize: Int get() = size - 2 * quietZone

    fun isDark(
        x: Int,
        y: Int,
    ): Boolean = dark[y * size + x]

    /** Horizontal runs of dark modules, row by row: one rectangle per run instead of per module. */
    fun darkRuns(): List<ModuleRun> = (0 until size).flatMap { y -> darkRunsInRow(y) }

    private fun darkRunsInRow(y: Int): List<ModuleRun> {
        val runs = mutableListOf<ModuleRun>()
        var x = 0
        while (x < size) {
            if (isDark(x, y)) {
                val start = x
                while (x < size && isDark(x, y)) x++
                runs += ModuleRun(row = y, start = start, length = x - start)
            } else {
                x++
            }
        }
        return runs
    }

    companion object {
        /**
         * Trims a platform encoder's raw grid to the symbol (a QR symbol always has dark finder
         * patterns in three corners, so its bounds are exact) and adds exactly [quietZone]
         * modules around it, whatever margin the encoder used. Null if there is no square symbol.
         */
        fun fromGrid(
            width: Int,
            height: Int,
            quietZone: Int = 1,
            isDark: (x: Int, y: Int) -> Boolean,
        ): QrModules? {
            var minX = width
            var minY = height
            var maxX = -1
            var maxY = -1
            for (y in 0 until height) {
                for (x in 0 until width) {
                    if (isDark(x, y)) {
                        minX = minOf(minX, x)
                        minY = minOf(minY, y)
                        maxX = maxOf(maxX, x)
                        maxY = maxOf(maxY, y)
                    }
                }
            }
            val symbol = maxX - minX + 1
            if (maxX < 0 || symbol != maxY - minY + 1) return null
            val size = symbol + 2 * quietZone
            val dark = BooleanArray(size * size)
            for (y in 0 until symbol) {
                for (x in 0 until symbol) {
                    dark[(y + quietZone) * size + x + quietZone] = isDark(minX + x, minY + y)
                }
            }
            return QrModules(size, dark, quietZone)
        }
    }
}

data class ModuleRun(
    val row: Int,
    val start: Int,
    val length: Int,
)
