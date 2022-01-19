package com.miniso.ecomm.bootdemoapp;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

import java.io.FileOutputStream;

public class SXSSFexample {
    public static void main(String[] args) throws Throwable {
        SXSSFWorkbook wb = new SXSSFWorkbook();
        wb.setCompressTempFiles(true);

        int max_row_per_sheet = 100 * 10000;
        int sheetIndex = 0;
        int sheetRowNum = 1;

        SXSSFSheet sh = wb.createSheet("all-data" + sheetIndex);
        sh.setRandomAccessWindowSize(100);// keep 100 rows in memory, exceeding rows will be flushed to disk
        for (int rownum = 1; rownum < 4000000; rownum++, sheetRowNum++) {
            if (rownum % max_row_per_sheet == 0) {
                sheetIndex++;
                sheetRowNum = 1;
                sh = wb.createSheet("all-data" + sheetIndex);
                sh.setRandomAccessWindowSize(100);// keep 100 rows in memory, exceeding rows will be flushed to disk
            }
            Row row = sh.createRow(sheetRowNum);
            for (int cellnum = 0; cellnum < 10; cellnum++) {
                Cell cell = row.createCell(cellnum);
                String address = new CellReference(cell).formatAsString();
                cell.setCellValue(address);
            }
        }


        FileOutputStream out = new FileOutputStream("/Users/tangpeng/tempsxssf.xlsx");
        wb.write(out);
        out.close();
    }
}
