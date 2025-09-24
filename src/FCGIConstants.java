public abstract class FCGIConstants 
{
    public static final int FCGIMaxLen = 0xffff;
    /*
    * Define Length of FCGI message bodies in bytes
    */
    public static final int FCGIHeaderLen   = 8;
    public static final int FCGIEndReqBodyLen   = 8;
    public static final int FCGIBeginReqBodyLen = 8;
    public static final int FCGIUnknownBodyTypeBodyLen = 8;
    /*
    * Header defines
    */
    public static int FCGIVersion1      = 1;
    /* FCGI Record Types */
    public static final int FCGIBeginRequest    = 1;
    public static final int FCGIAbortRequest    = 2;
    public static final int FCGIEndRequest  = 3;
    public static final int FCGIParams      = 4;
    public static final int FCGIStdin       = 5;
    public static final int FCGIStdout      = 6;
    public static final int FCGIStderr      = 7;
    public static final int FCGIData        = 8;
    public static final int FCGIGetValues   = 9;
    public static final int FCGIGetValuesResult = 10;
    public static final int FCGIUnknownType     = 11;
    public static final int FCGIMaxType = FCGIUnknownType;
    /* Request ID Values */
    public static final int FCGINullRequestID   = 0;
    /*
    * Begin Request defines
    */
    /* Mask flags */
    public static int FCGIKeepConn      = 1;
    /* Roles */
    public static final int FCGIResponder   = 1;
    public static final int FCGIAuthorizer  = 2;
    public static final int FCGIFilter      = 3;
    /*
    * End Request defines
    */
    /* Protocol status */
    public static final int FCGIRequestComplete = 0;
    public static final int FCGICantMpxConn = 1;
    public static final int FCGIOverload    = 2;
    public static final int FCGIUnknownRole = 3;
    /*
    * Get Values, Get Values Results  defines
    */
    public static final String FCGIMaxConns = "FCGI_MAX_CONNS";
    public static final String FCGIMaxReqs  = "FCGI_MAX_REQS";
    public static final String FCGIMpxsConns    = "FCGI_MPXS_CONNS";
    /*
    * Return codes for Process* functions
    */
    public static final int FCGIStreamRecord    = 0;
    public static final int FCGISkip        = 1;
    public static final int FCGIBeginRecord = 2;
    public static final int FCGIMgmtRecord = 3;
    /*
    * Error Codes
    */
    public static final int FCGIUnsupportedVersion = -2;
    public static final int FCGIProtocolError   = -3;
    public static final int FCGIParamsError = -4;
    public static final int FCGICallSeqError    = -5;
}