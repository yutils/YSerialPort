package com.yujing.chuankou.core.util

/**
 * 校验算法体系
 *
 * - 通用 CRC 引擎（支持任意 width/poly/init/refin/refout/xorout）
 * - 常用简单校验：SUM / XOR / BCC / LRC / Fletcher / Adler / 二进制补码
 * - 结果可配置：高字节在前、输入反转、输出反转、结果异或、结果取反
 */
object Checksum {

    /** 算法类型 */
    enum class Kind { CRC, SUM, XOR, BCC, LRC, FLETCHER, ADLER, TWOS_COMPLEMENT }

    /** CRC 参数（多项式为 MSB-first 常规形式） */
    data class CrcParams(
        val width: Int,
        val poly: Long,
        val init: Long,
        val refin: Boolean,
        val refout: Boolean,
        val xorout: Long,
        /** 标准校验值：对 ASCII "123456789" 的期望结果，-1 表示未标注 */
        val check: Long = -1L
    )

    /** 算法定义 */
    data class Algo(
        val id: String,
        val name: String,
        val group: String,
        val kind: Kind,
        val width: Int,
        val crc: CrcParams? = null,
        val desc: String = ""
    )

    /** 可调选项 */
    data class Options(
        val highByteFirst: Boolean = true,
        /** null 表示沿用算法默认 */
        val refin: Boolean? = null,
        val refout: Boolean? = null,
        /** 结果额外异或 */
        val extraXor: Long = 0,
        /** 结果按位取反 */
        val invert: Boolean = false,
        /** 输出位宽（0 = 用算法默认） */
        val outWidth: Int = 0,
        /** 覆盖多项式（null = 用算法默认） */
        val polyOverride: Long? = null,
        /** 覆盖初值（null = 用算法默认） */
        val initOverride: Long? = null
    )

    /** 计算结果 */
    data class Result(
        val algo: Algo,
        val value: Long,
        val width: Int,
        val bytes: ByteArray,
        val hex: String,
        val dec: String,
        val bin: String
    ) {
        override fun equals(other: Any?): Boolean = other is Result && other.hex == hex && other.algo.id == algo.id
        override fun hashCode(): Int = hex.hashCode() * 31 + algo.id.hashCode()
    }

    // ==================================================================
    // 算法清单
    // ==================================================================

