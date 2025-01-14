package com.alibaba.excel.analysis.csv;

import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.alibaba.excel.analysis.ExcelReadExecutor;
import com.alibaba.excel.context.csv.CsvReadContext;
import com.alibaba.excel.enums.CellDataTypeEnum;
import com.alibaba.excel.enums.RowTypeEnum;
import com.alibaba.excel.exception.ExcelAnalysisException;
import com.alibaba.excel.metadata.Cell;
import com.alibaba.excel.metadata.data.ReadCellData;
import com.alibaba.excel.read.metadata.ReadSheet;
import com.alibaba.excel.read.metadata.holder.ReadRowHolder;
import com.alibaba.excel.read.metadata.holder.csv.CsvReadWorkbookHolder;
import com.alibaba.excel.util.SheetUtils;
import com.alibaba.excel.util.StringUtils;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;

/**
 * read executor
 *
 * @author zhuangjiaju
 */
@Slf4j
public class CsvExcelReadExecutor implements ExcelReadExecutor {

    private List<ReadSheet> sheetList;
    private CsvReadContext csvReadContext;

    public CsvExcelReadExecutor(CsvReadContext csvReadContext) {
        this.csvReadContext = csvReadContext;
        sheetList = new ArrayList<>();
        ReadSheet readSheet = new ReadSheet();
        sheetList.add(readSheet);
        readSheet.setSheetNo(0);
    }

    @Override
    public List<ReadSheet> sheetList() {
        return sheetList;
    }

    @Override
    public void execute() {
        Iterable<CSVRecord> parseRecords;
        try {
            parseRecords = parseRecords();
        } catch (IOException e) {
            throw new ExcelAnalysisException(e);
        }
        for (ReadSheet readSheet : sheetList) {
            readSheet = SheetUtils.match(readSheet, csvReadContext);
            if (readSheet == null) {
                continue;
            }
            csvReadContext.currentSheet(readSheet);

            int rowIndex = 0;

            for (CSVRecord record : parseRecords) {
                dealRecord(record, rowIndex++);
            }

            // The last sheet is read
            csvReadContext.analysisEventProcessor().endSheet(csvReadContext);
        }
    }

    private Iterable<CSVRecord> parseRecords() throws IOException {
        CsvReadWorkbookHolder csvReadWorkbookHolder = csvReadContext.csvReadWorkbookHolder();
        CSVFormat csvFormat = csvReadWorkbookHolder.getCsvFormat();

        if (csvReadWorkbookHolder.getMandatoryUseInputStream()) {
            return csvFormat.parse(new InputStreamReader(csvReadWorkbookHolder.getInputStream(),csvReadWorkbookHolder.getEncoding()));
        }
        if (csvReadWorkbookHolder.getFile() != null) {
            return csvFormat.parse(new FileReader(csvReadWorkbookHolder.getFile()));
        }
        return csvFormat.parse(new InputStreamReader(csvReadWorkbookHolder.getInputStream(),csvReadWorkbookHolder.getEncoding()));
    }

    private void dealRecord(CSVRecord record, int rowIndex) {
        Map<Integer, Cell> cellMap = new LinkedHashMap<>();
        Iterator<String> cellIterator = record.iterator();
        int columnIndex = 0;
        while (cellIterator.hasNext()) {
            String cellString = cellIterator.next();
            ReadCellData<String> readCellData = new ReadCellData<>();
            readCellData.setRowIndex(rowIndex);
            readCellData.setColumnIndex(columnIndex);

            // csv is an empty string of whether <code>,,</code> is read or <code>,"",</code>
            if (StringUtils.isNotBlank(cellString)) {
                readCellData.setType(CellDataTypeEnum.STRING);
                readCellData.setStringValue(cellString);
            } else {
                readCellData.setType(CellDataTypeEnum.EMPTY);
            }
            cellMap.put(columnIndex++, readCellData);
        }

        RowTypeEnum rowType = MapUtils.isEmpty(cellMap) ? RowTypeEnum.EMPTY : RowTypeEnum.DATA;
        ReadRowHolder readRowHolder = new ReadRowHolder(rowIndex, rowType,
            csvReadContext.readWorkbookHolder().getGlobalConfiguration(), cellMap);
        csvReadContext.readRowHolder(readRowHolder);

        csvReadContext.csvReadSheetHolder().setCellMap(cellMap);
        csvReadContext.csvReadSheetHolder().setRowIndex(rowIndex);
        csvReadContext.analysisEventProcessor().endRow(csvReadContext);
    }
}
