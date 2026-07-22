---
name: pdf-generation
displayName: PDF 生成
description: Generate a PDF file with given content
version: 1.0.0
tags:
  - file
  - document
  - PDF
  - 文档
  - 导出
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
steps:
  - name: 校验入参
    description: 校验 fileName 与 content 非空
  - name: 初始化文档
    description: 用 iText 7 创建 PDF 文档并加载内置中文字体 STSongStd-Light
  - name: 写入内容
    description: 把 content 文本写入页面段落
  - name: 落盘返回路径
    description: 保存到 FILE_SAVE_DIR/pdf/{fileName} 并返回绝对路径 filePath
examples:
  - 生成 hello.pdf，内容为 hello world
timeoutMs: 30000
security:
  riskLevel: high
  permissionScopes: [local_compute, file_write]
  sideEffects: local_file
  dataSensitivity: user_input
  requiresConfirmation: true
  reviewStatus: reviewed
  lifecycleStatus: active
  allowedPaths:
    - FILE_SAVE_DIR/pdf
evaluation:
  profile: deterministic
  successCriteria:
    - 返回生成后的 PDF 文件路径
    - 文件名和内容参数非空时执行成功
  hardConstraints:
    - 只写入允许的 PDF 输出目录
    - 不执行系统命令
  goldenCaseTags: [normal, boundary, exception]
  attributionStages: [call, execution, integration]
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
