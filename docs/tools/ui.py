#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""UI 层级分析工具：把 uiautomator dump 的结果解析成「可点击节点 + 中心坐标」

用法：
    python docs/tools/ui.py <dump.xml> [关键字]
"""
import io
import re
import sys


def parse(path):
    s = io.open(path, encoding='utf-8', errors='replace').read()
    out = []
    for n in re.findall(r'<node[^>]*?/?>', s):
        def g(k):
            m = re.search(k + r'="([^"]*)"', n)
            return m.group(1) if m else ''
        bounds = g('bounds')
        m = re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', bounds)
        if not m:
            continue
        x1, y1, x2, y2 = map(int, m.groups())
        out.append({
            'text': g('text'),
            'desc': g('content-desc'),
            'cls': g('class').split('.')[-1],
            'rid': g('resource-id').split('/')[-1],
            'clickable': g('clickable') == 'true',
            'bounds': (x1, y1, x2, y2),
            'cx': (x1 + x2) // 2,
            'cy': (y1 + y2) // 2,
            'w': x2 - x1,
            'h': y2 - y1,
        })
    return out


def main():
    path = sys.argv[1]
    kw = sys.argv[2] if len(sys.argv) > 2 else ''
    nodes = parse(path)
    for nd in nodes:
        label = nd['text'] or nd['desc'] or nd['rid'] or nd['cls']
        if kw and kw.lower() not in (label + nd['cls'] + nd['rid']).lower():
            continue
        if not (nd['clickable'] or kw):
            continue
        print('%-40s %-22s %-18s tap(%d,%d)  %dx%d' % (
            label[:38], nd['cls'][:20], nd['rid'][:16], nd['cx'], nd['cy'], nd['w'], nd['h']))


if __name__ == '__main__':
    main()
