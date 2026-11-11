package game.repository.file;

/** 데이터 파일의 형식이 맞지 않을 때 던진다. 메시지에 파일 이름과 줄 번호가 들어간다. */
public class DataFormatException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String fileName;
    private final int lineNo;

    public DataFormatException(String fileName, int lineNo, String message) {
        super(fileName + ":" + lineNo + ": " + message);
        this.fileName = fileName;
        this.lineNo = lineNo;
    }

    public String getFileName() {
        return fileName;
    }

    public int getLineNo() {
        return lineNo;
    }
}
