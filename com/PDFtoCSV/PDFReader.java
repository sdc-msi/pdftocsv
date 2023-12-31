package com.PDFtoCSV;
import java.util.Scanner;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.io.PrintWriter;
import java.io.File;
import java.io.IOException;
import com.itextpdf.text.pdf.parser.PdfTextExtractor;
import com.itextpdf.text.pdf.parser.FilteredTextRenderListener;
import com.itextpdf.text.pdf.parser.RenderFilter;
import com.itextpdf.text.pdf.parser.LocationTextExtractionStrategy;
import com.itextpdf.text.pdf.parser.RegionTextRenderFilter;
import com.itextpdf.text.Rectangle;
import java.util.Iterator;
import com.itextpdf.text.pdf.parser.TextExtractionStrategy;
import com.itextpdf.text.pdf.PdfReader;
import java.util.ArrayList;

public class PDFReader
{
    private ArrayList<soe_repeat> done;
    private String[] parts;
    private String last_word;
    private String sem;
    String outputFilePath;
    String instituteCode;
    String semester;
    private String filePath;
    private short pageStart;
    private short pageEnd;
    private short totalPages;
    private PdfReader file;
    private TextExtractionStrategy strategy;
    private DataBlock data;
    private boolean isFirstTime;
    private StringBuilder csvData;
    Coordinates coords;
    
    public PDFReader(final String fp) {
        this.data = new DataBlock();
        this.done = new ArrayList<soe_repeat>();
        this.filePath = fp;
        this.pageStart = 1;
        this.isFirstTime = true;
        this.csvData = new StringBuilder();
        try {
            this.file = new PdfReader(this.filePath);
            final short n = (short)this.file.getNumberOfPages();
            this.totalPages = n;
            this.pageEnd = n;
        }
        catch (Exception e) {
            System.out.println("Could not open PDF Input File. Check the Input Path");
            System.exit(0);
        }
    }

    public PDFReader(final String inputFile,
                    final String outputFile,
                    final String instituteCode,
                    final String semester,
                    String format) 
    {
        this(inputFile);
        this.outputFilePath = outputFile;
        this.instituteCode = instituteCode;
        this.semester = semester;
        format = format.toLowerCase();
        this.coords = new Coordinates(format);

        if (format.startsWith("new")) {
            switch(this.semester) {
                case "1": this.semester = "first"; break;
                case "2": this.semester = "second";break;
                case "3": this.semester = "third";break;
                case "4": this.semester = "fourth";break;
                case "5": this.semester = "fifth";break;
                case "6": this.semester = "sixth";break;
                case "7": this.semester = "seventh";break;
                case "8": this.semester = "eighth";break;
            } 
        } else if(format.toLowerCase().equals("old")){
            this.semester = "0" + this.semester;
        }
    }
    
    public boolean isAlreadyAdded(final String prg, final String scheme) {
        for (final soe_repeat temp : this.done) {
            if (temp.prg.equals(this.data.progCode) && temp.scheme.equals(this.data.soeSchemeId)) {
                return true;
            }
        }
        return false;
    }

    
    public TextExtractionStrategy newArea(final float lly, final float llx, final float ury, final float urx) {
        final Rectangle rect = new Rectangle(lly, llx, ury, urx);
        final RenderFilter filter = (RenderFilter)new RegionTextRenderFilter(rect);
        final TextExtractionStrategy strategy = (TextExtractionStrategy)new FilteredTextRenderListener((TextExtractionStrategy)new LocationTextExtractionStrategy(), new RenderFilter[] { filter });
        return strategy;
    }
    
    public boolean isSoePage(final short pageNo) {
        this.strategy = this.newArea(coords.SOELLY , coords.SOELLX , coords.SOEURY , coords.SOEURX);
        try {
            if (PdfTextExtractor.getTextFromPage(this.file, (int)pageNo, this.strategy).toLowerCase().contains("scheme of examinations")) {
                return true;
            }
        }
        catch (Exception ex) {}
        return false;
    }
    
