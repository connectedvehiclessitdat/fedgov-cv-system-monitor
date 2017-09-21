package gov.usdot.cv.system.monitor.uptime.reporter;

import java.io.File;
import java.io.PrintStream;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.log4j.Logger;

import gov.usdot.cv.system.monitor.EmailSender;
import gov.usdot.cv.system.monitor.SystemMonitorConfig.UptimeConfig;
import gov.usdot.cv.system.monitor.constants.EmailConfiguration;
import gov.usdot.cv.system.monitor.constants.IpType;
import gov.usdot.cv.system.monitor.constants.MonitorDialogId;
import gov.usdot.cv.system.monitor.constants.Warehouse;
import gov.usdot.cv.system.monitor.uptime.Asset;
import gov.usdot.cv.system.monitor.uptime.Asset.AssetState;
import gov.usdot.cv.system.monitor.uptime.database.AssetDAO;
import gov.usdot.cv.system.monitor.uptime.database.ConnectionManager;
import gov.usdot.cv.system.monitor.util.DateUtil;

public class AssetReporter {

	private static Logger logger = Logger.getLogger(AssetReporter.class);
	
	private final DecimalFormat PERCENTAGE_FORMAT = new DecimalFormat("##0%");

	private Reporter reporter;
	private ConnectionManager connectionManager;
	private AssetDAO assetDAO;

	private String systemName;
	private String reportLocation;
	private String[] reportEmails;
	
	public AssetReporter(UptimeConfig config) {
		this.connectionManager = new ConnectionManager(config);

		this.reporter = new Reporter();
		
		this.systemName = config.systemName;
		this.reportLocation = config.reporter.reportLocation;
		this.reportEmails = config.reporter.reportEmails;
		
		PERCENTAGE_FORMAT.setRoundingMode(RoundingMode.HALF_UP);
	}
	
	public void start() throws SQLException, ParseException {
		// do all the 1 time startup tasks
		this.assetDAO = new AssetDAO(connectionManager.getConnection());
				
		File reportDir = new File(reportLocation);
		if (!reportDir.exists()) {
			reportDir.mkdirs();
		}
		
		// start the reporting thread
		new Thread(reporter).start();
	}
	
	public void stop() {
		reporter.stop();
		this.connectionManager.closeAllConnections();
	}
	
