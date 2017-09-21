package gov.usdot.cv.system.monitor.uptime.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;

import gov.usdot.cv.system.monitor.SystemMonitorConfig.UptimeConfig;

/**
 * Creates connections to the embedded H2 database.  Not doing pooling or anything
 * fancy yet.  Keeps track of all connections and provides a method to close all.
 */
public class ConnectionManager {
	
	private static Logger logger = Logger.getLogger(ConnectionManager.class);
	
	private String databaseURL;
	private List<Connection> connections = new ArrayList<Connection>();
	
	static {
		try {
            Class.forName("org.h2.Driver");
        } catch (ClassNotFoundException ex) {
            logger.error(ex);
        }
		// If we ever need to run a client/server H2 setup start with this
		// "database": "jdbc:h2:tcp://localhost:8161/C:\\temp\\tracker2\\track",
		// org.h2.tools.Server server = org.h2.tools.Server.createTcpServer(new String[] {
        //		"-tcpPort", "8161",
   		//		"-tcpAllowOthers" }).start();
		//		http://h2database.com/html/features.html#connection_modes
		//		http://h2database.com/html/features.html#auto_mixed_mode
		//		http://h2database.com/html/tutorial.html#using_server		
    }
	
	public ConnectionManager(UptimeConfig config) {
		databaseURL = String.format("%s%s%s%d", "jdbc:h2:", config.databasePath, 
        		";AUTO_SERVER=TRUE;AUTO_SERVER_PORT=", config.databasePort);
	}
	
	public Connection getConnection() throws SQLException {
		Connection connection = DriverManager.getConnection(databaseURL);
		connections.add(connection);
		return connection;
    }
    
    public void closeAllConnections() {
    	for (Connection connection: connections) {
    		try {
				connection.close();
			} catch (SQLException e) {
				logger.warn(e);
			}
    	}
    }
    
}