    public void getSoeSchemeId(final short pageNo) {
        this.strategy = this.newArea(coords.PDLLY, coords.PDLLX, coords.PDURY, coords.PDURX);
        try {
            final String pageDetails = PdfTextExtractor.getTextFromPage(this.file, (int)pageNo, this.strategy).toLowerCase();
            this.data.soeSchemeId = pageDetails.substring(pageDetails.indexOf("schemeid") + 10, pageDetails.indexOf("schemeid") + 22);
        }
        catch (Exception ex) {}
    }

    public String extractSem(String pageheader) {
        String regexPattern = "(\\w+)\\sSEMESTER";
        Pattern pattern = Pattern.compile(regexPattern);
        Matcher matcher = pattern.matcher(pageheader);

        if (matcher.find()) {
            return matcher.group(1);
        } else {
            return "";
        }
    }

    // public String extractProgCode(String pageheader) {
    //     String regexPattern = ""
    // }
    
    public void readPageDetails(final short pageNo) {
        this.strategy = this.newArea(coords.PHLLY, coords.PHLLX, coords.PHURY, coords.PHURX);
        try {
            final String pageheader = PdfTextExtractor.getTextFromPage(this.file, (int)pageNo, this.strategy);
            data.semester = extractSem(pageheader);

            // TODO : Clean up this code, modularize it just like extractSem
            // This approach throws a lot of exceptions, so I'm removing the println in the catch statement
            this.data.progCode = pageheader.substring(pageheader.toLowerCase().indexOf("programme code") + 16, pageheader.toLowerCase().indexOf("programme code") + 19);
            this.data.progName = pageheader.substring(pageheader.toLowerCase().indexOf("programme name") + 16, pageheader.toLowerCase().indexOf("sem.")).trim();
            this.data.instCode = pageheader.substring(pageheader.toLowerCase().indexOf("institution code") + 18, pageheader.toLowerCase().indexOf("institution code") + 21);
            this.data.batch = Short.parseShort(pageheader.substring(pageheader.toLowerCase().indexOf("batch") + 7, pageheader.toLowerCase().indexOf("batch") + 11).trim());

            if (!pageheader.toLowerCase().contains("result")) {
                return;
            }

            if (pageheader.toLowerCase().contains("regular")) {
                this.data.examType = "Regular";
            }
            else if (pageheader.toLowerCase().contains("reappear")) {
                this.data.examType = "Reappear";
            }
        }
        catch (Exception e) {}
    }

