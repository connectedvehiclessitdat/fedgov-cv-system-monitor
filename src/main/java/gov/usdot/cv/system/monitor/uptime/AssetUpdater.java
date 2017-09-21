package gov.usdot.cv.system.monitor.uptime;

import java.sql.SQLException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;

import gov.usdot.cv.system.monitor.SystemMonitorConfig.UptimeConfig;
import gov.usdot.cv.system.monitor.uptime.database.AssetDAO;
import gov.usdot.cv.system.monitor.uptime.database.ConnectionManager;
import gov.usdot.cv.system.monitor.util.DateUtil;

/**
 * All updates to Assets run through this class so we only have to deal with one DB connection
 * and don't have to deal with multiple threads and connection pools.  The AssetTracker calls
 * the updateAsset with a changed Asset Object when it wants to write to the DB.
 */
public class AssetUpdater {

	private static Logger logger = Logger.getLogger(AssetUpdater.class);
	
	private BlockingQueue<Asset> assetsToUpdate = new LinkedBlockingQueue<Asset>();
	private Updater updater;
	private ConnectionManager connectionManager;
	private AssetDAO assetDAO;
	
	private int updateIntervalInMinutes;
	
	public AssetUpdater(UptimeConfig config) {
		this.connectionManager = new ConnectionManager(config);

		updater = new Updater();
		
		this.updateIntervalInMinutes = config.updateIntervalInMinutes;
	}

	public void start() throws SQLException {
		assetDAO = new AssetDAO(connectionManager.getConnection());
		
		new Thread(updater).start();
	}
	
	public void stop() throws SQLException {
		updater.stop();
		connectionManager.closeAllConnections();
	}
	
	public void updateAsset(Asset asset) {
		assetsToUpdate.add(asset);
	}
	
	private class Updater implements Runnable {

		private boolean stop = false;
		
		public void run() {
			Asset lastAsset = new Asset();
			
			while (!stop) {
				Asset asset = null;
				try {
					asset = assetsToUpdate.poll(updateIntervalInMinutes, TimeUnit.MINUTES);
				} catch (InterruptedException e) {
					logger.error(e);
				}
				
				if (asset != null) {
					logger.debug(String.format("Asset to update received [%s]", asset.toString()));
					try {
						assetDAO.insertAsset(asset);
						lastAsset = asset;
					} catch (SQLException e) {
						logger.error(String.format("Error updating Asset [%s].", asset.toString()), e);
					}
				}
				else {
					logger.debug(String.format("Timeout reached without receiving new asset.  Using last asset received [%s]", lastAsset.toString()));
					// We didn't receive a new asset in the configured interval
					// Use the last known asset with the current time
					try {
						lastAsset.setTimestamp(DateUtil.currentTime().getTime());
						logger.debug(String.format("Last asset timestamp updated [%s]", lastAsset.toString()));
						assetDAO.insertAsset(lastAsset);
					} catch (SQLException e) {
						logger.error(String.format("Error updating last asset [%s].", lastAsset.toString()), e);
					}
				}
			}
		}
		
		public void stop() {
			stop = true;
		}
	}
	
}
