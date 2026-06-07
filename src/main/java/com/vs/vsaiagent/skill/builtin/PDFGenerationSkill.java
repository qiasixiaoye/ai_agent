package com.vs.vsaiagent.skill.builtin;

import cn.hutool.core.io.FileUtil;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.vs.vsaiagent.constant.FileConstant;
import com.vs.vsaiagent.skill.AbstractSkill;
import com.vs.vsaiagent.skill.SkillContext;
import com.vs.vsaiagent.skill.SkillMetadata;
import com.vs.vsaiagent.skill.SkillParam;
import com.vs.vsaiagent.skill.SkillSourceType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Phase 1 第一个 Skill 样板：PDF 生成。
 *
 * 行为等价于老的 {@link com.vs.vsaiagent.tools.PDFGenerationTool#generatePDF}，
 * 但元数据通过 defaultMetadata 声明 / 也可被 skills/pdf-generation/SKILL.md 覆盖。
 *
 * 老的 PDFGenerationTool 暂保留，直到 Phase 1 步骤 3 把 ToolRegistration 切到 Skill 源。
 */
@Component
public class PDFGenerationSkill extends AbstractSkill {

    public static final String SKILL_NAME = "pdf-generation";

    @Override
    protected SkillMetadata defaultMetadata() {
        return SkillMetadata.builder()
                .name(SKILL_NAME)
                .displayName("PDF 生成")
                .description("Generate a PDF file with given content")
                .version("1.0.0")
                .tags(List.of("file", "document"))
                .inputs(List.of(
                        SkillParam.required("fileName", "string", "Name of the file to save the generated PDF"),
                        SkillParam.required("content",  "string", "Content to be included in the PDF")
                ))
                .outputs(List.of(
                        SkillParam.optional("filePath", "string", "Absolute path of the generated PDF", null)
                ))
                .examples(List.of("生成 hello.pdf，内容为 hello world"))
                .timeoutMs(30000L)
                .sourceType(SkillSourceType.LOCAL)
                .build();
    }

    @Override
    protected Object doExecute(Map<String, Object> arguments, SkillContext context) throws Exception {
        String fileName = String.valueOf(arguments.get("fileName"));
        String content  = String.valueOf(arguments.get("content"));

        String fileDir  = FileConstant.FILE_SAVE_DIR + "/pdf";
        String filePath = fileDir + "/" + fileName;
        FileUtil.mkdir(fileDir);

        try (PdfWriter writer = new PdfWriter(filePath);
             PdfDocument pdf  = new PdfDocument(writer);
             Document   doc   = new Document(pdf)) {
            PdfFont font = PdfFontFactory.createFont("STSongStd-Light", "UniGB-UCS2-H");
            doc.setFont(font);
            doc.add(new Paragraph(content));
        }
        return Map.of(
                "filePath", filePath,
                "message",  "PDF generated successfully to: " + filePath
        );
    }
}
