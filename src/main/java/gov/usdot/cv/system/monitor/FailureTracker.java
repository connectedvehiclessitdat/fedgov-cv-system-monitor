package gov.usdot.cv.system.monitor;

import java.util.Date;

import gov.usdot.cv.system.monitor.util.DateUtil;

public class FailureTracker {

	private int failureCount = 0;
	private Date firstFailureDate = DateUtil.DATE_NOT_SET;
	private Date alertSentDate = DateUtil.DATE_NOT_SET;

	public void recordFailure() {
		recordFailure(DateUtil.currentTime());
		
	}
	
	public void recordFailure(Date failureDate) {
		// Record the time of this failure if this is the first failure
		if(failureCount == 0) {
			firstFailureDate = failureDate;
		}					
		failureCount++;
	}
	
	public Date getFirstFailureDate() {
		return firstFailureDate;
	}
	
	public int getFailureCount() {
		return failureCount;
	}
	
	public void clearFailure() {
		firstFailureDate = DateUtil.DATE_NOT_SET;
		failureCount = 0;
	}

	public void recordAlert() {
		recordAlert(DateUtil.currentTime());
		
	}
	
	public void recordAlert(Date alertDate) {
		alertSentDate = alertDate;
	}
	
	public Date getAlertSentDate() {
		return alertSentDate;
	}
	
	public void clearAlert() {
		alertSentDate = DateUtil.DATE_NOT_SET;
	}
	
	public boolean hasAlerted() {
		return !alertSentDate.equals(DateUtil.DATE_NOT_SET);
	}
	

}
