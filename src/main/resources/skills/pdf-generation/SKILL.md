---
name: pdf-generation
displayName: PDF 生成
description: Generate a PDF file with given content
version: 1.0.0
tags:
  - file
  - document
inputs:
  - name: fileName
    type: string
    description: Name of the file to save the generated PDF
    required: true
  - name: content
    type: string
    description: Content to be included in the PDF
    required: true
outputs:
  - name: filePath
    type: string
    description: 生成后的 PDF 绝对路径
examples:
  - 生成 hello.pdf，内容为 hello world
timeoutMs: 30000
sourceType: LOCAL
---

# PDF 生成 Skill

把给定文本写入一个 PDF 文件，保存到 `FileConstant.FILE_SAVE_DIR/pdf/{fileName}`。

## 依赖

- iText 7（`itextpdf.kernel` / `itextpdf.layout`）
- 内置中文字体 `STSongStd-Light` + `UniGB-UCS2-H`

## 限制

- 暂不支持自定义字体路径，需要中文渲染时请保持默认字体
- 暂不支持图片、表格、多列布局（后续版本扩展）

## 维护人

- 默认：repo owner
