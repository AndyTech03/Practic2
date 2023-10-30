package org.example;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.docx4j.openpackaging.exceptions.Docx4JException;
import org.docx4j.openpackaging.exceptions.InvalidFormatException;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.openpackaging.parts.WordprocessingML.MainDocumentPart;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class FileTest {

    public static void main(String[] args) {
        String[] files = new String[] {"testTable.xlsx", "testDocument1.docx", "testDocument2.docx"};
        excelTest(files[0]);
        wordTest(files[1], files[2]);
        zipTest(files);
    }

    private static void excelTest(String fileName){
        try (
                XSSFWorkbook newWorkbook = new XSSFWorkbook();
                OutputStream outputStream = Files.newOutputStream(Path.of(fileName))
        ) {
            XSSFSheet sheet = newWorkbook.createSheet();
            XSSFRow row = sheet.createRow(0);
            Cell cell = row.createCell(0, CellType.STRING);
            cell.setCellValue("тест");
            newWorkbook.write(outputStream);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void wordTest(String fileName1, String fileName2){
        try (
                OutputStream outputStream = Files.newOutputStream(Path.of(fileName1));
                OutputStream outputStream2 = Files.newOutputStream(Path.of(fileName2));
                XWPFDocument document = new XWPFDocument();
             ){
            WordprocessingMLPackage wordPackage = WordprocessingMLPackage.createPackage();
            MainDocumentPart mainDocumentPart = wordPackage.getMainDocumentPart();
            mainDocumentPart.addStyledParagraphOfText("Title", "Hello World!");
            mainDocumentPart.addParagraphOfText("Welcome");
            mainDocumentPart.addParagraphOfText("To word");
            wordPackage.save(outputStream);

            XWPFParagraph tmpParagraph = document.createParagraph();
            XWPFRun tmpRun = tmpParagraph.createRun();
            tmpRun.setText("Test");
            tmpRun.setFontSize(18);
            document.write(outputStream2);
        } catch (IOException | Docx4JException e) {
            throw new RuntimeException(e);
        }
    }

    private static void zipTest(String[] files){
        try (
                ZipOutputStream zipOut = new ZipOutputStream(Files.newOutputStream(Path.of("test.zip")));

                FileInputStream excelStream = new FileInputStream(files[0]);
                XSSFWorkbook excelDocument = new XSSFWorkbook(excelStream);

                FileInputStream wordStream = new FileInputStream(files[2]);
                XWPFDocument wordDocument = new XWPFDocument(wordStream);
        ){
            WordprocessingMLPackage wordPackage = WordprocessingMLPackage.load(Files.newInputStream(Path.of(files[1])));

            // zip excel
            ZipEntry zipEntry = new ZipEntry(files[0]);
            zipOut.putNextEntry(zipEntry);
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            excelDocument.write(byteArrayOutputStream);
            zipOut.write(byteArrayOutputStream.toByteArray());
            zipOut.closeEntry();

            // zip word 1
            zipEntry = new ZipEntry(files[1]);
            zipOut.putNextEntry(zipEntry);
            byteArrayOutputStream = new ByteArrayOutputStream();
            wordPackage.save(byteArrayOutputStream);
            zipOut.write(byteArrayOutputStream.toByteArray());
            zipOut.closeEntry();

            // zip word 2
            zipEntry = new ZipEntry(files[2]);
            zipOut.putNextEntry(zipEntry);
            byteArrayOutputStream = new ByteArrayOutputStream();
            wordDocument.write(byteArrayOutputStream);
            zipOut.write(byteArrayOutputStream.toByteArray());
            zipOut.closeEntry();
        } catch (IOException | Docx4JException e) {
            throw new RuntimeException(e);
        }
    }
}