    // TODO : Modularize this, or at the very least clean it up!
    public void readResultsFromPage(final short pageNo) {
        float lly = coords.SRNLLY;
        float ury = coords.SRNURY;
        float llx = coords.SDLLX;
        float urx = coords.SDURX;
        for (byte i = 0; i < 10; ++i) {
            try {
                this.strategy = this.newArea(lly, llx, ury, urx);
                this.data.rollNo[i] = PdfTextExtractor.getTextFromPage(this.file, (int)pageNo, this.strategy);
                if (this.data.rollNo[i].trim().isEmpty()) {
                    this.data.rollNo[i] = null;
                    break;
                }
                lly = coords.SNLLY + i * coords.SDIY;
                ury = coords.SNURY + i * coords.SDIY;
                this.strategy = this.newArea(lly, llx, ury, urx);
                this.data.name[i] = PdfTextExtractor.getTextFromPage(this.file, (int)pageNo, this.strategy);
                lly = coords.SIDLLY + i * coords.SDIY;
                ury = coords.SIDURY + i * coords.SDIY;
                this.strategy = this.newArea(lly, llx, ury, urx);
                this.data.studentId[i] = PdfTextExtractor.getTextFromPage(this.file, (int)pageNo, this.strategy);
                lly = coords.SSIDLLY + i * coords.SDIY;
                ury = coords.SSIDURY + i * coords.SDIY;
                this.strategy = this.newArea(lly, llx, ury, urx);
                this.data.schemeId[i] = PdfTextExtractor.getTextFromPage(this.file, (int)pageNo, this.strategy);
                this.data.studentId[i] = this.data.studentId[i].substring(this.data.studentId[i].indexOf(":") + 2);
                this.data.schemeId[i] = this.data.schemeId[i].substring(this.data.schemeId[i].indexOf(":") + 2);
                llx = coords.MLLX;
                urx = coords.MURX;
                lly = coords.MLLY + i * coords.SDIY;
                ury = coords.MURY + i * coords.SDIY;
                for (byte j = 0; j < 30; ++j) {
                    this.strategy = this.newArea(lly, llx, ury, urx);
                    this.data.subjectCodes[i][j] = PdfTextExtractor.getTextFromPage(this.file, (int)pageNo, this.strategy);
                    if (this.data.subjectCodes[i][j].trim().isEmpty()) {
                        this.data.subjectCodes[i][j] = null;
                        break;
                    }

                    this.data.subjectCodes[i][j] = this.data.subjectCodes[i][j].replace('\n', ' ');


                    lly = coords.MLLY + i * coords.SDIY + coords.MRI;
                    ury = coords.MURY + i * coords.SDIY + coords.MRI;
                    urx = urx - coords.MIEI + 0.01f;
                    this.strategy = this.newArea(lly, llx, ury, urx);
                    this.data.internalMarks[i][j] = PdfTextExtractor.getTextFromPage(this.file, (int)pageNo, this.strategy);
                    llx += coords.MIEI;
                    urx += coords.MIEI;
                    this.strategy = this.newArea(lly, llx, ury, urx);
                    this.data.externalMarks[i][j] = PdfTextExtractor.getTextFromPage(this.file, (int)pageNo, this.strategy);
                    llx = coords.MLLX + j * coords.MRSI;
                    urx = coords.MURX + j * coords.MRSI;
                    lly = coords.MLLY + i * coords.SDIY + coords.LAST;
                    ury = coords.MURY + i * coords.SDIY + coords.LAST;
                    this.strategy = this.newArea(lly, llx, ury, urx);
                    this.data.totalMarks[i][j] = PdfTextExtractor.getTextFromPage(this.file, (int)pageNo, this.strategy);
                    if (this.data.totalMarks[i][j].contains("(")) {
                        this.data.grades[i][j] = this.data.totalMarks[i][j].substring(this.data.totalMarks[i][j].indexOf("(") + 1, this.data.totalMarks[i][j].indexOf(")"));
                        this.data.totalMarks[i][j] = this.data.totalMarks[i][j].substring(0, this.data.totalMarks[i][j].indexOf("("));
                    }
                    if (this.data.totalMarks[i][j].contains("*")) {
                        this.data.totalMarks[i][j] = this.data.totalMarks[i][j].replace("*", "");
                    }
                    if (this.data.internalMarks[i][j].contains("A") || this.data.internalMarks[i][j].contains("C") || this.data.internalMarks[i][j].contains("D") || this.data.internalMarks[i][j].contains("-")) {
                        this.data.internalMarks[i][j] = "0";
                    }
                    if (this.data.externalMarks[i][j].contains("A") || this.data.externalMarks[i][j].contains("C") || this.data.externalMarks[i][j].contains("D") || this.data.externalMarks[i][j].contains("-")) {
                        this.data.externalMarks[i][j] = "0";
                    }
                    if (this.data.totalMarks[i][j].contains("A") || this.data.totalMarks[i][j].contains("C") || this.data.totalMarks[i][j].contains("D") || this.data.totalMarks[i][j].contains("-")) {
                        this.data.totalMarks[i][j] = "0";
                    }
                    if (this.data.internalMarks[i][j].replaceAll("[^A-Z]", "").length() > 1) {
                        final String[] name = this.data.name;
                        final byte b = i;
                        name[b] = String.valueOf(name[b]) + this.data.internalMarks[i][j].replaceAll("[^A-Z\\s]", "").substring(1);
                        this.parts = this.data.name[i].split("  ");
                        this.last_word = this.parts[this.parts.length - 1];
                        this.data.name[i] = this.last_word;
                        this.data.name[i] = this.data.name[i].replaceAll("[^\\x20-\\x7E]", "");
                        this.data.internalMarks[i][j] = this.data.internalMarks[i][j].replaceAll("[^0-9]", "");
                        if (this.data.externalMarks[i][j].replaceAll("[^A-Z]", "").length() > 1) {
                            this.last_word = this.data.externalMarks[i][j].replaceAll("[^A-Z\\s]", "").substring(1);
                            this.parts = this.last_word.split("  ");
                            this.last_word = "";
                            for (int k = 0; k < this.parts.length; ++k) {
                                if (this.parts[k] != " ") {
                                    this.last_word = String.valueOf(this.last_word) + this.parts[k];
                                }
                            }
                            this.data.name[i] = String.valueOf(this.data.name[i]) + this.last_word;
                            this.data.name[i] = this.data.name[i].replaceAll("[^\\x20-\\x7E]", "");
                            this.data.externalMarks[i][j] = this.data.externalMarks[i][j].replaceAll("[^0-9]", "");
                        }
                    }
                    llx = coords.MLLX + (j + 1) * coords.MRSI;
                    urx = coords.MURX + (j + 1) * coords.MRSI;
                    lly -= coords.LAST;
                    ury -= coords.LAST;
                }
                lly = coords.CLLY + i * coords.SDIY;
                ury = coords.CURY + i * coords.SDIY;
                llx = coords.CLLX;
                urx = coords.CURX;
                this.strategy = this.newArea(lly, llx, ury, urx);
                this.data.credits[i] = Byte.parseByte(PdfTextExtractor.getTextFromPage(this.file, (int)pageNo, this.strategy).trim());
                lly = coords.SRNLLY + (i + 1) * coords.SDIY;
                ury = coords.SRNURY + (i + 1) * coords.SDIY;
                llx = coords.SDLLX;
                urx = coords.SDURX;
            }
            catch (Exception ex) {}
        }
    }

