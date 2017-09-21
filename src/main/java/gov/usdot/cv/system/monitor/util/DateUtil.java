package gov.usdot.cv.system.monitor.util;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import org.apache.log4j.Logger;

public class DateUtil {

	private static final Logger logger = Logger.getLogger(DateUtil.class);

	public static final Date DATE_NOT_SET = new Date(0);
	public static final long ONE_MILLISECOND = 1;
	public static final long ONE_SECOND = ONE_MILLISECOND * 1000;
	public static final long ONE_MINUTE = ONE_SECOND * 60;
	public static final long ONE_HOUR = ONE_MINUTE * 60;
	public static final long ONE_DAY = ONE_HOUR * 24;
	public static final long ONE_WEEK = ONE_DAY * 7;
	public static final long ONE_YEAR = ONE_WEEK * 52;
	
	public final static SimpleDateFormat DEFAULT_SDF = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
	public final static SimpleDateFormat DAY_SDF = new SimpleDateFormat("MM/dd/yyyy");
	
	public static String format(Date date) {
		return format(date, DEFAULT_SDF);
	}
	
	public static String format(Date date, SimpleDateFormat sdf) {
		return sdf.format(date);
	}
	
	public static Date parse(String dateString) throws ParseException {
		return parse(dateString, DEFAULT_SDF);
	}
	
	public static Date parse(String dateString, SimpleDateFormat sdf) throws ParseException {
		return sdf.parse(dateString);
	}
	
	public static Date currentTime() {
		return new Date();
	}
	
	public static Date getNextTime(String hoursAndMinutes) throws ParseException {
		return getNextTime(hoursAndMinutes, DEFAULT_SDF);
	}
	
	@SuppressWarnings("deprecation")
	public static Date getNextTime(String hoursAndMinutes, SimpleDateFormat sdf) throws ParseException {
		Date hoursAndMinutesDate = parse(hoursAndMinutes, sdf);
		
		Calendar current = Calendar.getInstance();
	
		Calendar future = Calendar.getInstance();
		future.set(Calendar.HOUR_OF_DAY, hoursAndMinutesDate.getHours());
		future.set(Calendar.MINUTE, hoursAndMinutesDate.getMinutes());
		future.set(Calendar.SECOND, 0);
		if(future.before(current) || future.equals(current)) {
			// Ensure we aren't already passed the future time(i.e. the specified hour/minute won't occur again until tomorrow)
			future.add(Calendar.DAY_OF_MONTH, 1);
		}
		
		return future.getTime();
	}
	
	public static Date getStartOfDay(Date date) {
		Calendar calendarDate = Calendar.getInstance();
		calendarDate.setTime(date);
		
		calendarDate.set(Calendar.HOUR_OF_DAY, 0);
		calendarDate.set(Calendar.MINUTE, 0);
		calendarDate.set(Calendar.SECOND, 0);
		calendarDate.set(Calendar.MILLISECOND, 0);
		
		return calendarDate.getTime();
	}
	
	/**
	 * Get the start of a month for a date.  If a monthDelta is provided, the start
	 * of the month equal to the number of months from the specified date will be returned.
	 * For example,
	 *     if monthDelta is 1, then the start of the month for the month after the provided date will be returned,
	 *     if it is 2, then the start of 2 months from the provided date will be returned,
	 *     etc.
	 * 
	 * Using a negative value for the monthDelta will provide starts of months previous to the
	 * specified date.
	 * 
	 */
	public static Date getStartOfMonth(Date date, int monthDelta) {
		Calendar calendar = Calendar.getInstance();
		calendar.setTime(getStartOfDay(date));
		calendar.set(Calendar.DAY_OF_MONTH, 1);
		
		calendar.add(Calendar.MONTH, monthDelta);
		
		return calendar.getTime();
	}
	
	public static Date getEndOfMonth(Date date) {
		Calendar calendar = Calendar.getInstance();
		calendar.setTime(date);

		calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH));
		calendar.set(Calendar.HOUR_OF_DAY, calendar.getActualMaximum(Calendar.HOUR_OF_DAY));
		calendar.set(Calendar.MINUTE, calendar.getActualMaximum(Calendar.MINUTE));
		calendar.set(Calendar.SECOND, calendar.getActualMaximum(Calendar.SECOND));
		calendar.set(Calendar.MILLISECOND, calendar.getActualMaximum(Calendar.MILLISECOND));
		
		return calendar.getTime();
	}
	
	public static String duration(Date date1, Date date2) {
		
		logger.debug(String.format("Calculating duration between: %s and %s", format(date1), format(date2)));
		
		long dateDiff = Math.abs(date1.getTime() - date2.getTime());
		logger.debug(String.format("Total milliseconds between the two dates is: %d", dateDiff));
		
		StringBuilder duration = new StringBuilder();
		
		if(dateDiff >= ONE_YEAR) {
			long numYears = dateDiff / ONE_YEAR;
			duration.append(numYears).append(" year").append((numYears > 1)?("s"):(""));
			
			dateDiff -= numYears * ONE_YEAR;
			
			logger.debug(String.format("Duration modified to %s, total milliseconds left to check is %d", duration.toString(), dateDiff));
		}
		if(dateDiff >= ONE_WEEK) {
			if(duration.length() != 0) duration.append(" ");
			
			long numWeeks = dateDiff / ONE_WEEK;
			duration.append(numWeeks).append(" week").append((numWeeks > 1)?("s"):(""));
			
			dateDiff -= numWeeks * ONE_WEEK;
			
			logger.debug(String.format("Duration modified to %s, total milliseconds left to check is %d", duration.toString(), dateDiff));
		}
		if(dateDiff >= ONE_DAY) {
			if(duration.length() != 0) duration.append(" ");
			
			long numDays = dateDiff / ONE_DAY;
			duration.append(numDays).append(" day").append((numDays > 1)?("s"):(""));
			
			dateDiff -= numDays * ONE_DAY;
			
			logger.debug(String.format("Duration modified to %s, total milliseconds left to check is %d", duration.toString(), dateDiff));
		}
		if(dateDiff >= ONE_HOUR) {
			if(duration.length() != 0) duration.append(" ");
			
			long numHours = dateDiff / ONE_HOUR;
			duration.append(numHours).append(" hour").append((numHours > 1)?("s"):(""));
			
			dateDiff -= numHours * ONE_HOUR;
			
			logger.debug(String.format("Duration modified to %s, total milliseconds left to check is %d", duration.toString(), dateDiff));
		}
		if(dateDiff >= ONE_MINUTE) {
			if(duration.length() != 0) duration.append(" ");
			
			long numMinutes = dateDiff / ONE_MINUTE;
			duration.append(numMinutes).append(" minute").append((numMinutes > 1)?("s"):(""));
			
			dateDiff -= numMinutes * ONE_MINUTE;
			
			logger.debug(String.format("Duration modified to %s, total milliseconds left to check is %d", duration.toString(), dateDiff));
		}
		if(dateDiff >= ONE_SECOND) {
			if(duration.length() != 0) duration.append(" ");
			
			long numSeconds = dateDiff / ONE_SECOND;
			duration.append(numSeconds).append(" second").append((numSeconds > 1)?("s"):(""));
			
			dateDiff -= numSeconds * ONE_SECOND;
			
			logger.debug(String.format("Duration modified to %s, total milliseconds left to check is %d", duration.toString(), dateDiff));
		}
		
		return duration.toString();
	}
}