	private void printSummary(List<ReportLine> reportLines, PrintStream reportStream) {
		// These expected counts are based on the possible combinations of IP type, Secure, DialogID, and Destination
		// 
		// For SDC
		//		There is IPv4 & IPv6 = 2
		//		This can be secure or non-secure = 2
		//		The summary is based off of ISD and VSD = 2
		//		These go to both SDW & SDPC = 2
		//		So we expect a count of 2 * 2 * 2 * 2 = 16 possible values for each state of UP, DOWN, and UNKNOWN
		// 
		// For SDW
		//		There is IPv4 & IPv6 = 2
		//		This can be secure or non-secure = 2
		//		The summary is based off of ASD = 1
		//		These go to only SDW = 1
		//		So we expect a count of 2 * 2 * 1 * 1 = 4 possible values for each state of UP, DOWN, and UNKNOWN
		// 
		// For SDPC
		//		There is IPv4 & IPv6 = 2
		//		This can be secure or non-secure = 2
		//		The summary is based off of ISD and VSD = 2
		//		These go to only SDPC = 1
		//		So we expect a count of 2 * 2 * 2 * 1 = 8 possible values for each state of UP, DOWN, and UNKNOWN
		// 
		// For ORDS
		//		There is IPv4 & IPv6 = 2
		//		This can be secure or non-secure = 2
		//		The summary is based off of Subscriptions = 1
		//		These go to only the subscription database = 1
		//		So we expect a count of 2 * 2 * 1 * 1 = 4 possible values for each state of UP, DOWN, and UNKNOWN
		final double SDC_EXPECTED_COUNT = 16;
		final double SDW_EXPECTED_COUNT = 4;
		final double SDPC_EXPECTED_COUNT = 8;
		final double ORDS_EXPECTED_COUNT = 4;
		
		double sdcUp, sdcDown, sdcUnknown, sdwUp, sdwDown, sdwUnknown, sdpcUp, sdpcDown, sdpcUnknown, ordsUp, ordsDown, ordsUnknown;
		sdcUp = sdcDown = sdcUnknown = sdwUp = sdwDown = sdwUnknown = sdpcUp = sdpcDown = sdpcUnknown = ordsUp = ordsDown = ordsUnknown = 0;
		 
		for(ReportLine reportLine : reportLines) {
			logger.debug(
					String.format("Adding asset with dialog ID of [%s] and state of [%s] to summary.",
									reportLine.getDialogId().getType(),
									reportLine.getState()));
			
			switch (reportLine.getDialogId()) {
				case ISD :
				case VSD :
					if(reportLine.getState() == AssetState.UP) {
						sdcUp += reportLine.getPercentage();
						logger.debug(String.format("SDC UP total changed to [%f]", sdcUp));
						
						if(reportLine.getTarget() == Warehouse.SDPC || reportLine.getTarget() == Warehouse.ALL) {
							sdpcUp += reportLine.getPercentage();
							logger.debug(String.format("SDPC UP total changed to [%f]", sdpcUp));
						}
			 		}
			 		else if(reportLine.getState() == AssetState.DOWN) {
						sdcDown += reportLine.getPercentage();
						logger.debug(String.format("SDC DOWN total changed to [%f]", sdcDown));

						if(reportLine.getTarget() == Warehouse.SDPC || reportLine.getTarget() == Warehouse.ALL) {
							sdpcDown += reportLine.getPercentage();
							logger.debug(String.format("SDPC DOWN total changed to [%f]", sdpcDown));
						}
					}
					else if(reportLine.getState() == AssetState.UNKNOWN) {
						sdcUnknown += reportLine.getPercentage();
						logger.debug(String.format("SDC UNKNOWN total changed to [%f]", sdcUnknown));

						if(reportLine.getTarget() == Warehouse.SDPC || reportLine.getTarget() == Warehouse.ALL) {
							sdpcUnknown += reportLine.getPercentage();
							logger.debug(String.format("SDPC UNKNOWN total changed to [%f]", sdpcUnknown));
						}
					}
					break;
				case ASD :
					if(reportLine.getState() == AssetState.UP) {
						sdwUp += reportLine.getPercentage();
						logger.debug(String.format("SDW UP total changed to [%f]", sdwUp));
					}
					else if(reportLine.getState() == AssetState.DOWN) {
						sdwDown += reportLine.getPercentage();
						logger.debug(String.format("SDW DOWN total changed to [%f]", sdwDown));
					}
					else if(reportLine.getState() == AssetState.UNKNOWN) {
						sdwUnknown += reportLine.getPercentage();
						logger.debug(String.format("SDW UNKNOWN total changed to [%f]", sdwUnknown));
					}
					break;
				case SUB :
					if(reportLine.getState() == AssetState.UP) {
						ordsUp += reportLine.getPercentage();
						logger.debug(String.format("ORDS UP total changed to [%f]", ordsUp));
					}
					else if(reportLine.getState() == AssetState.DOWN) {
						ordsDown += reportLine.getPercentage();
						logger.debug(String.format("ORDS DOWN total changed to [%f]", ordsDown));
					}
					else if(reportLine.getState() == AssetState.UNKNOWN) {
						ordsUnknown += reportLine.getPercentage();
						logger.debug(String.format("ORDS UNKNOWN total changed to [%f]", ordsUnknown));
					}
					break;
				case UNKNOWN :
				default :
					break;
			}
		}
		
		double sdcUpAvg = getAverage(sdcUp, SDC_EXPECTED_COUNT);
		double sdcDownAvg = getAverage(sdcDown, SDC_EXPECTED_COUNT);
		double sdcUnknownAvg = getAverage(sdcUnknown, SDC_EXPECTED_COUNT);
		double sdwUpAvg = getAverage(sdwUp, SDW_EXPECTED_COUNT);
		double sdwDownAvg = getAverage(sdwDown, SDW_EXPECTED_COUNT);
		double sdwUnknownAvg = getAverage(sdwUnknown, SDW_EXPECTED_COUNT);
		double sdpcUpAvg = getAverage(sdpcUp, SDPC_EXPECTED_COUNT);
		double sdpcDownAvg = getAverage(sdpcDown, SDPC_EXPECTED_COUNT);
		double sdpcUnknownAvg = getAverage(sdpcUnknown, SDPC_EXPECTED_COUNT);
		double ordsUpAvg = getAverage(ordsUp, ORDS_EXPECTED_COUNT);
		double ordsDownAvg = getAverage(ordsDown, ORDS_EXPECTED_COUNT);
		double ordsUnknownAvg = getAverage(ordsUnknown, ORDS_EXPECTED_COUNT);
		logger.debug(
				String.format("SDC Averages: UP = [%f], DOWN = [%f], UNKNOWN = [%f]%n" +
							  "SDW Averages: UP = [%f], DOWN = [%f], UNKNOWN = [%f]%n" +
							  "SDPC Averages: UP = [%f], DOWN = [%f], UNKNOWN = [%f]%n" +
							  "ORDS Averages: UP = [%f], DOWN = [%f], UNKNOWN = [%f]",
							  sdcUpAvg, sdcDownAvg, sdcUnknownAvg,
							  sdwUpAvg, sdwDownAvg, sdwUnknownAvg,
							  sdpcUpAvg, sdpcDownAvg, sdpcUnknownAvg,
							  ordsUpAvg, ordsDownAvg, ordsUnknownAvg));
		
		reportStream.println(
				String.format("SDC,\tUP, %s,\tDOWN, %s,\tUNKNOWN, %s",
							PERCENTAGE_FORMAT.format(sdcUpAvg),
							PERCENTAGE_FORMAT.format(sdcDownAvg),
							PERCENTAGE_FORMAT.format(sdcUnknownAvg)
						)
				);
		reportStream.println(
				String.format("SDW,\tUP, %s,\tDOWN, %s,\tUNKNOWN, %s",
							PERCENTAGE_FORMAT.format(sdwUpAvg),
							PERCENTAGE_FORMAT.format(sdwDownAvg),
							PERCENTAGE_FORMAT.format(sdwUnknownAvg)
						)
				);
		reportStream.println(
				String.format("SDPC,\tUP, %s,\tDOWN, %s,\tUNKNOWN, %s",
							PERCENTAGE_FORMAT.format(sdpcUpAvg),
							PERCENTAGE_FORMAT.format(sdpcDownAvg),
							PERCENTAGE_FORMAT.format(sdpcUnknownAvg)
						)
				);
		reportStream.println(
				String.format("ORDS,\tUP, %s,\tDOWN, %s,\tUNKNOWN, %s",
							PERCENTAGE_FORMAT.format(ordsUpAvg),
							PERCENTAGE_FORMAT.format(ordsDownAvg),
							PERCENTAGE_FORMAT.format(ordsUnknownAvg)
						)
				);
	}
	
