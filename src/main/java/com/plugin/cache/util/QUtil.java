package com.plugin.cache.util;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URLEncoder;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Hashtable;
import java.util.Properties;
import java.util.StringTokenizer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Static helpers shared across the gateway plugins: lenient number parsing, property-file
 * loading, date formatting, string escaping, query-string parsing and log-line building.
 * <p>
 * <b>Thread safety:</b> the {@code now}, {@code getDate}, {@code getDateString} and
 * {@code compareAndGetMaxDate} methods share static {@link SimpleDateFormat} instances, which
 * are not thread-safe. Under concurrent use they can return wrong results or throw.
 * {@link #millisToDate(long)} creates its own formatter and is safe.
 * <p>
 * <b>Log delimiter:</b> the {@code addToBufferLog} methods separate fields with a delimiter
 * chosen at class load time:
 * <ul>
 * <li>system property {@code qmanager.logger.enable} = {@code "1"} (the default): the value of
 * {@code java.logger.delimiter}, or the control character {@code 0x16} (SYN) if that is unset;</li>
 * <li>otherwise a comma.</li>
 * </ul>
 */
public class QUtil {


    /** Event id for queue creation. */
    public final static int QCREATE_EVENTID = 1000;
    /** Event id for queue deletion. */
    public final static int QDELETE_EVENTID = 1001;
    /** Event id for a queue becoming inactive. */
    public final static int QINACTIVE_EVENTID = 1002;
    /** Event name for queue creation. */
    public final static String QCREATE_EVENT = "QCREATE";
    /** Event name for queue deletion. */
    public final static String QDELETE_EVENT = "QDELETE";
    /** Event name for a queue becoming inactive. */
    public final static String QINACTIVE_EVENT = "QINACTIVE";
    /** Severity flag: critical. Values are bit flags and may be OR-ed together. */
    public final static int SEVERITY_CRITICAL = 1;
    /** Severity flag: debug. */
    public final static int SEVERITY_DEBUG     = 2;
    /** Severity flag: warning. */
    public final static int SEVERITY_WARNING  = 4;
    /** Severity flag: info. */
    public final static int SEVERITY_INFO   = 8;
    static Logger LOGGER = LogManager.getLogger(QUtil.class);

    /** Field separator used by the {@code addToBufferLog} methods; see the class description. */
    static String delimiter = ",";

    static {
        boolean m_bJavaLoggerEnabled = System.getProperty("qmanager.logger.enable", "1").equalsIgnoreCase("1");
        String tmpdelimiter = System.getProperty("java.logger.delimiter");
        if (m_bJavaLoggerEnabled) {
            byte b = 0X16;
            char c = (char) b;
            if (tmpdelimiter == null) {
                delimiter = "" + c;
            } else {
                delimiter = tmpdelimiter;
            }
        }
    }

    /**
     * Parses an int, falling back to a default instead of throwing.
     *
     * @param strVal   the text to parse; may be {@code null}
     * @param nDefault value returned if {@code strVal} is {@code null} or not a valid int
     * @return the parsed value or {@code nDefault}
     */
    public static int intVal(String strVal, int nDefault) {
        try {
            return Integer.parseInt(strVal);
        } catch (Exception ex) {
            return nDefault;
        }
    }

    /**
     * Parses a long, falling back to a default instead of throwing.
     *
     * @param strVal   the text to parse; may be {@code null}
     * @param nDefault value returned if {@code strVal} is {@code null} or not a valid long
     * @return the parsed value or {@code nDefault}
     */
    public static long longVal(String strVal, long nDefault) {
        try {
            return Long.parseLong(strVal);
        } catch (Exception ex) {
            return nDefault;
        }
    }

    /**
     * Loads a properties file from the file system into an existing {@link Properties} object.
     *
     * @param strPropertyFile path of the file to read
     * @param props           target object; loaded entries are added to it
     * @return {@code props} on success, or {@code null} if the file cannot be read
     *         (the error is printed to {@code System.out})
     */
    public static Properties loadProperties(String strPropertyFile, Properties props) {
        FileInputStream fin = null;
        try {
            fin = new FileInputStream(strPropertyFile);
            props.load(fin);
            return props;
        } catch (Exception ex) {
            System.out.println("loadProperties() Failed. Ex : " + ex.getMessage());
        } finally {
            try {
                fin.close();
            } catch (Exception e) {
            }
        }
        return null;
    }
    /** Shared {@code yyyy-MM-dd HH:mm:ss.SSS} formatter (not thread-safe). */
    static SimpleDateFormat sdfWithMilli = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
    /** Shared {@code yyyy-MM-dd HH:mm:ss} formatter (not thread-safe). */
    static SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    /**
     * Formats the current time in the JVM default time zone. Not thread-safe.
     *
     * @param bIncludeMilliSeconds {@code true} for {@code yyyy-MM-dd HH:mm:ss.SSS},
     *                             {@code false} for {@code yyyy-MM-dd HH:mm:ss}
     * @return the formatted timestamp
     */
    public static String now(boolean bIncludeMilliSeconds) {
        if (bIncludeMilliSeconds) {
            return sdfWithMilli.format(new java.util.Date());
        } else {
            return sdf.format(new java.util.Date());
        }
    }

    /**
     * Formats the given date. Not thread-safe.
     *
     * @param bIncludeMilliSeconds {@code true} to include milliseconds
     * @param d                    the date to format
     * @return the formatted timestamp
     */
    public static String now(boolean bIncludeMilliSeconds, Date d) {
        if (bIncludeMilliSeconds) {
            return sdfWithMilli.format(d);
        } else {
            return sdf.format(d);
        }
    }

    /**
     * Parses a timestamp produced by {@link #now(boolean)}. Not thread-safe.
     *
     * @param bIncludeMilliSeconds {@code true} if {@code d} includes milliseconds
     * @param d                    the text to parse
     * @return the parsed date, or {@code null} if parsing fails (the error is logged)
     */
    public static Date getDate(boolean bIncludeMilliSeconds, String d) {
        try {
            if (bIncludeMilliSeconds) {
                return sdfWithMilli.parse(d);
            } else {
                return sdf.parse(d);
            }
        } catch (ParseException ex) {
        	LOGGER.error("getDate parse is failed for datestring "+d, ex);
        }
        return null;
    }
    /**
     * Formats epoch milliseconds as {@code yyyy-MM-dd HH:mm:ss}. Not thread-safe;
     * see {@link #millisToDate(long)} for a thread-safe equivalent.
     *
     * @param lMilliSeconds milliseconds since the epoch
     * @return the formatted timestamp, or an empty string on error
     */
    public static String getDateString(long lMilliSeconds) {
        try {
            Date d = new Date(lMilliSeconds);
            return sdf.format(d);
            
        } catch (Exception ex) {
        	LOGGER.error("getDateString is failed for millis "+lMilliSeconds, ex);
        }
        return "";
    }

    /**
     * Returns the later of two timestamp strings. Not thread-safe.
     *
     * @param strDate1             first timestamp
     * @param strDate2             second timestamp
     * @param bIncludeMilliSeconds {@code true} if the timestamps include milliseconds
     * @return {@code strDate2} if it is strictly later, otherwise {@code strDate1};
     *         also {@code strDate1} if either value cannot be parsed
     */
    public static String compareAndGetMaxDate(String strDate1, String strDate2, boolean bIncludeMilliSeconds) {
        String maxdate = strDate1;
        try {
            Date date1 = bIncludeMilliSeconds ? sdfWithMilli.parse(strDate1) : sdf.parse(strDate1);
            Date date2 = bIncludeMilliSeconds ? sdfWithMilli.parse(strDate2) : sdf.parse(strDate2);
            if (date1.before(date2)) {
                maxdate = strDate2;
            }
        } catch (Exception e) {
        }
        return maxdate;
    }

    /**
     * Appends an int, optionally followed by a comma.
     *
     * @param strBuff      the buffer to append to
     * @param nVal         the value
     * @param bAppendComma {@code true} to append {@code ","} after the value
     * @return {@code strBuff}, for chaining
     */
    public static StringBuffer addToBuffer(StringBuffer strBuff, int nVal, boolean bAppendComma) {
        strBuff.append(nVal);
        if (bAppendComma) {
            strBuff.append(",");
        }
        return strBuff;
    }

    /**
     * Appends a long, optionally followed by a comma.
     *
     * @param strBuff      the buffer to append to
     * @param nVal         the value
     * @param bAppendComma {@code true} to append {@code ","} after the value
     * @return {@code strBuff}, for chaining
     */
    public static StringBuffer addToBuffer(StringBuffer strBuff, long nVal, boolean bAppendComma) {
        strBuff.append(nVal);
        if (bAppendComma) {
            strBuff.append(",");
        }
        return strBuff;
    }

    /**
     * Backslash-escapes a string MySQL-style: NUL, newline, carriage return, backslash,
     * double quote, single quote and Ctrl-Z ({@code \032}).
     * <p>
     * Not a defence against SQL injection; use prepared statements for that.
     *
     * @param s the text to escape; may be {@code null}
     * @return the escaped text, or an empty string for {@code null}/empty input
     */
    public static String strEscape(String s) {
        if (s == null || s.length() <= 0) {
            return "";
        }
        boolean bConvert = false;
        StringBuffer sb = new StringBuffer(s);
        for (int i = 0; i < sb.length(); i++) {
            char c = sb.charAt(i);
            switch (c) {
                case '\0':
                    sb.setCharAt(i++, '\\');
                    sb.insert(i, "0"); // Must be escaped for "mysql"
                    break;
                case '\n':
                    sb.setCharAt(i++, '\\');
                    sb.insert(i, "n");// Must be escaped for logs
                    break;
                case '\r':
                    sb.setCharAt(i++, '\\');
                    sb.insert(i, "r");
                    break;
                case '\\':
                    sb.setCharAt(i++, '\\');
                    sb.insert(i, "\\");
                    break;
                case '\"':
                    sb.setCharAt(i++, '\\');
                    sb.insert(i, "\"");
                    break;
                case '\'':
                    sb.setCharAt(i++, '\\');
                    sb.insert(i, "\'");// Better safe than sorry
                    break;
                case '\032':
                    sb.setCharAt(i++, '\\');
                    sb.insert(i, "Z"); // This gives problems on Win32
                    break;
                default:
                    break;
            }
        }
        return sb.toString();
    }

    /**
     * Parses several {@code |}-separated query strings into one map each.
     * Each segment is parsed as in {@link #getExtraparamslist(String)}.
     * <p>
     * Example: {@code "a=1&b=2|a=3"} gives {@code [{a=1, b=2}, {a=3}]}.
     *
     * @param strExtraparams the text to parse; must not be {@code null}
     * @return one map per segment, in order
     */
    public static ArrayList<Hashtable> getExtraparamslistMap(String strExtraparams) {
        String[] arr = strExtraparams.split("\\|");
        ArrayList<Hashtable> al = new ArrayList<Hashtable>();
        for (int i = 0; i < arr.length; i++) {
            String url = arr[i];
            Hashtable table = new Hashtable();
            StringTokenizer stzExtraparmas = new StringTokenizer(url, "&");
            while (stzExtraparmas.hasMoreTokens()) {
                StringTokenizer stzExtparam = new StringTokenizer(stzExtraparmas.nextToken(), "=");
                if (stzExtparam.countTokens() == 2) {
                    String key = stzExtparam.nextToken();
                    String value = stzExtparam.nextToken();
                    table.put(key, value);
                }
            }
            al.add(table);
        }
        return al;
    }

    /**
     * Parses a query string ({@code key1=value1&key2=value2}) into a map.
     * <p>
     * Values are not URL-decoded. A pair is <b>dropped</b> unless it splits into exactly two
     * non-empty parts on {@code =}, so {@code k=}, {@code k} and {@code k=a=b} are all ignored.
     * Later duplicates overwrite earlier ones.
     *
     * @param strExtraparams the text to parse; must not be {@code null}
     * @return the key/value pairs
     */
    public static Hashtable getExtraparamslist(String strExtraparams) {
        Hashtable table = new Hashtable();
        StringTokenizer stzExtraparmas = new StringTokenizer(strExtraparams, "&");
        while (stzExtraparmas.hasMoreTokens()) {
            StringTokenizer stzExtparam = new StringTokenizer(stzExtraparmas.nextToken(), "=");
            if (stzExtparam.countTokens() == 2) {
                String key = stzExtparam.nextToken();
                String value = stzExtparam.nextToken();
                table.put(key, value);
            }
        }
        return table;
    }

    /**
     * Appends a string field to a log line, wrapped in single quotes. Any single quote inside
     * the value is replaced with a double quote; {@code null} becomes {@code ''}.
     *
     * @param strBuff      the buffer to append to
     * @param strVal       the value; may be {@code null}
     * @param bAppendComma {@code true} to append the log delimiter (see class description,
     *                     not necessarily a comma) after the field
     * @return {@code strBuff}, for chaining
     */
    public static StringBuffer addToBufferLog(StringBuffer strBuff, String strVal, boolean bAppendComma) {
        if (strVal != null) {
            strVal = strVal.replaceAll("\\'", "\"");
        } else {
            strVal = "";
        }
        strBuff.append("\'" + strVal + "\'");
        if (bAppendComma) {
            strBuff.append(delimiter);
        }
        return strBuff;
    }

    /**
     * Appends an int field (unquoted) to a log line.
     *
     * @param strBuff      the buffer to append to
     * @param nVal         the value
     * @param bAppendComma {@code true} to append the log delimiter after the field
     * @return {@code strBuff}, for chaining
     */
    public static StringBuffer addToBufferLog(StringBuffer strBuff, int nVal, boolean bAppendComma) {
        strBuff.append(nVal);
        if (bAppendComma) {
            strBuff.append(delimiter);
        }
        return strBuff;
    }

    /**
     * Appends a long field (unquoted) to a log line.
     *
     * @param strBuff      the buffer to append to
     * @param nVal         the value
     * @param bAppendComma {@code true} to append the log delimiter after the field
     * @return {@code strBuff}, for chaining
     */
    public static StringBuffer addToBufferLog(StringBuffer strBuff, long nVal, boolean bAppendComma) {
        strBuff.append(nVal);
        if (bAppendComma) {
            strBuff.append(delimiter);
        }
        return strBuff;
    }

    /**
     * @param datetime a past instant, in epoch milliseconds
     * @return whole minutes elapsed from {@code datetime} until now (truncated;
     *         negative if {@code datetime} is in the future)
     */
    public static long getDiffMinutes(long datetime) {
        long current=System.currentTimeMillis();
        return (((current-datetime)/1000)/60);
    }
    /**
     * Formats epoch milliseconds as {@code yyyy-MM-dd HH:mm:ss} in the JVM default time zone.
     * Thread-safe (uses a new formatter per call).
     *
     * @param currentDateTime milliseconds since the epoch
     * @return the formatted timestamp
     */
    public static String millisToDate(long currentDateTime) {
        //    long currentDateTime = System.currentTimeMillis();
        //creating Date from millisecond
        Date currentDate = new Date(currentDateTime);
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        //formatted value of current Date
        return df.format(currentDate);
    }



   
}

