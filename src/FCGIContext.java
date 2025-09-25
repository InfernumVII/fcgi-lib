
import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentHashMap;

public class FCGIContext {
    private int role;
    private int flag;
    private ConcurrentHashMap<String, String> params = new ConcurrentHashMap<>();
    private ByteBuffer stdinData;

    public FCGIContext() {
    }
    
    

    public int getRole() {
        return role;
    }

    public void setRole(int role) {
        this.role = role;
    }

    public int getFlag() {
        return flag;
    }

    public void setFlag(int flag) {
        this.flag = flag;
    }

    public ConcurrentHashMap<String, String> getParams() {
        return params;
    }

    public void setParams(ConcurrentHashMap<String, String> params) {
        this.params = params;
    }

    public ByteBuffer getStdinData() {
        return stdinData;
    }

    public void setStdinData(ByteBuffer stdinData) {
        this.stdinData = stdinData;
    }

    @Override
    public String toString() {
        return "FCGIContext [role=" + role + ", flag=" + flag + ", params=" + params.size() + ", stdinData=" + stdinData.capacity() + "]";
    }


    
}