	private double getAverage(double value, double total) {
		// Watch out for division by 0
		return (total == 0) ? (0) : (value / total);
	}
	
	private String buildHeader() {
		return "IP_TYPE,SECURE,DIALOG_ID,TARGET,STATE,UPTIME";
	}
	
	private List<ReportLine> buildReportLines(List<Asset> assets, Date reportEndDate) {
		List<ReportLine> reportLines = new LinkedList<ReportLine>();
		Map<AssetState, Long> timeAccumaltion = new HashMap<AssetState, Long>();
		
		if(assets.size() > 0) {
			
			Asset firstOfState = null;
			
			for(Asset currentAsset : assets) {
				if(firstOfState == null) {
					firstOfState = currentAsset;
				}
				
				if(firstOfState.getState() != currentAsset.getState()) {
					logger.debug(String.format("Asset state changing from [%s] to [%s]",
							firstOfState.getState().toString(), currentAsset.getState().toString()));
					
					// Make sure we have an entry for the state
					if(!timeAccumaltion.containsKey(firstOfState.getState())) {
						timeAccumaltion.put(firstOfState.getState(), 0L);
					}
					
					// Calculate the duration of the state and accumlate it
					logger.debug(String.format("State time accumulation is [%d]", 
													timeAccumaltion.get(firstOfState.getState())));
					long durationOfCurrentState = currentAsset.getTimestamp() - firstOfState.getTimestamp();
					long accumulatedTime = timeAccumaltion.get(firstOfState.getState()) + durationOfCurrentState;
					timeAccumaltion.put(firstOfState.getState(), accumulatedTime);
					logger.debug(String.format("State duration is [%d] and new accumulation is [%s]",
													durationOfCurrentState, accumulatedTime));
					
					firstOfState = currentAsset;
				}
			}
			
			// Calculate the time from the last state change to the end of the report
			// Make sure we have an entry for the state
			if(!timeAccumaltion.containsKey(firstOfState.getState())) {
				timeAccumaltion.put(firstOfState.getState(), 0L);
			}
			
			// Calculate the duration of the state and accumulate it
			logger.debug("Calculating current state time accumulation from last state change to end of report.");
			logger.debug(String.format("State time accumulation is [%d]", 
											timeAccumaltion.get(firstOfState.getState())));
			long durationOfCurrentState = reportEndDate.getTime() - firstOfState.getTimestamp();
			long accumulatedTime = timeAccumaltion.get(firstOfState.getState()) + durationOfCurrentState;
			timeAccumaltion.put(firstOfState.getState(), accumulatedTime);
			logger.debug(String.format("State duration is [%d] and new accumulation is [%s]",
											durationOfCurrentState, accumulatedTime));
						
			// Calculate the duration of the entire corpus of assets
			long entireDuration = reportEndDate.getTime() - assets.get(0).getTimestamp();
			logger.debug(String.format("Entire duration of asset is [%d]", entireDuration));
			
			for(Entry<AssetState, Long> stateTimeEntry : timeAccumaltion.entrySet()) {
				ReportLine reportLine = new ReportLine();
				
				reportLine.setIpType(firstOfState.getIpType());
				reportLine.setSecure(firstOfState.isSecure());
				reportLine.setDialogId(firstOfState.getDialogId());
				reportLine.setTarget(firstOfState.getTarget());
				reportLine.setState(stateTimeEntry.getKey());
				
				double percentage = (double)stateTimeEntry.getValue() / entireDuration;
				logger.debug(String.format("Asset [%s, %b, %s, %s, %s] percentage of uptime calculated to be [%f]", 
												reportLine.getIpType().toString(),
												reportLine.isSecure(),
												reportLine.getDialogId().getType(),
												reportLine.getTarget().toString(),
												reportLine.getState().toString(),
												percentage));

				reportLine.setPercentage(percentage);
				
				reportLines.add(reportLine);
			}
		}
		
		return reportLines;
	}
	