    val ALGOS: List<Algo> = buildList {
        // ---------- 简单校验 ----------
        add(Algo("sum8", "累加和 SUM8", "常用", Kind.SUM, 8, desc = "所有字节求和，取低 8 位"))
        add(Algo("sum16", "累加和 SUM16", "常用", Kind.SUM, 16, desc = "所有字节求和，取低 16 位"))
        add(Algo("xor8", "异或校验 XOR8", "常用", Kind.XOR, 8, desc = "所有字节逐字节异或，取低 8 位"))
        add(Algo("xor16", "异或校验 XOR16", "常用", Kind.XOR, 16, desc = "所有字节逐字节异或，取低 16 位"))
        add(Algo("bcc", "BCC 块校验", "常用", Kind.BCC, 8, desc = "同 XOR8，用于条码/磁卡"))
        add(Algo("lrc", "LRC 纵向冗余校验", "常用", Kind.LRC, 8, desc = "累加和取反加一（MODBUS ASCII）"))
        add(Algo("lrc16", "LRC16 纵向冗余校验", "常用", Kind.LRC, 16, desc = "16 位累加和取反加一"))
        add(Algo("twos8", "二进制补码 SUM8", "常用", Kind.TWOS_COMPLEMENT, 8, desc = "累加和取反加一"))
        add(Algo("fletcher16", "Fletcher-16", "常用", Kind.FLETCHER, 16))
        add(Algo("fletcher32", "Fletcher-32", "常用", Kind.FLETCHER, 32))
        add(Algo("adler32", "Adler-32", "常用", Kind.ADLER, 32))

        // ---------- CRC-8 ----------
        add(crc("crc8", "CRC-8", 8, 0x07, 0x00, false, false, 0x00, 0xF4))
        add(crc("crc8_cdma2000", "CRC-8/CDMA2000", 8, 0x9B, 0xFF, false, false, 0x00, 0xDA))
        add(crc("crc8_darc", "CRC-8/DARC", 8, 0x39, 0x00, true, true, 0x00, 0x15))
        add(crc("crc8_dvbs2", "CRC-8/DVB-S2", 8, 0xD5, 0x00, false, false, 0x00, 0xBC))
        add(crc("crc8_itu", "CRC-8/ITU", 8, 0x07, 0x00, false, false, 0x55, 0xA1))
        add(crc("crc8_maxim", "CRC-8/MAXIM (Dallas)", 8, 0x31, 0x00, true, true, 0x00, 0xA1))
        add(crc("crc8_rohc", "CRC-8/ROHC", 8, 0x07, 0xFF, true, true, 0x00, 0xD0))
        add(crc("crc8_wcdma", "CRC-8/WCDMA", 8, 0x9B, 0x00, true, true, 0x00, 0x25))
        add(crc("crc8_sae_j1850", "CRC-8/SAE-J1850", 8, 0x1D, 0xFF, false, false, 0xFF, 0x4B))
        add(crc("crc8_autosar", "CRC-8/AUTOSAR", 8, 0x2F, 0xFF, false, false, 0xFF, 0xDF))

        // ---------- CRC-16 ----------
        add(crc("crc16_arc", "CRC-16/ARC (IBM)", 16, 0x8005, 0x0000, true, true, 0x0000, 0xBB3D))
        add(crc("crc16_modbus", "CRC-16/MODBUS", 16, 0x8005, 0xFFFF, true, true, 0x0000, 0x4B37))
        add(crc("crc16_ccitt_false", "CRC-16/CCITT-FALSE", 16, 0x1021, 0xFFFF, false, false, 0x0000, 0x29B1))
        add(crc("crc16_xmodem", "CRC-16/XMODEM", 16, 0x1021, 0x0000, false, false, 0x0000, 0x31C3))
        add(crc("crc16_x25", "CRC-16/X-25", 16, 0x1021, 0xFFFF, true, true, 0xFFFF, 0x906E))
        add(crc("crc16_usb", "CRC-16/USB", 16, 0x8005, 0xFFFF, true, true, 0xFFFF, 0xB4C8))
        add(crc("crc16_maxim", "CRC-16/MAXIM (DOW)", 16, 0x8005, 0x0000, true, true, 0xFFFF, 0x44C2))
        add(crc("crc16_dnp", "CRC-16/DNP", 16, 0x3D65, 0x0000, true, true, 0xFFFF, 0xEA82))
        add(crc("crc16_kermit", "CRC-16/KERMIT", 16, 0x1021, 0x0000, true, true, 0x0000, 0x2189))
        add(crc("crc16_ibm_sdlc", "CRC-16/IBM-SDLC", 16, 0x1021, 0xFFFF, true, true, 0xFFFF, 0x906E))
        add(crc("crc16_en13757", "CRC-16/EN-13757", 16, 0x3D65, 0x0000, false, false, 0xFFFF, 0xC2B7))
        add(crc("crc16_t10dif", "CRC-16/T10-DIF", 16, 0x8BB7, 0x0000, false, false, 0x0000, 0xD0DB))
        add(crc("crc16_teledisk", "CRC-16/TELEDISK", 16, 0xA097, 0x0000, false, false, 0x0000, 0x0FB3))
        add(crc("crc16_dectr", "CRC-16/DECT-R", 16, 0x0589, 0x0000, false, false, 0x0001, 0x007E))

        // ---------- CRC-32 ----------
        add(crc("crc32", "CRC-32 (IEEE)", 32, 0x04C11DB7L, 0xFFFFFFFFL, true, true, 0xFFFFFFFFL, 0xCBF43926L))
        add(crc("crc32_mpeg2", "CRC-32/MPEG-2", 32, 0x04C11DB7L, 0xFFFFFFFFL, false, false, 0x00000000L, 0x0376E6E7L))
        add(crc("crc32_bzip2", "CRC-32/BZIP2", 32, 0x04C11DB7L, 0xFFFFFFFFL, false, false, 0xFFFFFFFFL, 0xFC891918L))
        add(crc("crc32_posix", "CRC-32/POSIX (CKSUM)", 32, 0x04C11DB7L, 0x00000000L, false, false, 0xFFFFFFFFL, 0x765E7680L))
        add(crc("crc32_jamcrc", "CRC-32/JAMCRC", 32, 0x04C11DB7L, 0xFFFFFFFFL, true, true, 0x00000000L, 0x340BC6D9L))
        add(crc("crc32_castagnoli", "CRC-32C (Castagnoli)", 32, 0x1EDC6F41L, 0xFFFFFFFFL, true, true, 0xFFFFFFFFL, 0xE3069283L))
        add(crc("crc32_autosar", "CRC-32/AUTOSAR", 32, 0xF4ACFB13L, 0xFFFFFFFFL, true, true, 0xFFFFFFFFL, 0x1697D06AL))
    }

    private fun crc(
        id: String, name: String, width: Int, poly: Long, init: Long,
        refin: Boolean, refout: Boolean, xorout: Long, check: Long
    ) = Algo(id, name, "CRC-$width", Kind.CRC, width, CrcParams(width, poly, init, refin, refout, xorout, check))

    val GROUPS: List<String> get() = ALGOS.map { it.group }.distinct()

    fun findAlgo(id: String): Algo = ALGOS.firstOrNull { it.id == id } ?: ALGOS.first()

