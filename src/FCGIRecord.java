/*
 * typedef struct {
             unsigned char version;
             unsigned char type;
             unsigned char requestIdB1;
             unsigned char requestIdB0;
             unsigned char contentLengthB1;
             unsigned char contentLengthB0;
             unsigned char paddingLength;
             unsigned char reserved;
             unsigned char contentData[contentLength];
             unsigned char paddingData[paddingLength];
         } FCGI_Record;
 */


import java.nio.ByteBuffer;
import java.util.Arrays;

public class FCGIRecord {
    private int version;
    private int type;
    private int requestId;
    private int contentLength;
    private int paddingLength;
    private ByteBuffer contentData;

    public FCGIRecord(int version, int type, int requestId, int contentLength, int paddingLength, ByteBuffer contentData) {
        this.version = version;
        this.type = type;
        this.requestId = requestId;
        this.contentLength = contentLength;
        this.paddingLength = paddingLength;
        this.contentData = contentData;
    }

    @Override
    public String toString() {
        return "FCGIRecord [version=" + version + ", type=" + type + ", requestId=" + requestId + ", contentLength="
                + contentLength + ", paddingLength=" + paddingLength + ", contentData=" + contentData.capacity()
                + "]";
    }

    public int getVersion() {
        return version;
    }

    public int getType() {
        return type;
    }

    public int getRequestId() {
        return requestId;
    }

    public int getContentLength() {
        return contentLength;
    }

    public int getPaddingLength() {
        return paddingLength;
    }

    public ByteBuffer getContentData() {
        return contentData;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public void setType(int type) {
        this.type = type;
    }

    public void setRequestId(int requestId) {
        this.requestId = requestId;
    }

    public void setContentLength(int contentLength) {
        this.contentLength = contentLength;
    }

    public void setPaddingLength(int paddingLength) {
        this.paddingLength = paddingLength;
    }

    public void setContentData(ByteBuffer contentData) {
        this.contentData = contentData;
    }

        
}