    void getExamTypeFromResultPage(int pageNo) {
        try {
            this.strategy = this.newArea(coords.PHLLY, coords.PHLLX,
                    coords.PHURY, coords.PHURX);
            final String pageheader = PdfTextExtractor.getTextFromPage(this.file, (int) pageNo, this.strategy);
            if (pageheader.toLowerCase().contains("regular")) {
                this.data.examType = "Regular";
            } else if (pageheader.toLowerCase().contains("reappear")) {
                this.data.examType = "Reappear";
            }
        } catch (IOException e) {
            System.out.println("Error!: " + e);
        }
    }

    void showProgress(int pageNo) {
        float percentDone = 100 * pageNo/totalPages ;
        int totalBars = 20;
        int bars = (int) percentDone/ 5;
        System.out.println("Processing: " + (int)percentDone + "%   \t[");
        for (int i = 0; i < totalBars; i++) {
            if (i <= bars) {
                System.out.print("#");
            } else {
                System.out.print(" ");
            }
        }
        System.out.print("]");
    }

    // TODO : Clean this up, reduce the if-else and nesting
    public void processFile() {
        int recordCount = 0;
        for (short i = this.pageStart; i <= this.pageEnd; ++i) {
            final int percent = i * 100 / this.totalPages;
            // showProgress(i);
           System.out.println("\rProcessing  : " + percent + "%");
            if (this.isSoePage(i)) {
                this.readPageDetails((short)(i + 1));
            }
            else {
                getExamTypeFromResultPage(i);
                if (this.data.examType.equals("Regular") &&
                 this.data.instCode.equals(this.instituteCode) &&
                 data.semester.toLowerCase().equals(semester.toLowerCase()) ) {
                    this.readResultsFromPage(i);

                    if (this.isFirstTime) {
                        this.csvData.append("S.No,Enrollment Number,Name,");
                        for (int j = 0; j < 30; ++j) {
                            
                            if (this.data.subjectCodes[0][j] != null) {
                                this.csvData.append(this.data.subjectCodes[0][j]+ "," 
                                + this.data.subjectCodes[0][j] + ","
                                + this.data.subjectCodes[0][j] + ",");
                            }
                        }
                        this.csvData.append("\n");
                        this.csvData.append(", ,");
                        for (int j = 0; j < 30; ++j) {
                            if (this.data.subjectCodes[0][j] != null) {
                                this.csvData.append(",");
                                this.csvData.append("Internal");
                                this.csvData.append(",");
                                this.csvData.append("External");
                                this.csvData.append(",");
                                this.csvData.append("Total");
                            }
                        }
                        this.csvData.append("\n");
                        this.isFirstTime = false;
                    }
                    for (int j = 0; j < 10 && this.data.rollNo[j] != null && !this.data.rollNo[j].equals(""); ++j) {
                        recordCount += 1;
                        csvData.append(recordCount + ",");
                        this.csvData.append(this.data.rollNo[j]);
                        this.csvData.append(",");
                        this.csvData.append(this.data.name[j]);
                        this.csvData.append(",");
                        for (int k = 0; k < 30 && this.data.internalMarks[j][k] != null && !this.data.internalMarks[j][k].equals(""); ++k) {
                            this.csvData.append(this.data.internalMarks[j][k]);
                            this.csvData.append(",");
                            this.csvData.append(this.data.externalMarks[j][k]);
                            this.csvData.append(",");
                            this.csvData.append(this.data.totalMarks[j][k]);
                            this.csvData.append(",");
                        }
                        this.csvData.append("\n");
                    }
                }
            }
        }
    }
    
