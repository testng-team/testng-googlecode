package org.testng;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;


/**
 * A {@link SkipException} extension that transforms a skipped method
 * into a failed method based on a time trigger.
 * <p/>
 * By default the time format is yyyy/MM/dd (according to {@code SimpleDateFormat}). 
 * You can customize this by using the specialized constructors. Suppported date
 * formats are according to the {@code SimpleDateFormat}.
 * 
 * @author <a href='mailto:the[dot]mindstorm[at]gmail[dot]com'>Alex Popescu</a>
 * @since 5.6
 */
public class TimeBombedSkipException extends SkipException {
  private static final SimpleDateFormat SDF= new SimpleDateFormat("yyyy/MM/dd");
  private Calendar m_expireDate;
  private DateFormat m_inFormat= SDF;
  private DateFormat m_outFormat= SDF;
  private volatile boolean m_stackChanged= false;
  
  /**
   * Creates a {@code TimeBombedSkipException} using the <tt>expirationDate</tt>.
   * The format used for date comparison is <tt>yyyy/MM/dd</tt>
   * @param msg exception message
   * @param expirationDate time limit after which the SKIP becomes a FAILURE
   */
  public TimeBombedSkipException(String msg, Date expirationDate) {
    super(msg);
    initExpireDate(expirationDate);
  }
  
  /**
   * Creates a {@code TimeBombedSkipException} using the <tt>expirationDate</tt>.
   * The <tt>format</tt> parameter wiil be used for performing the time comparison.
   * @param msg exception message
   * @param expirationDate time limit after which the SKIP becomes a FAILURE
   * @param format format for the time comparison
   */
  public TimeBombedSkipException(String msg, Date expirationDate, String format) {
    super(msg);
    m_inFormat= new SimpleDateFormat(format);
    m_outFormat= new SimpleDateFormat(format);
    initExpireDate(expirationDate);
  }
  
  /**
   * Creates a {@code TimeBombedSkipException} using the <tt>date</tt>
   * in the format <tt>yyyy/MM/dd</tt>.
   * @param msg exception message
   * @param date time limit after which the SKIP becomes a FAILURE
   */
  public TimeBombedSkipException(String msg, String date) {
    super(msg);
    initExpireDate(date);
  }
  
  /**
   * Creates a {@code TimeBombedSkipException} using the <tt>date</tt>
   * in the specified format <tt>format</tt>. The same format is used
   * when performing the time comparison.
   * @param msg exception message
   * @param date time limit after which the SKIP becomes a FAILURE
   * @param format format of the passed in <tt>date</tt> and of the time comparison
   */
  public TimeBombedSkipException(String msg, String date, String format) {
    this(msg, date, format, format);
  }
  
  /**
   * Creates a {@code TimeBombedSkipException} using the <tt>date</tt>
   * in the specified format <tt>inFormat</tt>. The <tt>outFormat</tt> will be
   * used to perform the time comparison and display.
   * @param msg exception message
   * @param date time limit after which the SKIP becomes a FAILURE
   * @param inFormat format of the passed in <tt>date</tt>
   * @param outFormat format of the time comparison
   */
  public TimeBombedSkipException(String msg, String date, String inFormat, String outFormat) {
    super(msg);
    m_inFormat= new SimpleDateFormat(inFormat);
    m_outFormat= new SimpleDateFormat(outFormat);
    initExpireDate(date);
  }
  
  private void initExpireDate(Date expireDate) {
    m_expireDate= Calendar.getInstance();
    m_expireDate.setTime(expireDate);    
  }
  
  private void initExpireDate(String date) {
    try {
      Date d= m_inFormat.parse(date);
      initExpireDate(d);
    }
    catch(ParseException pex) {
      throw new TestNGException("Cannot parse date:" + date + " using pattern: " + m_inFormat, pex);
    }
  }
  
  public boolean isSkip() {
    if(null == m_expireDate) return false;
    
    try {
      Calendar now= Calendar.getInstance();
      Date nowDate= m_inFormat.parse(m_inFormat.format(now.getTime()));
      now.setTime(nowDate);
      
      return !now.after(m_expireDate);
    }
    catch(ParseException pex) {
      throw new TestNGException("Cannot compare dates.");
    }
  }

  public String getMessage() {
    if(isSkip()) {
      return super.getMessage();
    }
    else {
      return super.getMessage() + "; Test must have been enabled by: " + m_outFormat.format(m_expireDate.getTime());
    }
  }

  public void printStackTrace(PrintStream s) {
    reduceStackTrace();
    super.printStackTrace(s);
  }

  public void printStackTrace(PrintWriter s) {
    reduceStackTrace();
    super.printStackTrace(s);
  }

  
}