	private void generateReportEmails(File report, Date startDate, Date endDate) {
		try {
			logger.debug("Generating report emails.");
			EmailSender.sendEmail(reportEmails, 
										EmailConfiguration.REPORT_SUBJECT,
										systemName,
										EmailConfiguration.buildReportMessage(startDate, endDate),
										report);
		} catch (Exception e) {
			logger.error("Failed to send report emails", e);
		}
	}
	
	private class Reporter implements Runnable {
		
		private boolean stop = false;
		
		public void stop() {
			this.stop = true;
		}

		public void run() {	
			File reportFile = null;
			PrintStream reportStream = null;
			
			while (!stop) {
				// Wait until it's time to create the report
				Date nextMonth = DateUtil.getStartOfMonth(DateUtil.currentTime(), 1);
				while(DateUtil.currentTime().before(nextMonth)) {
					try {
						Thread.sleep(nextMonth.getTime() - DateUtil.currentTime().getTime());
					} catch (Exception ignore) {}
				}
				
				Date reportStartDate = DateUtil.getStartOfMonth(DateUtil.currentTime(), -1);
				Date reportEndDate = DateUtil.getEndOfMonth(reportStartDate);
				
				try {
					String reportFileName = String.format("Uptime Report %s %s - %s.csv", 
																systemName,
																DateUtil.format(reportStartDate),
																DateUtil.format(reportEndDate));
					reportFile = new File(reportLocation, reportFileName);
					reportStream = new PrintStream(reportFile);
					
					
					List<Asset> uniqueAssets = assetDAO.getAllUniqueAssets();
					List<ReportLine> reportLines = new LinkedList<ReportLine>();
					for(Asset uniqueAsset : uniqueAssets) {
						List<Asset> assets = assetDAO.getAllForAssetInTimeRange(uniqueAsset, reportStartDate, reportEndDate);
						reportLines.addAll(buildReportLines(assets, reportEndDate));
					}

					reportStream.println(String.format("Reporting Period: %s - %s",
															DateUtil.format(reportStartDate, DateUtil.DAY_SDF),
															DateUtil.format(reportEndDate, DateUtil.DAY_SDF)));
					
					reportStream.println();
					printSummary(reportLines, reportStream);
					
					reportStream.println();
					reportStream.println("Details");
					
					reportStream.println();
					reportStream.println(buildHeader());
					
					for(ReportLine reportLine : reportLines) {
						reportStream.println(reportLine.toString());
					}
					
				} catch (Exception e) {
					logger.error("Unexpected exception caught generating report.", e);
				} finally {
					if (reportStream != null) {
						reportStream.close();
					}
				}
				
				generateReportEmails(reportFile, reportStartDate, reportEndDate);
			}
		}
	}
	
