#!/bin/bash
# 一键恢复全部被禁用的包。电视地址用 TV=ip:5555 指定。
TV=${TV:-192.168.1.10:5555}
adb connect $TV >/dev/null 2>&1
while read -r p; do
  [ -z "$p" ] && continue
  adb -s $TV shell "pm unblock $p" >/dev/null 2>&1
  echo "已恢复: $p"
done < "$(dirname "$0")/blocked_packages.txt"
echo "全部恢复完成,建议重启电视。"
