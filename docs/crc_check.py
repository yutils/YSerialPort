# -*- coding: utf-8 -*-
"""独立实现 CRC 位运算引擎，用于交叉校验 Kotlin 预设表的常量是否正确"""
DATA = b"123456789"


def mask_of(width):
    return (1 << width) - 1


def reflect(value, bits):
    r = 0
    for _ in range(bits):
        r = (r << 1) | (value & 1)
        value >>= 1
    return r


def crc(data, width, poly, init, refin, refout, xorout):
    mask = mask_of(width)
    top = 1 << (width - 1)
    crc_v = init & mask
    for b in data:
        byte = reflect(b, 8) if refin else b
        crc_v ^= (byte & 0xFF) << (width - 8)
        crc_v &= mask
        for _ in range(8):
            if crc_v & top:
                crc_v = ((crc_v << 1) ^ poly) & mask
            else:
                crc_v = (crc_v << 1) & mask
    if refout:
        crc_v = reflect(crc_v, width)
    crc_v = (crc_v ^ xorout) & mask
    return crc_v


# 与 Kotlin Checksum.ALGOS 中 CRC 部分保持一致（id, name, width, poly, init, refin, refout, xorout, check）
PRESETS = [
    ("crc8", "CRC-8", 8, 0x07, 0x00, False, False, 0x00, 0xF4),
    ("crc8_cdma2000", "CRC-8/CDMA2000", 8, 0x9B, 0xFF, False, False, 0x00, 0xDA),
    ("crc8_darc", "CRC-8/DARC", 8, 0x39, 0x00, True, True, 0x00, 0x15),
    ("crc8_dvbs2", "CRC-8/DVB-S2", 8, 0xD5, 0x00, False, False, 0x00, 0xBC),
    ("crc8_itu", "CRC-8/ITU", 8, 0x07, 0x00, False, False, 0x55, 0xA1),
    ("crc8_maxim", "CRC-8/MAXIM (Dallas)", 8, 0x31, 0x00, True, True, 0x00, 0xA1),
    ("crc8_rohc", "CRC-8/ROHC", 8, 0x07, 0xFF, True, True, 0x00, 0xD0),
    ("crc8_wcdma", "CRC-8/WCDMA", 8, 0x9B, 0x00, True, True, 0x00, 0x25),
    ("crc8_sae_j1850", "CRC-8/SAE-J1850", 8, 0x1D, 0xFF, False, False, 0xFF, 0x4B),
    ("crc8_autosar", "CRC-8/AUTOSAR", 8, 0x2F, 0xFF, False, False, 0xFF, 0xDF),
    ("crc16_arc", "CRC-16/ARC (IBM)", 16, 0x8005, 0x0000, True, True, 0x0000, 0xBB3D),
    ("crc16_modbus", "CRC-16/MODBUS", 16, 0x8005, 0xFFFF, True, True, 0x0000, 0x4B37),
    ("crc16_ccitt_false", "CRC-16/CCITT-FALSE", 16, 0x1021, 0xFFFF, False, False, 0x0000, 0x29B1),
    ("crc16_xmodem", "CRC-16/XMODEM", 16, 0x1021, 0x0000, False, False, 0x0000, 0x31C3),
    ("crc16_x25", "CRC-16/X-25", 16, 0x1021, 0xFFFF, True, True, 0xFFFF, 0x906E),
    ("crc16_usb", "CRC-16/USB", 16, 0x8005, 0xFFFF, True, True, 0xFFFF, 0xB4C8),
    ("crc16_maxim", "CRC-16/MAXIM (DOW)", 16, 0x8005, 0x0000, True, True, 0xFFFF, 0x44C2),
    ("crc16_dnp", "CRC-16/DNP", 16, 0x3D65, 0x0000, True, True, 0xFFFF, 0xEA82),
    ("crc16_kermit", "CRC-16/KERMIT", 16, 0x1021, 0x0000, True, True, 0x0000, 0x2189),
    ("crc16_ibm_sdlc", "CRC-16/IBM-SDLC", 16, 0x1021, 0xFFFF, True, True, 0xFFFF, 0x906E),
    ("crc16_en13757", "CRC-16/EN-13757", 16, 0x3D65, 0x0000, False, False, 0xFFFF, 0xC2B7),
    ("crc16_t10dif", "CRC-16/T10-DIF", 16, 0x8BB7, 0x0000, False, False, 0x0000, 0xD0DB),
    ("crc16_teledisk", "CRC-16/TELEDISK", 16, 0xA097, 0x0000, False, False, 0x0000, 0x0FB3),
    ("crc16_dectr", "CRC-16/DECT-R", 16, 0x0589, 0x0000, False, False, 0x0001, 0x007E),
    ("crc32", "CRC-32 (IEEE)", 32, 0x04C11DB7, 0xFFFFFFFF, True, True, 0xFFFFFFFF, 0xCBF43926),
    ("crc32_mpeg2", "CRC-32/MPEG-2", 32, 0x04C11DB7, 0xFFFFFFFF, False, False, 0x00000000, 0x0376E6E7),
    ("crc32_bzip2", "CRC-32/BZIP2", 32, 0x04C11DB7, 0xFFFFFFFF, False, False, 0xFFFFFFFF, 0xFC891918),
    ("crc32_posix", "CRC-32/POSIX (CKSUM)", 32, 0x04C11DB7, 0x00000000, False, False, 0xFFFFFFFF, 0x765E7680),
    ("crc32_jamcrc", "CRC-32/JAMCRC", 32, 0x04C11DB7, 0xFFFFFFFF, True, True, 0x00000000, 0x340BC6D9),
    ("crc32_castagnoli", "CRC-32C (Castagnoli)", 32, 0x1EDC6F41, 0xFFFFFFFF, True, True, 0xFFFFFFFF, 0xE3069283),
    ("crc32_autosar", "CRC-32/AUTOSAR", 32, 0xF4ACFB13, 0xFFFFFFFF, True, True, 0xFFFFFFFF, 0x1697D06A),
]

bad = 0
print("%-22s %-10s %-10s %s" % ("id", "computed", "expected", "status"))
print("-" * 62)
for (aid, name, w, poly, init, rin, rout, xo, chk) in PRESETS:
    got = crc(DATA, w, poly, init, rin, rout, xo)
    ok = (got == chk)
    if not ok:
        bad += 1
    print("%-22s 0x%08X 0x%08X %s" % (aid, got, chk, "OK" if ok else "*** MISMATCH ***"))

print("-" * 62)
print("mismatch count = %d / %d" % (bad, len(PRESETS)))