	private class ReportLine {
		private IpType ipType;
		private boolean secure;
		private MonitorDialogId dialogId;
		private Warehouse target;
		private AssetState state;
		private double percentage;
		
		public IpType getIpType() {
			return ipType;
		}
		
		public void setIpType(IpType ipType) {
			this.ipType = ipType;
		}
		
		public boolean isSecure() {
			return secure;
		}
		
		public void setSecure(boolean secure) {
			this.secure = secure;
		}
		
		public MonitorDialogId getDialogId() {
			return dialogId;
		}
		
		public void setDialogId(MonitorDialogId dialogId) {
			this.dialogId = dialogId;
		}
		
		public Warehouse getTarget() {
			return target;
		}
		
		public void setTarget(Warehouse target) {
			this.target = target;
		}
		
		public AssetState getState() {
			return state;
		}
		
		public void setState(AssetState state) {
			this.state = state;
		}
		
		public double getPercentage() {
			return percentage;
		}
		
		public void setPercentage(double percentage) {
			this.percentage = percentage;
		}
		
		public String toString() {
			StringBuilder sb = new StringBuilder();
			
			// Use the values from the firstAssetOfCurrentState since all the asset's in the list
			// are identical except for timestamp and state.
			sb.append(ipType.toString()).append(",")
				.append(secure).append(",")
				.append(dialogId.getType()).append(",")
				.append(target.toString()).append(",");
			
			sb.append(state.toString()).append(",");
			
			sb.append(PERCENTAGE_FORMAT.format(percentage));
			
			return sb.toString();
		}
	}
}