    // ==================================================================
    // 计算
    // ==================================================================

    fun calculate(data: ByteArray, algo: Algo, options: Options = Options()): Result {
        val width = if (options.outWidth > 0) options.outWidth else algo.width
        val mask = maskOf(width)
        var value: Long = when (algo.kind) {
            Kind.CRC -> crcValue(data, algo.crc!!, options)
            Kind.SUM -> {
                var s = 0L
                for (b in data) s += (b.toInt() and 0xFF)
                s
            }
            Kind.XOR, Kind.BCC -> {
                var s = 0L
                for (b in data) s = s xor (b.toInt() and 0xFF).toLong()
                s
            }
            Kind.LRC -> {
                var s = 0L
                for (b in data) s += (b.toInt() and 0xFF)
                if (algo.width <= 8) ((-s) and 0xFF) else ((-s) and 0xFFFF)
            }
            Kind.TWOS_COMPLEMENT -> {
                var s = 0L
                for (b in data) s += (b.toInt() and 0xFF)
                (-s)
            }
            Kind.FLETCHER -> if (algo.width <= 16) fletcher16(data) else fletcher32(data)
            Kind.ADLER -> adler32(data)
        }
        value = value and mask
        if (options.invert) value = value.inv() and mask
        if (options.extraXor != 0L) value = (value xor options.extraXor) and mask
        val byteCount = if (width <= 8) 1 else if (width <= 16) 2 else 4
        val bytes = Hex.longToBytes(value and mask, byteCount, options.highByteFirst)
        return Result(
            algo = algo,
            value = value and mask,
            width = width,
            bytes = bytes,
            hex = Hex.toHexString(bytes, " "),
            dec = (value and mask).toString(),
            bin = Hex.toBinaryString(bytes)
        )
    }

    /** 通用 CRC（位运算，支持 refin / refout 的任意组合） */
    fun crcValue(data: ByteArray, p: CrcParams, options: Options = Options()): Long {
        val width = p.width
        val mask = maskOf(width)
        val poly = (options.polyOverride ?: p.poly) and mask
        val refin = options.refin ?: p.refin
        val refout = options.refout ?: p.refout
        val topBit = 1L shl (width - 1)
        var crc = (options.initOverride ?: p.init) and mask
        for (b in data) {
            val byte = if (refin) reflect(b.toInt() and 0xFF, 8) else (b.toInt() and 0xFF)
            crc = crc xor ((byte.toLong() and 0xFFL) shl (width - 8))
            crc = crc and mask
            for (i in 0 until 8) {
                crc = if (crc and topBit != 0L) {
                    ((crc shl 1) xor poly) and mask
                } else {
                    (crc shl 1) and mask
                }
            }
        }
        if (refout) crc = reflect(crc, width)
        crc = (crc xor p.xorout) and mask
        return crc and mask
    }

    // ==================================================================
    // 辅助
    // ==================================================================

    fun maskOf(width: Int): Long = when {
        width <= 0 -> 0L
        width >= 64 -> -1L
        else -> (1L shl width) - 1
    }

    fun reflect(value: Long, bits: Int): Long {
        var v = value
        var r = 0L
        for (i in 0 until bits) {
            r = (r shl 1) or (v and 1L)
            v = v shr 1
        }
        return r
    }

    fun reflect(value: Int, bits: Int): Int {
        var v = value
        var r = 0
        for (i in 0 until bits) {
            r = (r shl 1) or (v and 1)
            v = v shr 1
        }
        return r
    }

    private fun fletcher16(data: ByteArray): Long {
        var sum1 = 0
        var sum2 = 0
        for (b in data) {
            sum1 = (sum1 + (b.toInt() and 0xFF)) % 255
            sum2 = (sum2 + sum1) % 255
        }
        return ((sum2 shl 8) or sum1).toLong()
    }

    private fun fletcher32(data: ByteArray): Long {
        var sum1 = 0L
        var sum2 = 0L
        var i = 0
        while (i < data.size) {
            val block = minOf(360, (data.size - i + 1) / 2)
            repeat(block) {
                val b0 = data[i].toInt() and 0xFF
                val b1 = if (i + 1 < data.size) data[i + 1].toInt() and 0xFF else 0
                val word = (b0 shl 8) or b1
                sum1 = (sum1 + word) % 65535
                sum2 = (sum2 + sum1) % 65535
                i += 2
            }
        }
        return ((sum2 shl 16) or sum1) and 0xFFFFFFFFL
    }

    private fun adler32(data: ByteArray): Long {
        var a = 1L
        var b = 0L
        for (byte in data) {
            a = (a + (byte.toInt() and 0xFF)) % 65521
            b = (b + a) % 65521
        }
        return ((b shl 16) or a) and 0xFFFFFFFFL
    }
}
