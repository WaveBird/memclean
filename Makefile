# 常用命令封装
#
#   make            查看帮助
#   make build      构建 APK
#   make deploy     部署到盒子（DEV=ip:port）
#   make verify     三档密度截图验证
#   make release    构建 + 校验和，产物放 dist/
#   make tag        校验版本并打标签，推送后由 GitHub Actions 自动发布
#   make icon       重新生成图标
#   make clean      清理构建产物

SHELL   := /bin/bash
DEV     ?= 192.168.1.172:5555
VERSION := $(shell grep -oE 'android:versionName="[^"]+"' AndroidManifest.xml | head -1 | cut -d'"' -f2)
TAG     := v$(VERSION)

# macOS 只有 shasum，Linux 只有 sha256sum
SHA256  := $(shell command -v sha256sum >/dev/null 2>&1 && echo "sha256sum" || echo "shasum -a 256")

.PHONY: help build deploy verify release checksum tag icon clean

help:
	@echo "内存守护 v$(VERSION)"
	@echo
	@echo "  make build      构建 APK → build/memclean-$(VERSION).apk"
	@echo "  make deploy     部署到盒子（DEV=ip:port，默认 $(DEV)）"
	@echo "  make verify     三档密度截图验证，存到 build/shots/"
	@echo "  make release    构建 + 校验和，产物放 dist/"
	@echo "  make tag        校验版本并打标签 $(TAG)"
	@echo "  make icon       重新生成图标（纯 Python）"
	@echo "  make clean      清理构建产物"

build:
	./build.sh

checksum: build
	@cd build && $(SHA256) *.apk > SHA256SUMS.txt && cat SHA256SUMS.txt

release: checksum
	@mkdir -p dist
	@cp build/memclean-*.apk build/SHA256SUMS.txt dist/
	@echo
	@echo "发布产物（dist/）："
	@ls -lh dist/

tag:
	@test -z "$$(git status --porcelain)" || { echo "工作区有未提交的改动，先提交再打标签"; exit 1; }
	@if git rev-parse -q --verify "refs/tags/$(TAG)" >/dev/null; then \
		echo "标签 $(TAG) 已存在"; exit 1; fi
	git tag -a "$(TAG)" -m "release $(TAG)"
	@echo
	@echo "已打标签 $(TAG)。推送后 GitHub Actions 会自动构建并发布："
	@echo "  git push origin $(TAG)"

deploy:
	./tools/deploy.sh $(DEV)

verify:
	./tools/verify_layouts.sh $(DEV)

icon:
	python3 tools/make_icon.py

clean:
	rm -rf build dist
	@echo "已清理 build/ 与 dist/"