    public void saveCSV() {
        try {
            final PrintWriter pw = new PrintWriter(new File(this.outputFilePath));
            pw.write(this.csvData.toString());
            pw.close();
            System.out.println("\nSuccessfully generated CSV File at " + this.outputFilePath);
        }
        catch (Exception e) {
            System.out.println("File could not be saved!");
            System.out.println("1. Make sure that the CSV file is NOT opened by another application!");
            System.out.println("2. Check the command line arguments entered");
            System.out.println("Error : Supply command line arguments properly!");
            System.out.println("[Input PDF File Path] [Output CSV File Path] [Institute Code]");
            System.out.println("Specify file paths IN double quotes and institute code WITHOUT double quotes");
        }
    }
    
    public static void main(final String[] args) {
        if (args.length != 5) {
            System.out.println("Error : Supply command line arguments properly!");
            System.out.println("[Input PDF File Path] [Output CSV File Path] [Institute Code] [Semester] []");
            System.out.println("[Input PDF file path] and [Output CSV file path] must be valid paths, enclosed in double quotes");
            System.out.println("Institute code should be entered WITHOUT double quotes.");
            System.out.println("Semester should be specified in words, for example 3rd semester would be written as 'third', 5th semester as 'fifth' and so on");
            System.exit(0);
        }
        
        
        final String inputFilePath = args[0];
        final PDFReader pdfreader = new PDFReader(inputFilePath);        
        pdfreader.outputFilePath = args[1];
        pdfreader.instituteCode = args[2];
        pdfreader.semester = args[3];
        args[4] = args[4].toLowerCase();
        pdfreader.coords = new Coordinates(args[4]);

        if (args[4].startsWith("new")) {
            switch (pdfreader.semester) {
                case "1": pdfreader.semester = "first"; break;
                case "2": pdfreader.semester = "second";break;
                case "3": pdfreader.semester = "third";break;
                case "4": pdfreader.semester = "fourth";break;
                case "5": pdfreader.semester = "fifth";break;
                case "6": pdfreader.semester = "sixth";break;
                case "7": pdfreader.semester = "seventh";break;
                case "8": pdfreader.semester = "eighth";break;
            }
        } else if (args[4].equals("old")) {
            pdfreader.semester = "0" + pdfreader.semester;
        }

        pdfreader.processFile();
        pdfreader.file.close();
        pdfreader.saveCSV();
        
    }

    public void runPDFReader() {
        this.processFile();
        this.file.close();
        this.saveCSV();
    }
}

