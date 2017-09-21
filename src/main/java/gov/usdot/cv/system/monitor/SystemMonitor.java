package gov.usdot.cv.system.monitor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.log4j.Logger;

import gov.usdot.cv.system.monitor.SystemMonitorConfig.SenderEntry;
import gov.usdot.cv.system.monitor.uptime.reporter.AssetReporter;
import gov.usdot.cv.system.monitor.util.JsonUtil;
import net.sf.json.JSONObject;

public class SystemMonitor {

	private static Logger logger = Logger.getLogger(SystemMonitor.class);
	
	boolean isMonitoring = false;
	
	private ExecutorService exec;
	
	final private SystemMonitorConfig config;
		
	public SystemMonitor(SystemMonitorConfig config) {
		this.config = config;
		
		// Create enough threads for all the senders plus the monitor thread
		exec = Executors.newFixedThreadPool(this.config.senders.entries.length + 1);
	}
	
	public void monitor() {
		if(!isMonitoring) {
			logger.debug("Starting monitoring.");
			
			// Create the sender threads
			List<SenderThread> senderThreads = new ArrayList<SenderThread>();
			for(SenderEntry senderEntry : config.senders.entries) {
				try {
					logger.debug(String.format("Creating sender thread for sender entry\n%s", senderEntry.toString()));
					SenderThread senderThread = new SenderThread(senderEntry);
					senderThreads.add(senderThread);
				} catch (Exception e) {
					logger.error(String.format("Failed to create sender [%s]", senderEntry.toString()), e);
				}
			}
			
			// Create the monitor thread
			MonitorThread monitorThread;
			try {
				logger.debug(String.format("Creating monitor thread from\n%s", config.monitorConfig.toString()));
				monitorThread = new MonitorThread(config.monitorConfig, config.senders, config.uptimeConfig);
			} catch (Exception e) {
				logger.error(String.format("Failed to create monitor\n%s", config.monitorConfig.toString()), e);
				return;
			}
			
			// Start the senders
			for(SenderThread senderThread : senderThreads) {
				logger.debug(String.format("Starting sender thread\n%s", senderThread.toString()));
				exec.execute(senderThread);
			}
			
			// Delay the start of the monitor thread to allow the senders to send their messages
			// before checking for them
			try {
				Thread.sleep(10000);
			} catch(InterruptedException ignore) {}
			
			// Start the Monitor thread
			logger.debug("Starting monitor thread");
			exec.execute(monitorThread);

			// Create and start the reporter
			AssetReporter assetReporter = new AssetReporter(config.uptimeConfig);
			try {
				assetReporter.start();
			} catch (Exception e) {
				logger.error("Failed to start reporter.", e);
			}
			
			// Flag that we are now monitoring
			isMonitoring = true;
			
			// Keep this thread alive until all threads complete, which should be never
			exec.shutdown();
			try {
				exec.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
			} catch (InterruptedException ignore) {}
		}
		else {
			logger.info("System monitor is already monitoring.");
		}
	}
	
	public static void main(String[] args) {		
		final CommandLineParser parser = new BasicParser();
		
	    final Options options = new Options();
	    options.addOption("c", "config", true, "System monitor configuration file that contains senders and monitor options (mandatory)");
		
		SystemMonitorConfig config = null;
		
	    try {
			final CommandLine commandLine = parser.parse(options, args);
			
		    if (commandLine.hasOption('c')) {
		    	String configFile = commandLine.getOptionValue('c');
		    	JSONObject jsonConfig = JsonUtil.createJsonFromFile(configFile);
		    	
		        if (jsonConfig != null) {
			        logger.debug(String.format("Loaded JSON config of %s", jsonConfig.toString()));
			        
			        try {
			        	config = new SystemMonitorConfig(jsonConfig);
			        	logger.debug("SystemMonitorConfig instantiated successfully");
					} catch (Exception e) {
						logger.error(String.format("Failed to instantiate system monitor config %s.", configFile), e);
						return;
					}
		        }
		        else {
		        	logger.error(String.format("Failed to load system monitor config %s.", configFile));
		        	return;
		        }
		    }
		    else {
	    		usage(options);
	    		return;
		    }
	    } catch (ParseException e) {
	    	logger.error("Command line arguments parsing failed.", e);
			System.out.println("Command line arguments parsing failed. Reason: " + e.getMessage());
			usage(options);
			return;
		}
	    		
		try {
			
			SystemMonitor monitor = new SystemMonitor(config);
			monitor.monitor();
			
		} catch (Exception e) {
			logger.error("Unexpected exception while monitoring system.", e);
			System.out.println("Unexpected exception while monitoring system. Reason: " + e.getMessage());
		}
	}
	
	private static void usage(Options options) {
		HelpFormatter formatter = new HelpFormatter();
		formatter.printHelp("Sender options", options);
	}
}
